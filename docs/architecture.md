# Architecture Overview

This Maven plugin monitors file changes and automatically runs affected tests. The architecture follows a **pipeline pattern** with clear separation of concerns across ten core components.

## Design Principles

1. **Single Responsibility**: Each component has one well-defined purpose
2. **Dependency Injection**: Components receive collaborators via constructors
3. **Immutable Configuration**: `WatcherConfig` is created once and passed to collaborators
4. **Pure Functions**: `TestNameResolver` has no state or side effects
5. **Fail-Fast Validation**: Configuration errors detected early in `WatcherMojo`
6. **Queue-Based Processing**: File changes during test execution are queued and processed after completion

## Core Components

| Component | Responsibility | Dependencies | State |
|-----------|---------------|--------------|-------|
| **WatcherMojo** | Entry point & orchestration | All components | Configuration only |
| **FileWatcher** | File system monitoring | PathMatcher | WatchService state |
| **Selector** | Test selection & execution | TestNameResolver, WatcherConfig | None (stateless) |
| **TestNameResolver** | Test name generation | None | None (pure utility) |
| **PathMatcher** | File filtering | None | Pattern matchers |
| **WatcherConfig** | Configuration holder | None | Immutable data |
| **QueueOrchestrator** | Queue changes during tests | None | Queue + running flag |
| **FileWatcherFactory** | Factory interface | None | None (interface) |
| **DefaultFileWatcherFactory** | Production FileWatcher factory | FileWatcher | None (stateless) |
| **TestFileWatcherFactory** | Test FileWatcher factory | FileWatcher | Test files list |

### 1. WatcherMojo - Orchestrator

**Responsibilities**:
- Declare Maven `@Parameter` configurations
- Validate configuration early (directories exist, parameters valid)
- Wire components together (Dependency Injection)
- Handle file change callbacks via `createFileChangeHandler()`
- Manage plugin lifecycle (start → watch → stop)
- Use Factory pattern for testable FileWatcher creation

**Key Behavior**:
- Delegates all work to specialized components - contains no business logic
- Uses `FileWatcherFactory` for dependency injection (enables testing without blocking)
- Extracts file change handler as a separate method for testing

**Factory Pattern**:
```java
private FileWatcherFactory fileWatcherFactory = new DefaultFileWatcherFactory();

void setFileWatcherFactory(FileWatcherFactory factory) {  // Package-private for testing
    this.fileWatcherFactory = factory;
}

// In execute():
FileWatcher fileWatcher = fileWatcherFactory.create(watchPaths, debounceMs,
                                                     recursive, pathMatcher, getLog());
fileWatcher.watch(createFileChangeHandler(selector), watchMode);
```

**Testability**: Factory pattern enables testing of `execute()` method (0% → 55-60% coverage)

---

### 2. FileWatcher - Monitoring Layer

**Responsibilities**:
- Monitor directories recursively
- Debounce rapid file changes (batch events)
- Register new directories dynamically
- Filter files through PathMatcher
- Skip excluded directories (e.g., `target/`, `node_modules/`)
- Support watch mode (continuous) and one-shot mode

**Key Behavior**:
- Polling-based debounce: accumulate → wait → emit
- Thread-safe using `ConcurrentHashMap` and `AtomicBoolean`
- Automatically registers newly created directories (recursive mode)
- Skips directories matching exclude patterns during registration

**Configuration**:
- Debounce period: Default 750ms (configurable)
- Polling interval: 100ms (hardcoded)
- Watch events: CREATE, DELETE, MODIFY
- Recursive mode: Default true

**Debounce Flow**:
```
Event Loop (100ms polling):
  1. Poll WatchService for events
  2. Accumulate changed files in Set<Path>
  3. Update lastChangeTime timestamp
  4. Wait until (currentTime - lastChangeTime) > debounceMs
  5. Filter accumulated files through PathMatcher
  6. Invoke callback with filtered list
  7. Clear accumulator and repeat
```

**Directory Exclusion**:
- Checks `PathMatcher.matchesDirectory()` before registering directories
- Skips entire subtrees for excluded directories (performance optimization)
- Uses `Files.walkFileTree()` with `SimpleFileVisitor.preVisitDirectory()`

---

### 3. Selector - Test Execution Bridge

**Responsibilities**:
- Separate changed files into source files and test files
- For test files: Run them directly
- For source files: Generate candidate test names using naming conventions
- Verify test files exist on disk
- Build Maven Surefire commands with all options
- Execute tests via ProcessBuilder
- Auto-detect Maven Wrapper

