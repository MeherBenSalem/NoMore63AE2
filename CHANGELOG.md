# No More 63 AE2 — Changelog

## 0.1.3

### Bug Fixes
* **Fabric mixin target mismatch** — `BasicCellInventoryCreateMixin` selects `createInventory` by name, avoiding the Mojang-named `ItemStack` descriptor that cannot match Fabric's intermediary runtime mappings.
* **1.21.1 Fabric release jar** — `remapJar` uses the populated development jar as its input so the distributed jar contains the mod classes and resources.

### Distribution
* Each Minecraft version and loader is published as its own Modrinth version and CurseForge file from the local release script.

## 0.1.2

### Bug Fixes
* **Fabric mixin target mismatch** — `BasicCellInventoryCreateMixin` no longer pins a Mojang-named `ItemStack` method descriptor with `remap = false`. Fabric AE2 ships with intermediary (`net.minecraft.class_1799`), so the old selector never matched and crashed on mixin apply. The injector now selects `createInventory` by name only, which works on Fabric, Forge, and NeoForge.
* **1.21.1 Fabric empty remapJar** — Loom was emitting an empty intermediary jar. `remapJar` now remaps the real `jar` output with nested dependencies disabled, and Fabric Loom is aligned to `1.9-SNAPSHOT`.

## 0.1.1

### Bug Fixes
* **Fabric AE2 dependency mismatch** — `fabric.mod.json` no longer uses Maven interval ranges like `[15.4.10,16.0.0)`, which Fabric Loader could reject even when AE2 `15.4.10` was installed. Fabric now depends on `>=15.4.10` (1.20.1) / `>=19.2.17` (1.21.1). Forge/NeoForge keep Maven intervals in `mods.toml`.

### Improvements
* Root `.gitignore` (excludes vendored AE2 tree, `.jdk21/`, build outputs).
* MultiLoader jars published for Fabric / Forge / NeoForge on 1.20.1 and Fabric / NeoForge on 1.21.1.

## 0.1.0

* Initial configurable item-type limit for AE2 storage cells (default 4096).
