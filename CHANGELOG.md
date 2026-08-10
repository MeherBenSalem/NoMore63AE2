# No More 63 AE2 — Changelog

## 0.1.1

### Bug Fixes
* **Fabric AE2 dependency mismatch** — `fabric.mod.json` no longer uses Maven interval ranges like `[15.4.10,16.0.0)`, which Fabric Loader could reject even when AE2 `15.4.10` was installed. Fabric now depends on `>=15.4.10` (1.20.1) / `>=19.2.17` (1.21.1). Forge/NeoForge keep Maven intervals in `mods.toml`.

### Improvements
* Root `.gitignore` (excludes vendored AE2 tree, `.jdk21/`, build outputs).
* MultiLoader jars published for Fabric / Forge / NeoForge on 1.20.1 and Fabric / NeoForge on 1.21.1.

## 0.1.0

* Initial configurable item-type limit for AE2 storage cells (default 4096).
