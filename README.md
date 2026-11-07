# Fortnite Locker (Fabric, Minecraft 1.21.8)

A battle-royale style Locker for Minecraft. Press **L** to open it, click a skin to equip it, and your player skin changes instantly.

## Build
Push to GitHub: the included Action builds the jar (Actions tab -> artifact `fortnite-locker-jar`).
Locally: Gradle 8.14+ and JDK 21, then `gradle build` -> `build/libs/fortnite-locker-1.0.0.jar`.

## Install
Fabric Loader 0.17+, Fabric API 0.136.1+1.21.8, Minecraft 1.21.8, Java 21. Put the jar in `mods/`.

## Using the locker
- **Left-click** a skin to equip it. **Right-click** (or select + **Edit**) to rename it, change its rarity, or delete it.
- **Drag & drop** a 64x64 skin `.png` onto the locker window to add it. An editor opens so you can set its name and rarity.
- Scroll with the mouse wheel (smooth) or drag the scrollbar. Lower your GUI Scale in Video Settings to fit more skins on screen.
- Search, rarity tabs and sort (Rarity / Name / Newest) are built in.
- Skins live in `.minecraft/config/fortnitelocker/skins/`; names and rarities are saved in `config/fortnitelocker/metadata.json`.

## Notes
- Client-side only: only you see the skin change.
- Skins must be 64x64 PNGs (modern Minecraft skin format).
- No skins are bundled (Fortnite art is Epic Games' IP). The locker starts empty: drag & drop your own PNGs for personal use.
