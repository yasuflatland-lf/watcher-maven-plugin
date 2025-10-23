# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Maven plugin that monitors file changes and automatically runs affected tests. The plugin uses Java NIO WatchService to detect changes in source files and intelligently selects which tests to run based on naming conventions (e.g., `Foo.java` -> `FooTest`, `FooTests`, `FooIT`, etc.).

## Quick Reference

- **Build & Development**: See [AGENTS.md](./AGENTS.md) for build commands, coding style, and commit conventions
- **Architecture**: See [docs/architecture.md](./docs/architecture.md) for detailed component design and data flow
- **Coding Rules**: See [docs/coding_rules.md](./docs/coding_rules.md) for coding standards and testing patterns
- **Debugging**: See [docs/debugging.md](./docs/debugging.md) for troubleshooting guide

## Core Components

1. **WatcherMojo** - Maven plugin entry point that wires components and orchestrates the watch loop
2. **FileWatcher** - Wraps Java NIO WatchService with debouncing and recursive directory monitoring
3. **Selector** - Bridges changed files to test execution (selects tests + runs Maven)
4. **TestNameResolver** - Pure utility that maps source files to test names (`Foo.java` → `FooTest`)
5. **PathMatcher** - Glob pattern filter for file inclusion/exclusion
6. **WatcherConfig** - Immutable configuration data holder
7. **QueueOrchestrator** - Queues file changes detected during test execution (watchMode=true only)
8. **FileWatcherFactory** - Factory interface for creating FileWatcher instances (enables testing)
9. **DefaultFileWatcherFactory** - Production implementation (creates real FileWatcher)
10. **TestFileWatcherFactory** - Test implementation (creates non-blocking FileWatcher for tests)

## Data Flow

### Standard Flow (No test running)
```
File change → FileWatcher → PathMatcher → Selector.selectTests()
  → TestNameResolver → Verify test exists → executeTests() → mvn test
```

### Queue Flow (watchMode=true, test running)
```
File change → FileWatcher → PathMatcher → QueueOrchestrator.enqueue()
  [Test completes] → QueueOrchestrator.dequeue() → Selector.selectTests()
  → TestNameResolver → Verify test exists → executeTests() → mvn test
  [Loop until queue is empty]
```

## Key Behaviors

- Test file changes trigger direct execution of those tests
- Source file changes trigger inferred tests based on naming conventions
- Inferred tests that don't exist are silently filtered out
- Watch mode: test failures are logged, watcher continues
- One-shot mode: test failures throw MojoExecutionException
- New directories are automatically registered for watching
- File changes detected during test execution are queued (watchMode=true only)
- Queued changes are automatically processed after test completion
- Test failures preserve the queue for next file change (watchMode=true only)

## Testability & Design Patterns

### Factory Pattern for Dependency Injection

The plugin uses the Factory pattern to enable testing of the `execute()` method in WatcherMojo:

```java
// In WatcherMojo
private FileWatcherFactory fileWatcherFactory = new DefaultFileWatcherFactory();

void setFileWatcherFactory(FileWatcherFactory factory) {  // Package-private for tests
    this.fileWatcherFactory = factory;
}

// In execute()
FileWatcher fileWatcher = fileWatcherFactory.create(watchPaths, debounceMs,
                                                     recursive, pathMatcher, getLog());
fileWatcher.watch(createFileChangeHandler(selector), watchMode);
```

**Benefits**:
- `execute()` method is testable without blocking (0% → 55-60% coverage)
- Tests complete in seconds instead of hanging indefinitely
- Overall coverage: 64% → 71%

### File Change Handler Extraction

File change callback logic is extracted as a package-private method for independent testing:

```java
Consumer<List<Path>> createFileChangeHandler(Selector selector) {
    return changedFiles -> {
        try {
            handleFileChanges(changedFiles, selector);
        } catch (MojoExecutionException e) {
            getLog().error("Error handling file changes", e);
        }
    };
}
```

**Benefits**:
- Handler can be tested independently
- Exception handling is testable
- 80% coverage on handler creation logic

### Test Implementation

```java
// In tests (see WatcherMojoTest.java)
@Test
void shouldExecuteWithTestFileWatcherFactory() throws Exception {
    Path testFile = testDir.resolve("ExampleTest.java");
    Files.createFile(testFile);

    // Inject TestFileWatcherFactory
    mojo.setFileWatcherFactory(new TestFileWatcherFactory(List.of(testFile), null));

    // execute() completes without blocking
    mojo.execute();
}
```

**Related Tests**:
- `shouldExecuteWithTestFileWatcherFactory()`: Tests execute() with Factory pattern
- `shouldCreateFileChangeHandler()`: Tests handler creation independently
- `shouldHandleFileChangesViaHandler()`: Tests file change handling through handler

## Quick Debugging

Enable debug logs:
```bash
mvn watcher:watch -X                          # Full Maven debug
mvn watcher:watch -Dwatcher.verbose=true      # Verbose mode (recommended)
```

Build requirements:
```bash
mvn compile                                       # Generate plugin descriptor (required)
mvn test                                          # Run tests
```

Common issues:
- File changes not detected → Check directory registration and file system events
- Tests not executed → Check PathMatcher filtering and test naming conventions
- Test failures → Run `mvn compile` first to generate plugin descriptor
- For detailed troubleshooting, see [docs/debugging.md](./docs/debugging.md)
