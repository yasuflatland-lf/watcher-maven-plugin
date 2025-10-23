# Coding Rules

## Language and Style

- **Java 21**: All source code uses Java 21 features and syntax
- **Indentation**: Use 4 spaces (no tabs) consistently
- **Brace placement**: K&R style (opening brace on same line, closing brace on new line)
- **Annotations**: Place `@Mojo` and `@Parameter` directly above the fields/classes they describe
- **Imports**: No wildcard imports; organize logically (java.*, javax.*, third-party, local)

## Class Design Principles

### Naming Conventions

Name classes after domain concepts they implement:
- `FileWatcher`: File system monitoring
- `Selector`: Test selection and execution
- `TestNameResolver`: Test name generation
- `PathMatcher`: File pattern matching
- `WatcherConfig`: Configuration data holder
- `WatcherMojo`: Maven plugin entry point

### Dependency Injection

**Constructor injection**: Make dependencies explicit
```java
public Selector(MavenProject project, WatcherConfig config, Log log) {
    this.project = project;
    this.config = config;
    this.log = log;
}
```

**Factory methods**: Use for complex object creation (e.g., `PathMatcher.forTargetCode()`)

### Separation of Concerns

- **WatcherMojo**: Entry point only - validates config, wires components, handles callbacks
- **FileWatcher**: Pure file monitoring - no business logic about tests
- **Selector**: Test selection and execution - no file monitoring logic
- **TestNameResolver**: Pure utility functions - no state, no dependencies
- **PathMatcher**: Pattern matching only - no file I/O
- **WatcherConfig**: Data holder only - no behavior

### Error Handling

- Use `MojoExecutionException` for Maven-specific errors that should stop execution
- Use `IOException` for file I/O errors
- Log warnings for non-fatal issues (missing directories, non-existent test files)
- Validate early in `WatcherMojo.validateConfiguration()` before starting work

### Logging Best Practices

Use appropriate log levels:

```java
// Debug: Detailed diagnostic information (requires -X or verbose mode)
if (log.isDebugEnabled()) {
    log.debug("Processing " + files.size() + " changed file(s)");
}

// Info: User-facing information
log.info("File changes detected!");
log.info("Selected " + testClasses.size() + " test(s)");

// Warn: Non-fatal issues
log.warn("WatchEvent OVERFLOW detected - some file changes may have been missed");

// Error: Fatal errors
log.error("Tests failed with exit code: " + exitCode);
```

**Guidelines**:
- Always check `log.isDebugEnabled()` before calling `log.debug()` to avoid string concatenation overhead
- Use debug logs liberally for troubleshooting - only visible with `-X` or verbose mode
- Info logs should be concise and actionable
- Include relevant context (file counts, class names, error codes)

## Testing Guidelines

### Test Organization

- **Location**: `src/test/java/com/sennproject/maven/plugin/watch/files/`
- **Naming**: `{ClassName}Test.java` (e.g., `FileWatcherTest`, `SelectorTest`)
- **Package structure**: Mirror production code exactly

### Test Framework

- **JUnit 5**: Use `@Test`, `@ParameterizedTest`, `@BeforeEach`, `@AfterEach`
- **Mockito**: Use for mocking Maven components (`MavenProject`, `Log`)
- **AssertJ** (optional): Use for fluent assertions if preferred

### Test Patterns

**Parameterized tests**: Use `@ParameterizedTest` for multiple scenarios
```java
@ParameterizedTest
@ValueSource(strings = {"Foo.java", "Bar.java"})
void testMultipleFiles(String fileName) {
    // ...
}
```

**Temporary directories**: Use JUnit's `@TempDir`
```java
@Test
void testFileWatcher(@TempDir Path tempDir) {
    // ...
}
```

**Mockito verification**: Verify interactions
```java
verify(log).info("File changes detected!");
```

**Test isolation**: Each test should be independent and repeatable

### Coverage Expectations

- **Unit tests**: Test each component in isolation
- **Integration tests**: Verify end-to-end flows (e.g., FileChangeIntegrationTest)
- **Integration points**: Verify component interactions (e.g., FileWatcher → Selector)
- **Edge cases**: Empty lists, null handling, invalid paths
- **Configuration validation**: All parameter validation logic

### Integration Test Pattern

Integration tests verify the complete pipeline from file change to test execution:

```java
@Test
void testFileChangeTriggersExecuteTests() throws Exception {
    // Given: Source file and test file
    Path sourceFile = srcDir.resolve("Calculator.java");
    Files.createFile(testDir.resolve("CalculatorTest.java"));

    // Create spy to verify executeTests is called
    Selector spySelector = spy(new Selector(project, config, mockLog));
    lenient().when(spySelector.executeTests(anyList())).thenReturn(true);

    // When: File change is detected
    // ... FileWatcher triggers callback ...

    // Then: Verify executeTests was called
    verify(spySelector, times(1)).executeTests(argThat(list ->
        list.contains("CalculatorTest")
    ));
}
```

**Key practices**:
- Use Mockito spies to verify method calls without executing Maven
- Use `lenient()` when stubbing methods that throw checked exceptions
- Declare test methods with `throws Exception` for checked exception handling
- Test both positive flows (tests found and executed) and negative flows (no tests found)

### Testing Private Methods

Use reflection helpers for testing private methods while maintaining encapsulation:

