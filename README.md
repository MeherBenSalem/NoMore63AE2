# No More 63 AE2

MultiLoader Applied Energistics 2 addon that replaces AE2's hardcoded **63 item types per cell** limit with a configurable value (default **4096**, range **63–65535**).

## Features

- Configurable item-type cap for AE2 item storage cells
- Shared mixin + TOML config across Minecraft lines
- Fabric / Forge / NeoForge on 1.20.1 and 1.21.1; NeoForge on 26.2

## Supported versions

| Minecraft | Fabric | Forge | NeoForge | AE2 dependency |
|-----------|--------|-------|----------|----------------|
| 1.20.1    | Yes    | Yes   | Yes      | 15.4.10+ (Fabric: `>=15.4.10`) |
| 1.21.1    | Yes    | Yes   | Yes      | 19.2.17+ (Fabric: `>=19.2.17`) |
| 26.2      | —      | —     | Yes      | 26.1.11-beta+ |

**Note:** Official AE2 is still published for Minecraft **26.1.2**. The 26.2 NeoForge jar compiles against AE2 `26.1.11-beta` and will only run in-game once AE2 itself ships a matching 26.2 build.

## Requirements

- **JDK 21** (or 17) to run Gradle for `1.20.1/` and `1.21.1/` (toolchains provision the correct Java)
- **JDK 25** for `26.2/`
- Do not run these Gradle builds with an unsupported bleeding-edge JDK that Gradle plugins reject

## Installation

1. Install Applied Energistics 2 for your Minecraft version and loader.
2. Drop the matching `no-more-63-ae2-<minecraft>-<loader>-<version>.jar` into `mods/`.
3. Start the game once; edit `config/no_more_63_ae2.toml` if you want a different type limit.
4. Restart after changing the config (no hot-reload).

## Configuration

File: `config/no_more_63_ae2.toml`

```toml
# No More 63 AE2 config
# Controls how many unique item types an AE2 item storage cell can hold.
# AE2 default is 63.
maxItemTypesPerCell = 4096
```

## Building

```powershell
# 1.20.1 and 1.21.1 (use JDK 21 for the Gradle JVM)
$env:JAVA_HOME = "C:\path\to\jdk-21"
cd 1.20.1
.\gradlew.bat build
cd ..\1.21.1
.\gradlew.bat build

# 26.2 NeoForge (use JDK 25)
$env:JAVA_HOME = "C:\path\to\jdk-25"
cd ..\26.2
.\gradlew.bat build
```

Mod JARs are written to each loader's `build/libs/` directory:

`no-more-63-ae2-<minecraft>-<loader>-<modVersion>.jar`

## Project layout

```
NoMore63AE2/
├── shared/     # Cross-version mixin + TOML config
├── 1.20.1/     # Fabric, Forge, NeoForge
├── 1.21.1/     # Fabric, Forge, NeoForge
└── 26.2/       # NeoForge only
```

## AE2 patch

- **Class:** `appeng.me.cells.BasicCellInventory`
- **Mixin:** `nm63ae2.mixin.BasicCellInventoryCreateMixin` (patches `createInventory` after construction)
- **Scope:** Item storage cells only (`AEKeyType.items()`); fluid/other cells unchanged

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

See [.github/SECURITY.md](.github/SECURITY.md).

## License

Licensed under the [Apache License 2.0](LICENSE).