**Test Execution Pipeline**:
1. **Separate**: Divide changed files into source and test files
2. **Resolve**: Convert paths to fully qualified class names
   - Test files: Direct conversion
   - Source files: Delegate to `TestNameResolver.resolve()`
3. **Verify**: Check test files exist (for inferred tests only)
4. **Build**: Construct Maven command
   - Detect Maven executable (`./mvnw`, `mvnw.cmd`, or `mvn`)
   - Add `--batch-mode test -Dtest=Class1,Class2,...`
   - Add tag filtering: `-Dgroups=...`, `-DexcludedGroups=...`
   - Add Surefire options (rerun, parallel, etc.)
   - Add output control: `-Dsurefire.useFile=false -Dsurefire.tempDir=none` (default)
   - Add temp directory: `-Djava.io.tmpdir=<project.basedir>/target/tmp` (default)
   - Add additional properties from config
5. **Execute**: Run via ProcessBuilder with `inheritIO()` for real-time output
6. **Report**: Return exit code (0 = success, non-zero = failure)
   - Success (exit code 0): Silent (no messages)
   - Failure (non-zero): Display error message with exit code

---

### 4. TestNameResolver - Naming Convention Engine

**Responsibilities**:
- Map source files to test class names
- Support multiple naming conventions
- Preserve package structure
- Convert paths to fully qualified class names

**Naming Conventions**:
- Standard tests: `{ClassName}Test`, `{ClassName}Tests`
- Integration tests: `{ClassName}IT`, `{ClassName}IntegrationTest`

**Example**: `Foo.java` → `[FooTest, FooTests, FooIT, FooIntegrationTest]`

---

### 5. PathMatcher - File Filtering

**Responsibilities**:
- Apply include/exclude patterns to files
- Check directory exclusion patterns
- Enforce precedence (excludes win)
- Support glob and regex syntax

**Default Patterns**:
- Include: `**/*.java`, `**/*.kt`
- Exclude: None (monitors both source and test files)

**Directory Exclusion**:
- `matchesDirectory()` checks if directories should be excluded from watching
- Supports both path and path-with-trailing-slash matching

---

### 6. WatcherConfig - Configuration Data

**Contents**:
- **Directories**: source, test, additional watch paths
- **Watch settings**: mode (continuous/one-shot), debounce, recursive
- **Filter patterns**: includes, excludes (glob patterns)
- **Surefire options**: rerun count, parallel, order, thread count, skip after failure
- **Output control**: useFile flag (suppress .surefire-* files), tempDir (temporary directory path)
- **Tag filtering**:
  - `watcherGroups` / `groups`: JUnit 5 tag expressions to include
  - `watcherExcludedGroups` / `excludedGroups`: JUnit 5 tag expressions to exclude
  - Priority logic: `watcher.*` parameters take precedence over standard parameters
- **Additional properties**: custom Maven properties (Map<String, String>)
- **Logging**: verbose flag

**Pattern**: Data Transfer Object (DTO) - pure data holder, no behavior

**Tag Filtering Priority**:
- `getEffectiveGroups()`: Returns `watcherGroups` if set, otherwise `groups`
- `getEffectiveExcludedGroups()`: Returns `watcherExcludedGroups` if set, otherwise `excludedGroups`
- Empty strings are treated as "not set"

---

### 7. QueueOrchestrator - Change Accumulator

**Purpose**: Queue file changes detected during test execution (watchMode=true only)

**Responsibilities**:
- Accumulate file changes while tests are running
- Track test execution state (running/complete)
- Provide thread-safe enqueue/dequeue operations
- Automatically remove duplicate files
- Preserve insertion order of changes

**Key Behavior**:
- Thread-safe using `synchronized` blocks and `AtomicBoolean`
- Duplicate removal via `LinkedHashSet` (same file queued multiple times = one entry)
- Order preservation (first queued, first in the list)

**Lifecycle**:
```
1. Initialize: new QueueOrchestrator() (watchMode=true only)
2. Before test: markTestRunning()
3. During test: enqueue(changedFiles) → returns true (queued)
4. After test: markTestComplete()
5. Check queue: isEmpty() → false if files queued
6. Process queue: dequeue() → returns all files, clears queue
7. Repeat: Loop until queue is empty
```

**API Methods**:
- `enqueue(List<Path>)`: Add files to queue
- `dequeue()`: Get all files and clear queue
- `requeueAtFront(List<Path>)`: Restore failed batch to front
- `markTestRunning()` / `markTestComplete()`: Manage test execution state
- `isEmpty()`, `clear()`, `isTestRunning()`: State inspection