```java
/**
 * Helper to invoke private methods and unwrap InvocationTargetException
 */
private Object invokePrivateMethod(Object target, String methodName, Object... args) throws Exception {
    Class<?>[] parameterTypes = Arrays.stream(args)
        .map(Object::getClass)
        .toArray(Class[]::new);

    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);

    try {
        return method.invoke(target, args);
    } catch (InvocationTargetException e) {
        throw (Exception) e.getCause();
    }
}

/**
 * Helper to assert that a private method throws a specific exception
 */
private <T extends Throwable> T assertPrivateMethodThrows(
        Class<T> expectedType,
        Object target,
        String methodName,
        Object... args) {
    return assertThrows(expectedType, () -> {
        invokePrivateMethod(target, methodName, args);
    });
}
```

**Usage**:
```java
@Test
void validateConfiguration_shouldThrowWhenSourceDirectoryMissing() throws Exception {
    setField(mojo, "sourceDirectory", new File("non-existent"));

    MojoExecutionException exception = assertPrivateMethodThrows(
        MojoExecutionException.class,
        mojo,
        "validateConfiguration"
    );

    assertTrue(exception.getMessage().contains("Source directory does not exist"));
}
```

## Build and Verification

### Standard Workflow

```bash
# Generate plugin descriptor (required before running tests)
mvn compile

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FileWatcherTest

# Run specific test method
mvn test -Dtest=FileWatcherTest#testDebounce

# Run tests with coverage report
mvn clean test
# View report at target/site/jacoco/index.html
```

**Important**: Always run `mvn compile` before running tests to generate the plugin descriptor at `target/classes/META-INF/maven/plugin.xml`.

### Plugin Testing

```bash
# One-shot mode (detect changes once and exit)
mvn watcher:watch -Dwatcher.watchMode=false

# Continuous watch mode
mvn watcher:watch

# Verbose logging
mvn watcher:watch -Dwatcher.verbose=true

# Debug mode (shows Maven internals)
mvn -X watcher:watch

# Custom configuration
mvn watcher:watch \
  -Dwatcher.debounceMs=1000 \
  -Dwatcher.includes="**/*.java" \
  -Dwatcher.excludes="**/*IT.java"

# With tag filtering
mvn watcher:watch \
  -Dwatcher.groups=WIP \
  -Dwatcher.excludedGroups=slow,integration

# With additional Maven properties
mvn watcher:watch \
  -Dwatcher.additionalProperties.spring.profiles.active=test \
  -Dwatcher.additionalProperties.test.database=h2
```

### Pre-commit Checklist

1. Run `mvn compile` - generate plugin descriptor
2. Run `mvn clean test` - all tests must pass
3. Check `target/site/jacoco/index.html` - maintain or improve coverage
4. Run `mvn watcher:watch -Dwatcher.watchMode=false` on a real project to verify end-to-end
5. Review logs for warnings or errors
6. Verify changes match coding standards (indentation, naming, etc.)

### Release Checklist

Before creating a release:

1. Ensure all tests pass on main branch: `mvn clean verify`
2. Update documentation (README.md, docs/) if needed
3. Verify version number follows [Semantic Versioning](https://semver.org/)
4. Prepare release notes (features, bug fixes, breaking changes)
5. Test the plugin manually in a real project

For detailed release instructions, see [release.md](release.md).

## Common Patterns

### Configuration Parameters in WatcherMojo

```java
@Parameter(
    property = "watcher.propertyName",
    defaultValue = "defaultValue",
    required = false
)
private Type propertyName;
```

**Map-type parameters** for additional properties:
```java
@Parameter(property = "watcher.additionalProperties")
private Map<String, String> additionalProperties = new HashMap<>();
```

Allows users to pass arbitrary Maven properties:
```bash
mvn watcher:watch \
  -Dwatcher.additionalProperties.foo=bar \
  -Dwatcher.additionalProperties.baz=qux
```

### Path Handling

- Convert `File` to `Path` early: `file.toPath()`
- Use `Path.resolve()` for joining paths, not string concatenation
- Use `Files.exists()`, `Files.isDirectory()`, `Files.isRegularFile()` for checks
- Normalize paths when comparing: `path.normalize()`

### ProcessBuilder for External Commands

```java
ProcessBuilder processBuilder = new ProcessBuilder(command);
processBuilder.directory(project.getBasedir());
processBuilder.inheritIO();  // Show output in real-time
Process process = processBuilder.start();
int exitCode = process.waitFor();
```

### PathMatcher Usage

**File filtering**:
```java
PathMatcher matcher = PathMatcher.of(
    Arrays.asList("**/*.java", "**/*.kt"),  // includes
    Arrays.asList("**/target/**", "**/node_modules/**")  // excludes
);

if (matcher.matches(filePath)) {
    // File should be watched
}
```

**Directory exclusion** (for FileWatcher):
```java
if (pathMatcher != null && pathMatcher.matchesDirectory(directoryPath)) {
    // Skip this directory and its subtree
    return FileVisitResult.SKIP_SUBTREE;
}
```

**Common exclude patterns**:
- Build outputs: `**/target/**`, `**/build/**`
- Dependencies: `**/node_modules/**`, `**/.gradle/**`
- IDE files: `**/.idea/**`, `**/.vscode/**`
- Generated sources: `**/generated-sources/**`

## Anti-Patterns to Avoid

- ❌ Don't add business logic to `WatcherMojo` - delegate to collaborators
- ❌ Don't add stateful behavior to `TestNameResolver` - keep it pure
- ❌ Don't catch and ignore exceptions without logging
- ❌ Don't use wildcard imports (`import java.util.*`)
- ❌ Don't create test files that aren't cleaned up (`@TempDir` handles this)
- ❌ Don't use `Thread.sleep()` in production code (debounce uses event timing)
- ❌ Don't parse Maven output - use exit codes and inherited I/O
