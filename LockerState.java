package com.fortnitelocker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class LockerState {
    public enum Rarity {
        COMMON(0xB1B1B1), UNCOMMON(0x60AA3A), RARE(0x49ACF2),
        EPIC(0xB15BE2), LEGENDARY(0xE98B2B), ICON(0x2BE9D5);
        public final int color;
        Rarity(int c) { color = c; }
        public String label() { return name().charAt(0) + name().substring(1).toLowerCase(); }
        public Rarity next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public record Skin(String id, String name, Rarity rarity, Identifier texture,
                       boolean bundled, Path file, long added) {}

    public static final List<Skin> SKINS = new ArrayList<>();
    public static int skipped = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type META_TYPE = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
    private static Map<String, Map<String, String>> meta = new HashMap<>();
    private static final Map<String, Identifier> DYNAMIC_CACHE = new HashMap<>();
    private static int counter = 0;
    private static String equippedId = null;

    public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("fortnitelocker"); }
    public static Path skinsDir() { return dir().resolve("skins"); }

    public static Skin byId(String id) {
        if (id == null) return null;
        for (Skin s : SKINS) if (s.id().equals(id)) return s;
        return null;
    }

    public static void reload() {
        SKINS.clear();
        skipped = 0;
        loadMeta();
        try {
            Files.createDirectories(skinsDir());
            try (Stream<Path> s = Files.list(skinsDir())) {
                s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                 .sorted().forEach(LockerState::loadExternal);
            }
        } catch (Exception e) {
            FortniteLockerClient.LOGGER.error("Failed scanning skins folder", e);
        }
        if (byId(equippedId) == null) equippedId = null;
    }

    private static Skin withMeta(String id, String name, Rarity rarity, Identifier tex,
                                 boolean bundled, Path file, long added) {
        Map<String, String> m = meta.get(id);
        if (m != null) {
            String n = m.get("name");
            if (n != null && !n.isBlank()) name = n;
            try { if (m.get("rarity") != null) rarity = Rarity.valueOf(m.get("rarity")); }
            catch (IllegalArgumentException ignored) {}
        }
        return new Skin(id, name, rarity, tex, bundled, file, added);
    }

    // Default naming from filename: [rarity_]Name.png ; overridden by the in-game editor.
    private static void loadExternal(Path p) {
        try {
            String file = p.getFileName().toString();
            String base = file.substring(0, file.length() - 4);
            Rarity rarity = Rarity.RARE;
            String name = base;
            int us = base.indexOf('_');
            if (us > 0) {
                try {
                    rarity = Rarity.valueOf(base.substring(0, us).toUpperCase());
                    name = base.substring(us + 1);
                } catch (IllegalArgumentException ignored) {}
            }
            name = name.replace('_', ' ');
            long modified = p.toFile().lastModified();
            String key = file + modified;
            Identifier id = DYNAMIC_CACHE.get(key);
            if (id == null) {
                try (InputStream in = Files.newInputStream(p)) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 64 || img.getHeight() != 64) {
                        img.close();
                        skipped++;
                        FortniteLockerClient.LOGGER.warn("Skipping {}: skin must be 64x64", file);
                        return;
                    }
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "fortnitelocker:" + file, img);
                    tex.upload();
                    id = Identifier.of("fortnitelocker", "dynamic/skin_" + (counter++));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
                    DYNAMIC_CACHE.put(key, id);
                }
            }
            SKINS.add(withMeta("file_" + file, name, rarity, id, false, p, modified));
        } catch (Exception e) {
            FortniteLockerClient.LOGGER.warn("Could not load skin {}: {}", p, e.getMessage());
        }
    }

    /** Copies a PNG into the skins folder. Returns the new skin's id. */
    public static String addFile(Path src) throws IOException {
        Files.createDirectories(skinsDir());
        String fileName = src.getFileName().toString();
        String stem = fileName.substring(0, fileName.length() - 4);
        Path dest = skinsDir().resolve(fileName);
        int n = 1;
        while (Files.exists(dest)) dest = skinsDir().resolve(stem + "_" + (n++) + ".png");
        Files.copy(src, dest);
        return "file_" + dest.getFileName();
    }

    public static void setMeta(String id, String name, Rarity rarity) {
        Map<String, String> m = new HashMap<>();
        m.put("name", name);
        m.put("rarity", rarity.name());
        meta.put(id, m);
        saveMeta();
        reload();
    }

    public static void delete(Skin s) {
        if (s.bundled()) return;
        try { Files.deleteIfExists(s.file()); } catch (IOException ignored) {}
        meta.remove(s.id());
        if (s.id().equals(equippedId)) unequip();
        saveMeta();
        reload();
    }

    private static void loadMeta() {
        meta = new HashMap<>();
        try {
            Path f = dir().resolve("metadata.json");
            if (Files.exists(f)) {
                Map<String, Map<String, String>> m = GSON.fromJson(Files.readString(f), META_TYPE);
                if (m != null) meta = m;
            }
        } catch (Exception e) {
            FortniteLockerClient.LOGGER.warn("Could not read metadata.json: {}", e.getMessage());
        }
    }

    private static void saveMeta() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("metadata.json"), GSON.toJson(meta));
        } catch (Exception ignored) {}
    }

    public static Identifier getEquippedTexture() {
        Skin s = byId(equippedId);
        return s == null ? null : s.texture();
    }
    public static String getEquippedId() { return equippedId; }
    public static void equip(Skin s) { equippedId = s.id(); saveEquipped(); }
    public static void unequip() { equippedId = null; saveEquipped(); }

    public static void load() {
        try {
            Path f = dir().resolve("equipped.txt");
            if (Files.exists(f)) {
                String v = Files.readString(f).trim();
                equippedId = v.isEmpty() ? null : v;
            }
        } catch (Exception ignored) {}
    }

    private static void saveEquipped() {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("equipped.txt"), equippedId == null ? "" : equippedId);
        } catch (Exception ignored) {}
    }
}
