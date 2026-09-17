# Contributing

Thanks for contributing to No More 63 AE2.

## Before you start

- Open an issue or discussion for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, mixin targets) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Build the version root you changed from inside that folder, for example `1.20.1/`, `1.21.1/`, or `26.2/`.
4. Update documentation when behavior, configuration, or release steps change.
5. Open a pull request with a clear summary, verification notes, and any compatibility risks.

## Coding expectations

- Shared mixin and config logic lives in `shared/`. Prefer extending that when the change applies across Minecraft lines.
- Follow the existing patterns in the targeted version root for loader entrypoints and Gradle wiring.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.
- Do not invent Minecraft, NeoForge, Fabric, or AE2 APIs. Verify against the exact version you target.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, AE2 version, reproduction steps, and relevant logs when possible.

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
