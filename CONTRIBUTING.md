# Contributing

Thanks for contributing to Core RPG - Quests Engine.

## Before you start

- Open an issue or discussion for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, mixin targets) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Make the change in every Minecraft version workspace it applies to (`1.20.1/`, `1.21.1/`, `26.2/`, `26.3/`): the
   `common/` code is nearly the same in all four and only version-specific APIs differ. Build the workspace you changed
   (`./gradlew build` from that folder: Gradle on Java 21 for 1.20.1 and 1.21.1, Java 25 for 26.x), or all four with
   `./gradlew buildAll` from the root (the 8 loader jars land in `releases/`). This also runs the unit tests; the
   in-game tests are on 1.21.1 (`:neoforge:runGameTestServer`, `:fabric:runGametest`).
4. Update documentation when behavior, configuration, or release steps change.
5. Open a pull request with a clear summary, verification notes, and any compatibility risks.

## Coding expectations

- Game logic, GUI and mixins live in each workspace's `common/`. Loader modules (`fabric/`, `neoforge/`, and `forge/`
  on 1.20.1) only wire events, networking and registries.
- The supported targets are exactly 1.20.1 (Fabric, Forge), 1.21.1, 26.2 and 26.3 (Fabric, NeoForge), all with the same
  `version=` (`./gradlew checkVersions`, `python3 scripts/check_targets.py workspaces`).
- When you add or change a quest object property, regenerate the format reference: `python3 tools/gen_book_docs.py`.
- Follow the existing patterns in the targeted version root for loader entrypoints and Gradle wiring.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.
- Do not invent Minecraft, Fabric, NeoForge or Forge APIs. Verify against the exact version you target.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible. For quest
  problems, attach the relevant files from `config/corerpg_quests/book/`.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
