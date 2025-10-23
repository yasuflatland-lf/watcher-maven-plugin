# Watch Files Maven Plugin

A Maven plugin that monitors file changes and automatically runs affected tests only.

[![](https://jitpack.io/v/yasuflatland-lf/watcher-maven-plugin.svg)](https://jitpack.io/#yasuflatland-lf/watcher-maven-plugin)
![Coverage](.github/badges/jacoco.svg)
![Branches](.github/badges/branches.svg)

## Requirements

- Java 21 or higher
- Maven 3.9.11 or higher
- JUnit 5 test framework

## Installation

### 1. Add the JitPack plugin repository

```xml
<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>
```

### 2. Add the plugin to your `pom.xml`

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.github.yasuflatland-lf</groupId>
            <artifactId>watcher-maven-plugin</artifactId>
            <version>1.0.0</version>  <!-- Use git tag version -->
        </plugin>
    </plugins>
</build>
```

**Version options:**
- **Stable releases**: Use git tags (e.g., `v1.0.0`, `v1.1.0`)

No GitHub credentials required - JitPack builds directly from the public repository.

## Usage

### Basic Usage

```bash
# Continuously watch files and run tests
mvn watcher:watch

# One-shot mode (detect changes once)
mvn watcher:watch -Dwatcher.watchMode=false
```

**Memory Configuration (Recommended):**
For proper test execution, configure memory settings in your `pom.xml` using `additionalProperties`. The default recommendation is **4GB** (`-Xmx4096m`). See [Configuration Example](#configuration-example) for details.

### Configuration Parameters

Below are the available configuration parameters with their default values:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `watchMode` | boolean | `true` | Continuous monitoring mode. Set to `false` for one-shot execution. |
| `debounceMs` | long | `750` | Debounce time in milliseconds after file change detection. |
| `rerunFailingTestsCount` | int | `0` | Number of times to rerun failed tests (for handling flaky tests). |
| `runOrder` | String | `"filesystem"` | Test execution order. Options: `filesystem`, `alphabetical`, `reversealphabetical`, `random`, `hourly`, `failedfirst`. |
| `parallel` | boolean | `false` | Enable parallel test execution. |
| `threadCount` | int | `1` | Number of threads for parallel execution. |
| `skipAfterFailureCount` | int | `0` | Number of tests to skip after failure (0 = no skipping). |
| `verbose` | boolean | `false` | Enable additional debug information. Always shows changed files and selected tests. When enabled, also shows Maven commands and intermediate test resolution details. |
| `recursive` | boolean | `true` | Monitor directories recursively. |
| `includes` | List<String> | `["**/*.java"]` | File patterns to include (glob patterns). |
| `excludes` | List<String> | `[]` | File patterns to exclude (glob patterns). |
| `groups` | String | `null` | JUnit 5 tag expression to include tests. |
| `excludedGroups` | String | `null` | JUnit 5 tag expression to exclude tests. |
| `useFile` | boolean | `false` | Enable Surefire file-based output. When `false` (default), suppresses `.surefire-*` temporary files. |
| `tempDir` | String | `"target/tmp"` | Temporary directory for `java.io.tmpdir`. Redirects temporary files to prevent `.surefire-*` files in project root. |
| `additionalProperties` | Map | `{}` | Additional Maven/Surefire properties. |

### Configuration Example
Add below to your `pom.xml`. 

```xml
<plugin>
    <groupId>com.github.yasuflatland-lf</groupId>
    <artifactId>watcher-maven-plugin</artifactId>
    <version>v1.0.0</version>
    <configuration>
        <!-- File change monitoring mode -->
        <watchMode>true</watchMode>

        <!-- Debounce time (wait after file change detection before running tests) -->
        <debounceMs>750</debounceMs>

        <!-- Retry failed tests (for handling flaky tests) -->
        <rerunFailingTestsCount>0</rerunFailingTestsCount>

        <!-- Test execution order (default: filesystem) -->
        <runOrder>filesystem</runOrder>

        <!-- Parallel execution (disabled for TestContainers compatibility) -->
        <parallel>false</parallel>

        <!-- Monitored file patterns -->
        <includes>
            <include>**/*.java</include>
            <include>**/*.yaml</include>
            <include>**/*.sql</include>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
        </includes>

        <!-- Excluded patterns -->
        <excludes>
            <exclude>**/target/**</exclude>
            <exclude>**/node_modules/**</exclude>
            <exclude>**/.git/**</exclude>
            <!-- Exclude OpenAPI auto-generated files -->
            <exclude>**/generated-sources/**</exclude>
        </excludes>

        <!-- Additional Maven Surefire configuration -->
        <additionalProperties>
            <!-- Test execution memory settings (default recommendation: 4GB) -->
            <argLine>-Xmx4096m -XX:+EnableDynamicAgentLoading</argLine>
            <!-- Single fork execution (for TestContainers usage) -->
            <forkCount>1</forkCount>
            <reuseForks>true</reuseForks>
        </additionalProperties>
    </configuration>
</plugin>
```

**Notes:**
- The plugin must be run explicitly with `mvn watcher:watch` (it does not run automatically during the build lifecycle)
- Many settings in the example above use default values and can be omitted if unchanged
- **Memory Settings (Important):**
  - **Default recommendation**: 4GB (`-Xmx4096m`) for reliable test execution
  - Configure via `<argLine>` in `<additionalProperties>` section of your `pom.xml`
  - Adjust based on your test requirements (increase for TestContainers, large datasets, etc.)
- **Output Levels:**
  - **Always displayed**: Changed file list, selected test list, test execution results
  - **With `verbose=true`**: Maven commands, intermediate test resolution (source→test mapping), debug information
- For TestContainers projects, use `parallel=false` and `forkCount=1` to avoid container conflicts
- The `-XX:+EnableDynamicAgentLoading` flag is required for Java 21+ with dynamic agent loading

### Advanced Configuration

#### JUnit 5 Tag Filtering (Groups)

Filter which tests to run based on JUnit 5 `@Tag` annotations. This is useful for running only specific categories of tests (e.g., WIP tests, fast tests, excluding slow/integration tests).

```xml
<configuration>
    <!-- Include tests with specific tags -->
    <groups>WIP</groups>

    <!-- Exclude tests with specific tags (takes precedence over includes) -->
    <excludedGroups>slow,integration</excludedGroups>
</configuration>
```

**Tag Expression Syntax:**
- `,` (comma) - OR operator: `WIP,fast` runs tests tagged with WIP OR fast
- `&` (ampersand) - AND operator: `unit&fast` runs tests tagged with both unit AND fast
- `!` (exclamation) - NOT operator: `!slow` runs tests NOT tagged with slow

**Command Line Usage:**

```bash
# Plugin-specific parameter (recommended)
mvn watcher:watch -Dwatcher.groups=WIP

# Maven Surefire compatible parameter (also supported)
mvn watcher:watch -Dgroups=WIP

# Combine include and exclude
mvn watcher:watch -Dwatcher.groups="unit,fast" -Dwatcher.excludedGroups=slow

# Complex expressions
mvn watcher:watch -Dwatcher.groups="unit&fast" -Dwatcher.excludedGroups="slow,integration"
```

**Parameter Priority:**
- `watcher.groups` takes precedence over `groups`
- `watcher.excludedGroups` takes precedence over `excludedGroups`
- Excluded groups always take precedence over included groups

**Example Test Class:**

```java
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class MyServiceTest {
    @Test
    @Tag("WIP")
    void workInProgressTest() {
        // Only runs when -Dgroups=WIP
    }

    @Test
    @Tag("unit")
    @Tag("fast")
    void fastUnitTest() {
        // Runs when -Dgroups=unit OR -Dgroups=fast
    }

    @Test
    @Tag("slow")
    void slowTest() {
        // Excluded when -DexcludedGroups=slow
    }

    @Test
    void untaggedTest() {
        // Always runs (unless groups is specified)
    }
}
```

**Use Cases:**
- **WIP (Work In Progress)**: Run only tests you're currently working on: `-Dwatcher.groups=WIP`
- **Fast feedback loop**: Run only fast tests during development: `-Dwatcher.groups=fast -Dwatcher.excludedGroups=slow`
- **Skip integration tests**: Run unit tests only: `-Dwatcher.excludedGroups=integration`
- **CI/CD**: Run different test suites in different stages


#### Directory Exclusion

The `excludes` parameter now supports directory patterns to prevent monitoring entire directory trees:

```xml
<configuration>
    <excludes>
        <!-- Exclude directories -->
        <exclude>**/target/**</exclude>
        <exclude>**/node_modules/**</exclude>
        <exclude>**/.git/**</exclude>
        <exclude>**/build/**</exclude>

        <!-- Exclude file patterns -->
        <exclude>**/*Test.java</exclude>
        <exclude>**/*.log</exclude>
    </excludes>
</configuration>
```

**Benefits:**
- Reduces CPU usage by skipping irrelevant directories
- Prevents false triggers from build output or dependencies
- Improves performance in large projects

#### Additional Maven Properties

Pass arbitrary Maven/Surefire properties to the test command:

```xml
<configuration>
    <additionalProperties>
        <!-- JVM arguments -->
        <argLine>-Xmx2048m -Xms512m</argLine>

        <!-- Surefire fork configuration -->
        <forkCount>4</forkCount>
        <reuseForks>true</reuseForks>

        <!-- Custom system properties -->
        <my.custom.property>value</my.custom.property>
        <test.environment>development</test.environment>
    </additionalProperties>
</configuration>
```

These properties are appended as `-Dkey=value` arguments to the Maven test command.

#### Surefire Output Control

The plugin suppresses `.surefire-*` temporary files by default to keep your workspace clean.

**Default behavior** (`useFile=false`):
- No `.surefire-*` files created in project root
- Test reports available in `target/surefire-reports/`
- Silent on success, error messages on failure
- Command: `mvn watcher:watch`

**Enable file output** (`useFile=true`):
- Creates `.surefire-*` temporary files
- Useful for debugging Surefire issues
- Command: `mvn watcher:watch -Dwatcher.useFile=true`

**Configuration:**
```xml
<configuration>
    <useFile>false</useFile>  <!-- Default -->
</configuration>
```

### Command Line Arguments

```bash
# Disable watch mode
mvn watcher:watch -Dwatcher.watchMode=false

# Change debounce time
mvn watcher:watch -Dwatcher.debounceMs=2000

# Rerun failed tests 3 times
mvn watcher:watch -Dwatcher.rerunFailingTestsCount=3

# Parallel execution
mvn watcher:watch -Dwatcher.parallel=true -Dwatcher.threadCount=4

# Enable verbose logging (shows Maven commands and debug info)
# Note: Changed files and selected tests are always shown
mvn watcher:watch -Dwatcher.verbose=true

# Tag filtering (run only WIP tests)
mvn watcher:watch -Dwatcher.groups=WIP

# Tag filtering (exclude slow tests)
mvn watcher:watch -Dwatcher.excludedGroups=slow,integration

# Enable Surefire file output (creates .surefire-* files)
mvn watcher:watch -Dwatcher.useFile=true

# Additional properties via command line
mvn watcher:watch -Dwatcher.additionalProperties.argLine="-Xmx2048m"
```

## How It Works

### High-Level Flow

1. **File Monitoring (`FileWatcher`)**: Registers both source and test directories, listens for create/modify/delete events, debounces noisy bursts, and filters paths through `PathMatcher`.
2. **Test Selection (`Selector` + `TestNameResolver`)**:
   - **Test files changed**: Runs those tests directly
   - **Source files changed**: Infers corresponding test class names using naming conventions
   - Filters to only classes that exist on disk
3. **Test Execution (`Selector`)**: Builds the `mvn test` command line with Surefire options (reruns, run order, parallel settings, tag filtering) and runs it in batch mode from the project root.
4. **Result Reporting (`WatcherMojo`)**: Logs outcomes, handles rerun counts, and stops after one pass when `watchMode=false`.

### File Monitoring Details

The plugin uses **Java NIO WatchService** for efficient file system monitoring:

**Monitoring Mechanism:**
- **Events Detected**: `CREATE`, `DELETE`, `MODIFY`
- **Polling Interval**: 100ms (internal, for checking events)
- **Debounce Period**: 750ms (default, configurable via `debounceMs`)
  - Accumulates rapid file changes in a buffer
  - Waits for quiet period after last change
  - Batches multiple changes into single test run

**Recursive Directory Watching:**
- Automatically registers all subdirectories when `recursive=true` (default)
- Newly created directories are detected and registered automatically
- Supports skipping excluded directories (see Directory Exclusion below)

**Directory Exclusion Optimization:**
- Checks `PathMatcher.matchesDirectory()` before registering directories
- Skips entire directory trees matching exclusion patterns
- Prevents unnecessary WatchService registrations for:
  - Build outputs: `**/target/**`, `**/build/**`
  - Dependencies: `**/node_modules/**`, `**/.gradle/**`
  - IDE files: `**/.idea/**`, `**/.vscode/**`
  - Version control: `**/.git/**`
- Reduces CPU usage and memory footprint in large projects

**Thread Safety:**
- Uses `ConcurrentHashMap` for managing watch keys
- Uses `AtomicBoolean` for tracking running state
- Safe for concurrent file system events

**Watch Modes:**
- **Continuous mode** (`watchMode=true`, default): Keeps monitoring after test execution
- **One-shot mode** (`watchMode=false`): Detects changes once and exits
  - Useful for CI/CD pipelines
  - Fails the build if tests fail

**File Filtering:**
- Default: Monitors `**/*.java` and `**/*.kt` files (including test files)
- When test files change, they are run directly
- When source files change, corresponding tests are inferred and run
- Customizable via `includes` and `excludes` parameters
- Exclude patterns take precedence over include patterns

### Naming Conventions

The plugin infers test classes using these fixed patterns:

| Source File | Inferred Test Classes |
|------------|----------------------|
| `Foo.java` | `FooTest`, `FooTests`, `FooIT`, `FooIntegrationTest` |
| `com/example/Service.kt` | `com.example.ServiceTest`, `com.example.ServiceTests`, `com.example.ServiceIT`, `com.example.ServiceIntegrationTest` |

**How it works**:
- When a source file changes (e.g., `Calculator.java`), the plugin looks for corresponding test files
- Test files are searched using these suffix patterns: `Test`, `Tests`, `IT`, `IntegrationTest`
- Package structure is preserved (`com.example.Calculator` → `com.example.CalculatorTest`)
- Only test files that actually exist on disk are executed
- Test files themselves (when changed) are run directly without inference

## Continuous Integration

All pull requests, pushes to `main`, and release creation run through GitHub Actions (`.github/workflows/ci.yml`). The workflow builds the plugin with `mvn --batch-mode --update-snapshots clean verify` on Ubuntu using Temurin JDK 21.

| Event | Actions |
|-------|---------|
| Pull request to `main` | ✅ Build & test<br>📊 Upload test results (XML) |
| Push to `main` | ✅ Build & test<br>📊 Upload test results (XML) |
| Release created | ✅ Build & test<br>📊 Upload test results (XML)<br>📦 Upload build artifacts (JAR, POM) |

**Note:** The workflow does not publish to any Maven repository. JitPack handles building and serving artifacts directly from the GitHub repository.

## Development

### Build

```bash
mvn clean install
```

### Run Tests

```bash
mvn test
```

### Local Usage

```bash
# Install plugin locally
mvn clean install

# Use in other projects
cd /path/to/your/project
mvn watcher:watch
```

### Release Process

**Quick steps** to release a new stable version:

1. **Update version in pom.xml**:
   ```xml
   <version>1.1.0</version>
   ```

2. **Create and push annotated tag**:
   ```bash
   git tag -a v1.1.0 -m "Version 1.1.0 - Feature summary"
   git push origin v1.1.0
   ```

3. **GitHub Actions runs automatically**:
   - Builds and tests the release
   - Uploads artifacts (JAR, POM) for inspection

4. **(Optional) Create GitHub Release**:
   - Navigate to Releases page on GitHub
   - Create release from the pushed tag
   - Add release notes

5. **JitPack builds on first request**:
   - Visit `https://jitpack.io/#yasuflatland-lf/watcher-maven-plugin/v1.1.0` to trigger build
   - Monitor build logs in real-time
   - Consumers can immediately use the new version

**Consumers use the released version**:

```xml
<plugin>
    <groupId>com.github.yasuflatland-lf</groupId>
    <artifactId>watcher-maven-plugin</artifactId>
    <version>v1.1.0</version>  <!-- Use the git tag -->
</plugin>
```

For detailed release checklist and best practices, see [docs/release.md](docs/release.md).

## Troubleshooting

### File changes not detected

- Depending on the file system type, WatchService may not be supported
- Increasing debounce time may improve: `-Dwatcher.debounceMs=2000`
- Check if files are in excluded directories (e.g., `**/target/**`, `**/build/**`)

### Tests not found

- The plugin always shows changed files and selected tests - check this output first
- Verify that test file naming conventions are correct (Test, Tests, IT, IntegrationTest suffixes)
- Enable verbose mode for detailed test resolution logs: `-Dwatcher.verbose=true`
- Verify test classes exist in the test source directory

### Tests not executed

- Verify that source and test directory paths are correct
- If using Maven Wrapper, ensure that `mvnw` is executable

### Performance issues in large projects

- Use directory exclusion to skip irrelevant directories:
  ```xml
  <excludes>
      <exclude>**/target/**</exclude>
      <exclude>**/node_modules/**</exclude>
      <exclude>**/.git/**</exclude>
  </excludes>
  ```
- Increase debounce time to reduce test execution frequency
- Consider using `watchMode=false` for one-shot testing

### Custom Maven/Surefire properties not applied

- Verify the `additionalProperties` configuration syntax
- Check the generated Maven command with `-Dwatcher.verbose=true`
- Ensure property names are correct (refer to [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/) documentation)

## License

Apache License 2.0

## Author

Yasuyuki Takeo

## Reference Projects

- [fizzed-watcher-maven-plugin](https://github.com/fizzed/java-maven-plugins)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