**Test Failure Handling**:
- On test failure: Failed batch is requeued at front via `requeueAtFront()`
- Failed batch gets priority over newly queued files
- Next file change: New changes merged after requeued batch

---

### 8. Factory Components - Testability Layer

The Factory pattern enables dependency injection for testing, allowing `execute()` to be testable without blocking.

#### FileWatcherFactory (Interface)
```java
@FunctionalInterface
FileWatcher create(List<Path> watchPaths, long debounceMs, boolean recursive,
                  PathMatcher pathMatcher, Log log) throws IOException;
```

#### DefaultFileWatcherFactory (Production)
- Simple pass-through to FileWatcher constructor
- Used by default in WatcherMojo
- Creates real FileWatcher instances with full monitoring capabilities

#### TestFileWatcherFactory (Testing)
- Creates non-blocking FileWatcher instances for tests
- Overrides `watch()` method to return immediately (prevents test hanging)
- Can simulate file changes by triggering callback with test files
- **Impact**: execute() coverage 0% → 55-60%, overall coverage 64% → 71%

---

## Data Flow Pipeline

**Standard Flow** (test not running):
```
File System Events → FileWatcher → PathMatcher → Selector → TestNameResolver → Maven Surefire
```

**Queue Flow** (test running, watchMode=true):
```
File System Events → FileWatcher → PathMatcher → QueueOrchestrator.enqueue()
  [Test completes] → QueueOrchestrator.dequeue() → Selector → TestNameResolver → Maven Surefire
  [Loop until queue empty]
```

### Detailed Pipeline Phases

```
Phase 1: Initialization
  ├─ Validate configuration
  ├─ Create WatcherConfig (immutable)
  ├─ Create PathMatcher, Selector
  └─ Create FileWatcher (via factory)

Phase 2: Monitoring
  ├─ Register directories with WatchService
  ├─ Poll for events (100ms loop)
  ├─ Accumulate changes in Set
  └─ Wait for debounce period (default 750ms)

Phase 3: Filtering
  ├─ Check include patterns (**/*.java, **/*.kt)
  ├─ Check exclude patterns (if any)
  └─ Return filtered file list

Phase 4: Queue Check (watchMode=true only)
  ├─ Test running? → enqueue() → return
  └─ Test not running → continue to Phase 5

Phase 5: Queue Processing (watchMode=true only)
  ├─ Enqueue current changes
  └─ WHILE (!isEmpty()):
      ├─ Dequeue all files
      ├─ Mark test as running
      ├─ Process files (Phase 6 → Phase 8)
      ├─ If test fails: requeueAtFront() → break
      └─ Mark test as complete

Phase 6: Test Selection
  ├─ Separate: Split into test files and source files
  ├─ Test files → Convert to class names directly
  ├─ Source files → TestNameResolver.resolve() → candidates
  ├─ Verify: Check inferred test files exist on disk
  └─ Return: List of fully qualified test class names

Phase 7: Test Execution
  ├─ Detect Maven executable (mvnw vs mvn)
  ├─ Build command with Surefire options
  ├─ Execute via ProcessBuilder (inherited I/O)
  └─ Return exit code

Phase 8: Result Handling
  ├─ Watch mode: Log result, continue monitoring
  │   └─ If queue not empty: loop back to Phase 5
  └─ One-shot mode: Throw exception on failure, exit
```

### State Transitions

**Watch Mode** (continuous, with queue):
```
Monitoring → Change Detected → Filter → Queue Check
  ├─ Test Running: Enqueue → [Back to Monitoring]
  └─ Test Not Running: Process Changes
        ↓
      Mark Running → Select → Execute → Mark Complete
        ↓
      Queue Empty?
        ├─ Yes: [Back to Monitoring]
        └─ No: Dequeue → [Loop back to Select]
```

**One-Shot Mode** (single run):
```
Monitoring → Change Detected → Filter → Select → Execute → Exit
```

## Configuration Reference

| Setting | Default Value | Purpose |
|---------|---------------|---------|
| **File patterns** | Include: `**/*.java`, `**/*.kt`<br>Exclude: None | Monitor all Java/Kotlin files |
| **Debounce** | 750ms | Batch rapid changes |
| **Watch mode** | Continuous | Keep monitoring after test execution |
| **Recursive** | true | Monitor subdirectories automatically |
| **Polling interval** | 100ms (internal) | How often to check for events |
| **Tag filtering** | None | No JUnit 5 tag filtering |
| **Additional properties** | Empty map | Custom Maven properties |

