# Repository Guidelines

Quick reference for developers working on this Maven plugin.

## Build Commands

```bash
mvn clean install              # Build and install locally
mvn test                       # Run unit tests
mvn compile                    # Generate plugin descriptor (required before running tests)
mvn watcher:watch          # Run plugin in watch mode
mvn -X watcher:watch       # Debug mode
git tag -a vX.Y.Z -m "Release"  # Create a release tag for JitPack
git push origin vX.Y.Z           # Push tag to trigger a JitPack build
```

## Coding Style

- Java 21 with 4-space indentation
- K&R brace style (opening brace on same line)
- Constructor injection for dependencies
- Name classes after domain concepts (e.g., `FileWatcher`, `Selector`)
- Tests end with `Test` or `Tests`

**For detailed coding rules and patterns**, see [docs/coding_rules.md](./docs/coding_rules.md)

## Testing

- JUnit 5 + Mockito via Surefire
- Unit tests mirror production package structure
- Use `@TempDir` for file system tests
- Use reflection helpers for testing private methods
- Run `mvn compile` to generate plugin descriptor before running tests
- Run `mvn test` before opening PRs

**For detailed testing guidelines**, see [docs/coding_rules.md](./docs/coding_rules.md)

## Commit Guidelines

Follow Conventional Commits format:
- `feat:` - New features
- `fix:` - Bug fixes
- `refactor:` - Code restructuring
- `chore:` - Maintenance tasks
- `docs:` - Documentation updates

Each PR should describe:
- Behavior changes
- Testing performed
- New configuration parameters (if any)
