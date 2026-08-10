# No More 63 AE2

MultiLoader AE2 addon that replaces AE2's hardcoded **63 item types per cell** limit with a configurable value (default **4096**, range **63–65535**).

## Project layout

```
NoMore63AE2/
├── shared/                 # Cross-version Java sources (mixin + config)
├── 1.20.1/                 # Minecraft 1.20.1 multiloader build
│   ├── common/
│   ├── fabric/
│   ├── forge/
│   └── neoforge/
├── 1.21.1/                 # Minecraft 1.21.1 multiloader build
│   ├── common/
│   ├── fabric/
│   ├── forge/
│   └── neoforge/
├── 26.1.2/                 # Reference NeoForge 26.1.2 addon (preserved)
└── Applied-Energistics-2-main/  # AE2 dev tree (preserved)
```

## Supported versions

| Minecraft | Fabric | Forge | NeoForge | AE2 dependency |
|-----------|--------|-------|----------|----------------|
| 1.20.1    | Yes    | Yes   | Yes      | 15.4.10+ (Fabric: `>=15.4.10`) |
| 1.21.1    | Yes    | Yes   | Yes      | 19.2.17+ (Fabric: `>=19.2.17`) |

## Build requirements

- **Java 17** toolchain for `1.20.1/` (Gradle auto-provisions via Foojay)
- **Java 21** toolchain for `1.21.1/`
- Run Gradle with **JDK 21** or **JDK 17** (not Java 26 — Gradle/plugins are incompatible with Java 26 on this setup)

```powershell
# Example: point JAVA_HOME at JDK 21 before building
$env:JAVA_HOME = "C:\path\to\jdk-21"

cd 1.20.1
.\gradlew build

cd ..\1.21.1
.\gradlew build
```

## Output JARs

Mod JARs are written to each loader's `build/libs/` directory, named:

`no-more-63-ae2-<minecraft>-<loader>-<modVersion>.jar`

Examples:

- `1.20.1/fabric/build/libs/no-more-63-ae2-1.20.1-fabric-0.1.0.jar`
- `1.21.1/neoforge/build/libs/no-more-63-ae2-1.21.1-neoforge-0.1.0.jar`

## Configuration

File: `config/no_more_63_ae2.toml`

```toml
# No More 63 AE2 config
# Controls how many unique item types an AE2 item storage cell can hold.
# AE2 default is 63.
maxItemTypesPerCell = 4096
```

The active value is logged on startup. Config changes require a restart (no hot-reload).

## AE2 patch

- **Class:** `appeng.me.cells.BasicCellInventory`
- **Mixin:** `nm63ae2.mixin.BasicCellInventoryCreateMixin` (patches `BasicCellInventory.createInventory` after construction)
- **Injection:** Before `getUpgradesInventory()` in the constructor, after AE2 clamps types to 63
- **Scope:** Item storage cells only (`AEKeyType.items()`); fluid/other cells unchanged

## Known limitations

- **1.20.1 NeoForge** is built with the legacy Forge 1.20.1 toolchain (binary-compatible with NeoForge 1.20.1) and ships `neoforge.mods.toml` metadata.
- **1.20.1 AE2** is resolved from [Modmaven](https://modmaven.dev/) (`appeng:appliedenergistics2-*`) because 15.x is not published to Maven Central.
- **`26.1.2/`** remains a separate NeoForge-only reference using `ModConfigSpec` instead of the shared TOML config.