## Key Behaviors (Invariants)

### What Gets Ignored
1. **Non-existent tests**: Silently filtered out (no error) - only for inferred tests from source files
2. **Files outside source/test directories**: Ignored unless explicitly included via `watchDirectories`

### Error Handling Modes

**Watch Mode** (resilient):
- Test failures logged as errors
- Watcher continues monitoring
- Suitable for development workflow

**One-Shot Mode** (strict):
- Test failures throw `MojoExecutionException`
- Build fails immediately
- Suitable for CI/CD pipelines

### Dynamic Behavior
- **New directories**: Automatically registered when created (recursive mode)
- **Deleted directories**: Watch keys removed gracefully
- **Multiple changes**: Batched by debounce period

## Testing Strategy

### Test Organization
```
src/test/java/com/sennproject/maven/plugin/watch/files/
├── FileWatcherTest.java                - Debounce, directory registration, filtering
├── SelectorTest.java                   - Test selection, command building
├── TestNameResolverTest.java           - Naming convention mappings
├── PathMatcherTest.java                - Pattern matching, precedence
├── WatcherConfigTest.java              - Configuration, effective groups logic
├── QueueOrchestratorTest.java          - Queue operations (24 test cases)
├── FileChangeIntegrationTest.java      - End-to-end flow
└── TagFilteringIntegrationTest.java    - JUnit 5 @Tag annotation tests
```

### Testing Approach
- **Unit tests**: Test each component in isolation
- **Integration tests**: End-to-end flows using TestFileWatcherFactory
- **JUnit 5**: `@Test`, `@ParameterizedTest` for multiple scenarios
- **Mockito**: Mock Maven components (`MavenProject`, `Log`)
- **Temporary files**: `@TempDir` for file system tests
- **Spies**: Verify method calls without running Maven subprocess
- **Factory pattern**: TestFileWatcherFactory enables testing `execute()` method
- **Coverage**: JaCoCo reports in `target/site/jacoco/`

### What We Test
1. **FileWatcher**: Debounce timing, recursive registration, filter integration, directory exclusion
2. **Selector**: Directory filtering, test existence checks, Maven command structure, tag filtering, Maven wrapper detection
3. **TestNameResolver**: All naming convention combinations, package preservation
4. **PathMatcher**: Glob matching, include/exclude precedence, directory matching
5. **WatcherConfig**: Data holder behavior, null handling, effective groups priority logic
6. **QueueOrchestrator**: Enqueue/dequeue, duplicate removal, order preservation, state management, requeue functionality (24 test cases)
7. **WatcherMojo**: Configuration validation, handleFileChanges behavior, watch mode vs one-shot mode, queue integration
8. **FileChangeIntegrationTest**: Complete pipeline from file change to test execution
9. **TagFilteringIntegrationTest**: JUnit 5 @Tag annotations for different test categories

## Debugging & Logging

### Log Levels
- `log.info()`: User-facing information (file changes detected, tests selected/executed)
- `log.debug()`: Detailed diagnostic information (requires `-X` or verbose mode)
- `log.warn()`: Non-fatal issues (OVERFLOW events, missing test files in verbose mode)
- `log.error()`: Fatal errors (test execution failures in one-shot mode)

### Enable Debug Logs
```bash
mvn watcher:watch -X                          # Full Maven debug
mvn watcher:watch -Dwatcher.verbose=true      # Verbose mode (recommended)
```

### Troubleshooting Flow
When `executeTests` is not called, check logs in this order:

1. **FileWatcher detects changes?**
   - Look for: `[DEBUG] File system event detected`
   - If missing: Check IDE auto-save, file system type, inotify limits

2. **PathMatcher filters files?**
   - Look for: `[DEBUG] After filtering: N file(s)`
   - If N=0: Check includes/excludes configuration

3. **Callback invoked?**
   - Look for: `[DEBUG] Invoking callback (handleFileChanges)`
   - If missing: Check debounceMs setting

4. **Tests found?**
   - Look for: `[DEBUG] Categorized files - source: X, test: Y`
   - If both 0: Files not under sourceDirectory/testSourceDirectory

5. **selectTests returns results?**
   - Look for: `[INFO] Selected N test(s)` or `[INFO] No tests to run`
   - If empty: Verify test naming conventions and file existence

**Detailed debugging documentation**: See [debugging.md](./debugging.md)
