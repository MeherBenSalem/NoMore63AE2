# Contributing

Thanks for contributing to Core RPG - Quests Engine.

## Before you start

- Open an issue or discussion for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, mixin targets) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Build from inside `1.21.1/` (`./gradlew build`) and run the tests (`:common:test`, `:neoforge:runGameTestServer`, `:fabric:runGametest`).
4. Update documentation when behavior, configuration, or release steps change.
5. Open a pull request with a clear summary, verification notes, and any compatibility risks.

## Coding expectations

- Game logic, GUI and mixins live in `1.21.1/common/`. Loader modules only wire events, networking and registries.
- When you add or change a quest object property, regenerate the format reference: `python3 tools/gen_book_docs.py`.
- Follow the existing patterns in the targeted version root for loader entrypoints and Gradle wiring.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.
- Do not invent Minecraft, NeoForge or Fabric APIs. Verify against the exact version you target.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible. For quest
  problems, attach the relevant files from `config/corerpg_quests/book/`.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
