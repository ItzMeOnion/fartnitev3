package com.fortnitelocker;

import com.fortnitelocker.LockerState.Rarity;
import com.fortnitelocker.LockerState.Skin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LockerScreen extends Screen {
    private static final int GAP = 4, WHITE = 0xFFFFFFFF, NAVY = 0xFF0A1F5C;
    private static final String[] SORTS = {"Rarity", "Name", "Newest"};
    private static final int ROW_H = 12;

    private record Btn(String label, int x, int y, int w, int h, Runnable action) {}

    private final List<Skin> view = new ArrayList<>();
    private final List<Btn> btns = new ArrayList<>();
    private String query = "";
    private int tabFilter = -1;       // -1 = all, otherwise rarity ordinal
    private int sortMode = 0;
    private boolean panelOpen = false;
    private float scrollPos = 0, scrollTarget = 0;
    private boolean draggingBar = false;
    private String selectedId = null;
    private String status = "";
    private long statusUntil = 0;

    public LockerScreen() { super(Text.literal("Locker")); }

    // ---------- layout ----------
    private int leftW() { return Math.max(150, Math.min(250, width * 36 / 100)); }
    private int gridX() { return leftW() + 16; }
    private int gridRight() { return width - 22; }
    private int gridTop() { return 84; }
    private int gridBottom() { return height - 42; }
    private int cols() { return Math.max(3, Math.min(8, (gridRight() - gridX()) / 50)); }
    private int tile() {
        int c = cols();
        return Math.max(20, (gridRight() - gridX() - GAP * (c - 1)) / c);
    }
    private int contentH() {
        int rows = (int) Math.ceil(view.size() / (double) cols());
        return rows == 0 ? 0 : rows * (tile() + GAP) - GAP;
    }
    private int viewH() { return Math.max(1, gridBottom() - gridTop()); }
    private float maxScroll() { return Math.max(0, contentH() - viewH()); }

    private int sfX() { return gridRight() - 96; }          // sort & filter button
    private int panelX() { return gridRight() - 128; }
    private int panelY() { return 80; }
    private int panelH() { return 12 * ROW_H + 8; }

    // ---------- setup ----------
    @Override
    protected void init() {
        int gx = gridX();
        TextFieldWidget search = new TextFieldWidget(textRenderer, gx, 58, sfX() - gx - 6, 18, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search Outfits"));
        search.setMaxLength(32);
        search.setText(query);
        search.setChangedListener(s -> { query = s; refresh(); });
        addDrawableChild(search);

        btns.clear();
        int y = height - 21, x = 10;
        x = addBtn("EDIT", x, y, () -> openEdit(selectedOrEquipped()));
        x = addBtn("UNEQUIP", x, y, LockerState::unequip);
        x = addBtn("SKINS FOLDER", x, y, () -> {
            try { java.nio.file.Files.createDirectories(LockerState.skinsDir()); } catch (Exception ignored) {}
            Util.getOperatingSystem().open(LockerState.skinsDir().toFile());
        });
        addBtn("RELOAD", x, y, () -> { LockerState.reload(); refresh(); reportSkipped(); });
        int bw = textRenderer.getWidth("BACK") + 24;
        btns.add(new Btn("BACK", width - bw - 10, y, bw, 18, this::close));
        refresh();
    }

    private int addBtn(String label, int x, int y, Runnable r) {
        int w = textRenderer.getWidth(label) + 20;
        btns.add(new Btn(label, x, y, w, 18, r));
        return x + w + 4;
    }

    void refresh() {
        view.clear();
        String q = query.trim().toLowerCase();
        for (Skin s : LockerState.SKINS) {
            if (tabFilter >= 0 && s.rarity().ordinal() != tabFilter) continue;
            if (!q.isEmpty() && !s.name().toLowerCase().contains(q)) continue;
            view.add(s);
        }
        Comparator<Skin> byName = Comparator.comparing(s -> s.name().toLowerCase());
        switch (sortMode) {
            case 0 -> view.sort(Comparator.<Skin>comparingInt(s -> -s.rarity().ordinal()).thenComparing(byName));
            case 1 -> view.sort(byName);
            default -> view.sort(Comparator.<Skin>comparingLong(s -> -s.added()).thenComparing(byName));
        }
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
        scrollPos = Math.max(0, Math.min(scrollPos, maxScroll()));
    }

    private void reportSkipped() {
        if (LockerState.skipped > 0) setStatus("Skipped " + LockerState.skipped + " file(s): skins must be 64x64 PNGs");
    }
    private void setStatus(String s) { status = s; statusUntil = System.currentTimeMillis() + 5000; }

    private Skin selectedOrEquipped() {
        Skin s = LockerState.byId(selectedId);
        return s != null ? s : LockerState.byId(LockerState.getEquippedId());
    }
    private void openEdit(Skin s) {
        if (s == null) { setStatus("Click a skin first, then press EDIT (or right-click a skin)."); return; }
        client.setScreen(new EditSkinScreen(this, s));
    }

    // ---------- drag & drop upload ----------
    @Override
    public void onFilesDropped(List<Path> paths) {
        String firstId = null;
        for (Path p : paths) {
            if (!p.toString().toLowerCase().endsWith(".png")) continue;
            try {
                String id = LockerState.addFile(p);
                if (firstId == null) firstId = id;
            } catch (Exception e) {
                FortniteLockerClient.LOGGER.warn("Could not add {}: {}", p, e.getMessage());
            }
        }
        LockerState.reload();
        refresh();
        Skin added = LockerState.byId(firstId);
        if (added != null) {
            selectedId = added.id();
            client.setScreen(new EditSkinScreen(this, added));
        } else if (firstId != null) {
            reportSkipped();
        } else {
            setStatus("Drop .png skin files (64x64) onto this window.");
        }
    }

    // ---------- drawing helpers ----------
    static void drawBackground(DrawContext ctx, int w, int h) {
        ctx.fillGradient(0, 0, w, h, 0xFF2C88F4, 0xFF0A44B4);
        int[] off = {w * 46 / 100, w * 74 / 100};
        int[] bw = {w * 14 / 100, w * 5 / 100};
        int[] col = {0x16FFFFFF, 0x10FFFFFF};
        for (int b = 0; b < 2; b++)
            for (int y = 0; y < h; y++) {
                int x = off[b] - (int) (y * 0.45f);
                ctx.fill(x, y, x + bw[b], y + 1, col[b]);
            }
    }

    private void drawScaled(DrawContext ctx, String s, int x, int y, float sc, int color) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) x, (float) y);
        m.scale(sc, sc);
        ctx.drawText(textRenderer, Text.literal(s), 0, 0, color, true);
        m.popMatrix();
    }

    private void drawFlatButton(DrawContext ctx, String label, int x, int y, int w, int h, boolean hover) {
        ctx.fill(x, y, x + w, y + h, hover ? WHITE : 0xDD0A1F5C);
        ctx.fill(x, y + h - 1, x + w, y + h, hover ? 0xFFBBD4FF : 0xFF1E3E94);
        ctx.drawText(textRenderer, Text.literal("§l§o" + label), x + (w - textRenderer.getWidth("§l§o" + label)) / 2,
            y + (h - 8) / 2, hover ? NAVY : WHITE, !hover);
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 60), g = Math.min(255, ((c >> 8) & 255) + 60), b = Math.min(255, (c & 255) + 60);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static int darken(int c) {
        int r = (int) (((c >> 16) & 255) * 0.45f), g = (int) (((c >> 8) & 255) * 0.45f), b = (int) ((c & 255) * 0.45f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float diff = scrollTarget - scrollPos;
        scrollPos = Math.abs(diff) < 0.5f ? scrollTarget : scrollPos + diff * 0.3f;

        drawBackground(ctx, width, height);
        // left character panel
        ctx.fillGradient(0, 0, leftW() + 8, height, 0x33001040, 0xBB001040);
        ctx.fill(leftW() + 8, 0, leftW() + 9, height, 0x55FFFFFF);

        boolean inPanel = panelOpen && inRect(mouseX, mouseY, panelX(), panelY(), 128, panelH());
        Skin hovered = inPanel ? null : skinAt(mouseX, mouseY);
        Skin equipped = LockerState.byId(LockerState.getEquippedId());
        Skin focus = hovered != null ? hovered : (LockerState.byId(selectedId) != null ? LockerState.byId(selectedId) : equipped);

        // character preview
        if (client != null && client.player != null) {
            int y1 = 18, y2 = height - 100;
            int size = Math.max(30, (int) ((y2 - y1) * 0.42f));
            InventoryScreen.drawEntity(ctx, 10, y1, leftW() + 4, y2, size, 0.0625F,
                (float) mouseX, (float) mouseY, client.player);
        }
        // name + info (bottom left)
        String nm = focus == null ? "Default Skin" : focus.name();
        drawScaled(ctx, "§l§o" + nm.toUpperCase(), 14, height - 90, 1.6f, WHITE);
        if (focus != null) {
            ctx.drawText(textRenderer, Text.literal(focus.rarity().label() + " Outfit"),
                14, height - 72, 0xFF000000 | focus.rarity().color, true);
            if (focus == equipped) ctx.drawText(textRenderer, Text.literal("§aEQUIPPED"),
                14 + textRenderer.getWidth(focus.rarity().label() + " Outfit") + 8, height - 72, WHITE, true);
        } else {
            ctx.drawText(textRenderer, Text.literal("Your normal Minecraft skin"), 14, height - 72, 0xFFCCE0FF, true);
        }
        int hy = height - 58;
        for (OrderedText line : textRenderer.wrapLines(
                Text.literal("Drag & drop a .png onto this window to add a skin. Right-click a skin to edit."),
                leftW() - 12)) {
            ctx.drawText(textRenderer, line, 14, hy, 0xFF9FBBF0, false);
            hy += 9;
        }

        // header + tab
        int gx = gridX();
        drawScaled(ctx, "§l§oCHARACTER", gx, 6, 2.4f, WHITE);
        ctx.drawText(textRenderer, Text.literal("§l§oOUTFIT"), gx, 40, WHITE, true);
        ctx.fill(gx, 51, gx + textRenderer.getWidth("§l§oOUTFIT"), 53, WHITE);
        String count = view.size() + " / " + LockerState.SKINS.size() + " owned";
        ctx.drawText(textRenderer, Text.literal(count), gridRight() - textRenderer.getWidth(count), 40, 0xFFCCE0FF, true);

        // sort & filter button
        boolean sfHover = inRect(mouseX, mouseY, sfX(), 57, 96, 20);
        drawFlatButton(ctx, "SORT & FILTER", sfX(), 57, 96, 20, sfHover || panelOpen);

        // grid
        int top = gridTop(), bot = gridBottom(), t = tile(), c = cols();
        ctx.enableScissor(gx - 4, top, gridRight() + 4, bot);
        if (view.isEmpty()) {
            boolean none = LockerState.SKINS.isEmpty();
            String l1 = none ? "YOUR LOCKER IS EMPTY" : "NO OUTFITS MATCH";
            String l2 = none ? "Drag & drop a 64x64 skin .png onto this window" : "Try a different search or filter";
            int cx = (gx + gridRight()) / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§l§o" + l1), cx, top + 40, WHITE);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(l2), cx, top + 56, 0xFFCCE0FF);
        }
        int firstRow = Math.max(0, (int) scrollPos / (t + GAP));
        for (int idx = firstRow * c; idx < view.size(); idx++) {
            int y = top + (idx / c) * (t + GAP) - Math.round(scrollPos);
            if (y >= bot) break;
            if (y + t < top) continue;
            int x = gx + (idx % c) * (t + GAP);
            drawTile(ctx, view.get(idx), x, y, t, view.get(idx) == hovered);
        }
        ctx.disableScissor();

        // scrollbar
        if (maxScroll() > 0) {
            int bx = width - 16;
            ctx.fill(bx, top, bx + 5, bot, 0x55000000);
            int knobH = Math.max(20, (int) ((float) viewH() * viewH() / contentH()));
            int knobY = top + (int) (scrollPos / maxScroll() * (viewH() - knobH));
            ctx.fill(bx, knobY, bx + 5, knobY + knobH, draggingBar ? WHITE : 0xCCFFFFFF);
        }

        // bottom bar
        ctx.fill(0, height - 24, width, height, 0xAA000820);
        for (Btn b : btns) drawFlatButton(ctx, b.label(), b.x(), b.y(), b.w(), b.h(), inRect(mouseX, mouseY, b.x(), b.y(), b.w(), b.h()));
        if (System.currentTimeMillis() < statusUntil)
            ctx.drawText(textRenderer, Text.literal("§e" + status), gx, height - 38, WHITE, true);

        super.render(ctx, mouseX, mouseY, delta);

        if (panelOpen) drawPanel(ctx, mouseX, mouseY);
        else if (hovered != null) {
            String tip = hovered.name() + "  (" + hovered.rarity().label() + ")";
            int w = textRenderer.getWidth(tip) + 8;
            int tx = Math.min(mouseX + 8, width - w - 4), ty = Math.max(2, mouseY - 18);
            ctx.fill(tx, ty, tx + w, ty + 14, 0xEE100020);
            ctx.drawText(textRenderer, Text.literal(tip), tx + 4, ty + 3, WHITE, true);
        }
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int px = panelX(), py = panelY(), pw = 128;
        ctx.fill(px - 1, py - 1, px + pw + 1, py + panelH() + 1, 0xFFFFFFFF);
        ctx.fill(px, py, px + pw, py + panelH(), 0xF006163A);
        for (int i = 0; i < 12; i++) {
            int ry = py + 4 + i * ROW_H;
            boolean hover = inRect(mx, my, px, ry, pw, ROW_H) && i != 0 && i != 4;
            if (hover) ctx.fill(px + 2, ry, px + pw - 2, ry + ROW_H, 0x44FFFFFF);
            if (i == 0) ctx.drawText(textRenderer, Text.literal("§l§oSORT BY"), px + 6, ry + 2, 0xFF9FBBF0, false);
            else if (i == 4) ctx.drawText(textRenderer, Text.literal("§l§oRARITY"), px + 6, ry + 2, 0xFF9FBBF0, false);
            else if (i <= 3) {
                boolean sel = sortMode == i - 1;
                ctx.drawText(textRenderer, Text.literal(SORTS[i - 1]), px + 18, ry + 2, WHITE, false);
                if (sel) ctx.fill(px + 8, ry + 4, px + 13, ry + 9, WHITE);
            } else {
                int ri = i - 5;                 // 0 = All, 1.. = rarities
                boolean sel = tabFilter == ri - 1;
                if (ri == 0) ctx.drawText(textRenderer, Text.literal("All"), px + 18, ry + 2, WHITE, false);
                else {
                    Rarity r = Rarity.values()[ri - 1];
                    ctx.fill(px + 18, ry + 2, px + 26, ry + 10, 0xFF000000 | r.color);
                    ctx.drawText(textRenderer, Text.literal(r.label()), px + 31, ry + 2, WHITE, false);
                }
                if (sel) ctx.fill(px + 8, ry + 4, px + 13, ry + 9, WHITE);
            }
        }
    }

    private boolean panelClick(double mx, double my) {
        int px = panelX(), py = panelY();
        int i = (int) ((my - py - 4) / ROW_H);
        if (my < py + 4 || i < 0 || i > 11) return true;
        if (i >= 1 && i <= 3) { sortMode = i - 1; refresh(); }
        else if (i >= 5) { tabFilter = (i - 5) - 1; refresh(); }
        return true;
    }

    private void drawTile(DrawContext ctx, Skin s, int x, int y, int t, boolean over) {
        int c = 0xFF000000 | s.rarity().color;
        ctx.fill(x, y, x + t, y + t, darken(c));                  // rarity frame
        ctx.fillGradient(x + 1, y + 1, x + t - 1, y + t - 1, lighten(c), darken(c));
        float u = (t - 8) / 32f;
        int fx = x + (t - Math.round(16 * u)) / 2;
        drawFigure(ctx, s.texture(), fx, y + 4, u);

        boolean equipped = s.id().equals(LockerState.getEquippedId());
        boolean selected = s.id().equals(selectedId);
        int bc = selected || equipped ? WHITE : over ? 0xFFFFE680 : 0;
        if (bc != 0) {
            int th = selected ? 2 : 1;
            ctx.fill(x, y, x + t, y + th, bc);
            ctx.fill(x, y + t - th, x + t, y + t, bc);
            ctx.fill(x, y, x + th, y + t, bc);
            ctx.fill(x + t - th, y, x + t, y + t, bc);
        }
        if (equipped) {
            int bx = x + t - 12, by = y + 3;
            ctx.fill(bx, by, bx + 9, by + 9, 0xFF2ECC40);
            int[][] chk = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}};
            for (int[] p : chk) ctx.fill(bx + p[0] - 1, by + p[1] - 1, bx + p[0], by + p[1], WHITE);
        }
    }

    // Front-facing mini character built from the 64x64 skin texture.
    static void drawFigure(DrawContext ctx, Identifier tex, int fx, int fy, float u) {
        part(ctx, tex, fx, fy, u, 8, 8, 8, 8, 4, 0);
        part(ctx, tex, fx, fy, u, 20, 20, 8, 12, 4, 8);
        part(ctx, tex, fx, fy, u, 44, 20, 4, 12, 0, 8);
        part(ctx, tex, fx, fy, u, 36, 52, 4, 12, 12, 8);
        part(ctx, tex, fx, fy, u, 4, 20, 4, 12, 4, 20);
        part(ctx, tex, fx, fy, u, 20, 52, 4, 12, 8, 20);
        part(ctx, tex, fx, fy, u, 40, 8, 8, 8, 4, 0);
        part(ctx, tex, fx, fy, u, 20, 36, 8, 12, 4, 8);
        part(ctx, tex, fx, fy, u, 44, 36, 4, 12, 0, 8);
        part(ctx, tex, fx, fy, u, 52, 52, 4, 12, 12, 8);
        part(ctx, tex, fx, fy, u, 4, 36, 4, 12, 4, 20);
        part(ctx, tex, fx, fy, u, 4, 52, 4, 12, 8, 20);
    }

    private static void part(DrawContext ctx, Identifier tex, int fx, int fy, float u,
                             int sx, int sy, int sw, int sh, int ox, int oy) {
        int dx = fx + Math.round(ox * u), dy = fy + Math.round(oy * u);
        int dw = Math.round((ox + sw) * u) - Math.round(ox * u);
        int dh = Math.round((oy + sh) * u) - Math.round(oy * u);
        if (dw <= 0 || dh <= 0) return;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, dx, dy, (float) sx, (float) sy, dw, dh, sw, sh, 64, 64);
    }

    // ---------- input ----------
    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Skin skinAt(double mx, double my) {
        if (mx < gridX() || mx >= gridRight() || my < gridTop() || my >= gridBottom()) return null;
        int t = tile(), c = cols(), cell = t + GAP;
        double ry = my - gridTop() + scrollPos, rx = mx - gridX();
        if (ry % cell >= t || rx % cell >= t) return null;
        int col = (int) (rx / cell), row = (int) (ry / cell);
        if (col >= c) return null;
        int idx = row * c + col;
        return idx >= 0 && idx < view.size() ? view.get(idx) : null;
    }

    private void dragBarTo(double my) {
        int knobH = Math.max(20, (int) ((float) viewH() * viewH() / Math.max(1, contentH())));
        double frac = (my - gridTop() - knobH / 2.0) / Math.max(1, viewH() - knobH);
        scrollTarget = (float) Math.max(0, Math.min(maxScroll(), frac * maxScroll()));
        scrollPos = scrollTarget;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (panelOpen) {
            if (inRect(mx, my, panelX(), panelY(), 128, panelH())) return panelClick(mx, my);
            panelOpen = false;
            if (!inRect(mx, my, sfX(), 57, 96, 20)) return true;
        }
        if (button == 0) {
            for (Btn b : btns) {
                if (inRect(mx, my, b.x(), b.y(), b.w(), b.h())) { b.action().run(); return true; }
            }
            if (inRect(mx, my, sfX(), 57, 96, 20)) { panelOpen = !panelOpen; return true; }
            if (maxScroll() > 0 && mx >= width - 18 && mx <= width - 8 && my >= gridTop() && my < gridBottom()) {
                draggingBar = true;
                dragBarTo(my);
                return true;
            }
        }
        Skin s = skinAt(mx, my);
        if (s != null) {
            selectedId = s.id();
            if (button == 0) {
                LockerState.equip(s);
                MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            } else if (button == 1) {
                openEdit(s);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar) { dragBarTo(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * (tile() + GAP) * 0.8f;
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
