# Debugging Guide

This document explains how to debug issues when file changes are not detected or tests are not executed.

## Enabling Debug Logs

The plugin includes comprehensive debug logging. Enable it with Maven's `-X` or `-D` options:

```bash
# Method 1: Enable full Maven debug logging (most detailed)
mvn watcher:watch -X

# Method 2: Enable plugin verbose mode (recommended for troubleshooting)
mvn watcher:watch -Dwatcher.verbose=true
```

**Note**: Changed files and selected tests are always shown by default.

## Debug Log Flow

Logs are output at each step from file change detection to test execution:

### 1. FileWatcher - File Change Detection

**Directory registration**:
```
[DEBUG] Starting directory registration...
[DEBUG] Registered directory: /workspace/src/main/java
[DEBUG] Directory registration complete. Starting file monitoring loop...
```

**File changes detected**:
```
[DEBUG] File system event detected in directory: /workspace/src/main/java
[DEBUG] Event: ENTRY_MODIFY - /workspace/src/main/java/Calculator.java
[DEBUG] Added 1 file(s) to changed files set
```

**After debounce period**:
```
[DEBUG] Debounce period elapsed. Processing 1 changed file(s)
[DEBUG] Changed files:
[DEBUG]   - /workspace/src/main/java/Calculator.java
[DEBUG] After filtering: 1 file(s)
[DEBUG] Invoking callback (handleFileChanges) to process changes...
```

### 2. WatcherMojo - Callback Processing

```
[DEBUG] handleFileChanges called with 1 file(s)
[INFO]
[INFO] ==========================================
[INFO] File changes detected!
[INFO] ==========================================
[DEBUG] Calling Selector.selectTests()...
```

**Note**: Debug messages use `String.format()` for consistent formatting.

### 3. Selector - Test Selection

```
[DEBUG] Selector.selectTests() called with 1 file(s)
[INFO] Changed files:
[INFO]   - /workspace/src/main/java/Calculator.java
[DEBUG] Categorized files - source: 1, test: 0
[INFO] Selected tests:
[INFO]   - CalculatorTest
```

**Note**: Changed files and selected tests are always displayed. With verbose mode, you'll also see intermediate test resolution details.

### 4. Selector - Test Execution

```
[DEBUG] Calling Selector.executeTests() with 1 test(s)...
[DEBUG] Selector.executeTests() called with 1 test class(es)
[INFO] ==========================================
[INFO] Executing tests...
[INFO] ==========================================
```

## Troubleshooting

### Issue 1: File Changes Not Detected

**Symptoms**: Nothing happens when you save a file

**Check**:
1. **Directory registration logs appearing?**
   ```
   [DEBUG] Registered directory: /workspace/src/main/java
   ```
   → If not appearing, check `watchDirectories` configuration

2. **File system events being detected?**
   ```
   [DEBUG] File system event detected in directory: ...
   ```
   → If not detected, check:
   - IDE auto-save settings
   - File system type (NFS, Docker volumes, etc.)
   - OS file watcher limits (inotify limits on Linux)

### Issue 2: File Changes Detected but Callback Not Invoked

**Symptoms**: `File system event detected` appears but `handleFileChanges called` does not

**Check**:
1. **File count after filtering**:
   ```
   [DEBUG] After filtering: 0 file(s)
   ```
   → If 0, check PathMatcher `includes`/`excludes` configuration

   **Example configuration**:
   ```xml
   <configuration>
       <includes>
           <include>**/*.java</include>
           <include>**/*.kt</include>
       </includes>
       <excludes>
           <exclude>**/target/**</exclude>
       </excludes>
   </configuration>
   ```

2. **Debounce period**:
   ```
   [DEBUG] Debounce period elapsed. Processing X changed file(s)
   ```
   → If not appearing, `debounceMs` setting might be too large

   **Adjust debounce** (default is 750ms):
   ```xml
   <configuration>
       <debounceMs>300</debounceMs>
   </configuration>
   ```

### Issue 3: Callback Invoked but Tests Not Executed

**Symptoms**: `handleFileChanges called` appears but `executeTests` is not called

**Check**:
1. **selectTests result**:
   ```
   [DEBUG] Selector.selectTests() called with X file(s)
   [INFO] No tests to run for these changes
   [DEBUG] Selector.selectTests() returned empty list
   ```
   → If empty list, corresponding test files don't exist

2. **File categorization**:
   ```
   [DEBUG] Categorized files - source: 0, test: 0
   ```
   → If both are 0, files are not under `sourceDirectory` or `testSourceDirectory`

   **Default directories**:
   - sourceDirectory: `src/main/java`
   - testSourceDirectory: `src/test/java`

3. **Test naming conventions**:
   - `Foo.java` → `FooTest.java`, `FooTests.java`, `FooIT.java`, `FooIntegrationTest.java`
   - Package structure is preserved (`com.example.Foo` → `com.example.FooTest`)

### Issue 4: executeTests Called but Tests Fail

**Symptoms**: Tests are executed but fail

**Check**:
1. **Maven command**:
   ```
   [DEBUG] Maven command: [mvn, --batch-mode, test, -Dtest=CalculatorTest]
   ```
   Or with Maven wrapper:
   ```
   [DEBUG] Maven command: [./mvnw, --batch-mode, test, -Dtest=CalculatorTest]
   ```

2. **Run with verbose mode** to see Surefire output:
   ```bash
   mvn watcher:watch -Dwatcher.verbose=true
   ```

3. **Check Surefire configuration**:
   - JUnit version
   - Tag filtering (`groups`, `excludedGroups`)
   - Parallel execution settings
   - Additional properties

4. **Verify plugin descriptor exists**:
   ```bash
   mvn compile  # Generate plugin descriptor first
   ls -la target/classes/META-INF/maven/plugin.xml
   ```

### Issue 5: Unwanted .surefire-* Files in Workspace

**Symptoms**: `.surefire-*` temporary files appear in project root or workspace directories

**Solution**:
The plugin suppresses these files by default. If you're seeing them:

1. **Check plugin version**:
   - Confirm you're using a release that includes the suppression defaults (for example, v0.9.0 or newer)
   - Older tags may require setting the properties manually

2. **Verify configuration**:
   ```xml
   <configuration>
       <!-- Default is false (suppresses .surefire-* files) -->
       <useFile>false</useFile>

       <!-- Default is "target/tmp" (redirects temporary files) -->
       <tempDir>target/tmp</tempDir>
   </configuration>
   ```

3. **Customize temporary directory** (optional):
   ```xml
   <configuration>
       <!-- Use custom directory for temporary files -->
       <tempDir>custom/temp</tempDir>
   </configuration>
   ```

4. **Check command line override**:
   ```bash
   # If you're using this, it will create .surefire-* files
   mvn watcher:watch -Dwatcher.useFile=true

   # Use default (suppresses .surefire-* files)
   mvn watcher:watch
   ```

**What the plugin does by default**:
- Sets `-Dsurefire.useFile=false` to suppress `.surefire-*` files
- Sets `-Dsurefire.tempDir=none` to disable Surefire's temp directory
- Sets `-Djava.io.tmpdir=<project.basedir>/target/tmp` to redirect temporary files
- Test reports still generated in `target/surefire-reports/`
- No impact on test execution or results
- Temporary files (if any) created in `target/tmp` and cleaned up with `mvn clean`

**Test Output Messages**:
- **Success**: No messages (silent success)
- **Failure**: Error messages with exit codes displayed:
  ```
  [ERROR] ==========================================
  [ERROR] Tests failed with exit code: 1
  [ERROR] ==========================================
  ```

**Verbose mode shows Maven command**:
```bash
mvn watcher:watch -Dwatcher.verbose=true
```

Example output:
```
[INFO] Maven command: mvn --batch-mode test -Dtest=FooTest -Dsurefire.useFile=false -Dsurefire.tempDir=none -Dsurefire.trimStackTrace=false -Dsurefire.redirectTestOutputToFile=false -Djava.io.tmpdir=/absolute/path/to/project/target/tmp
```

## Integration Tests

The plugin includes integration tests that verify the complete flow:

```bash
# Run integration tests
mvn test -Dtest=FileChangeIntegrationTest

# Run all tests
mvn test
```

**FileChangeIntegrationTest** verifies:
- `testFileChangeTriggersExecuteTests`: executeTests is called when source files change
- `testTestFileChangeTriggersExecuteTests`: executeTests is called when test files change
- `testFileChangeWithNoTestsDoesNotCallExecuteTests`: executeTests is not called when no tests are found
- `testPathMatcherFiltersNonMatchingFiles`: PathMatcher filters correctly

## Common Scenarios

### Scenario 1: Using Custom File Patterns

```xml
<configuration>
    <includes>
        <include>**/*.java</include>
        <include>**/*.kt</include>
    </includes>
    <excludes>
        <exclude>**/target/**</exclude>
        <exclude>**/*IT.java</exclude>
    </excludes>
</configuration>
```

**Check in debug logs**:
- Before filtering: `Processing 2 changed file(s)`
- After filtering: `After filtering: 1 file(s)`
- Exclusion patterns working as intended

### Scenario 2: Multi-Module Projects

Run watcher for each module individually:

```bash
# From root, specific module only
mvn watcher:watch -pl module-name

# From module directory
cd module-name
mvn watcher:watch
```

### Scenario 3: Plugin Descriptor Not Found

If tests fail with errors about missing plugin descriptor:

```bash
# Generate plugin descriptor first
mvn compile

# Then run tests
mvn test
```

The plugin descriptor is generated at `target/classes/META-INF/maven/plugin.xml` and is required by some tests.

### Scenario 4: Using in Docker

File watchers may not work correctly with Docker volumes.

**Solutions**:
1. Polling mode (planned for future version)
2. Run plugin on host machine
3. Edit files directly inside Docker (via docker exec)

## Best Practices for Debugging

1. **Check default output first**: The plugin always shows changed files and selected tests
   - Look for `[INFO] Changed files:` and `[INFO] Selected tests:` in the output
   - If these don't appear, the issue is likely in file detection

2. **Step-by-step verification**: Check logs from top to bottom
   - FileWatcher → WatcherMojo → Selector

3. **Verbose mode**: Enable for detailed test resolution logs
   ```bash
   mvn watcher:watch -Dwatcher.verbose=true
   ```

4. **Full debug**: Use `-X` only when more detail is needed
   ```bash
   mvn watcher:watch -X
   ```

5. **Integration tests**: Run integration tests locally to verify basic operation
   ```bash
   mvn test -Dtest=FileChangeIntegrationTest
   ```

## Log File Location

Maven logs go to console, but you can save them to a file:

```bash
# Save logs to file
mvn watcher:watch -X > debug.log 2>&1

# View in real-time while saving
mvn watcher:watch -X 2>&1 | tee debug.log
```

## Related Files

Source code locations to reference when debugging:

- **FileWatcher.java**: File change detection and debounce processing
- **WatcherMojo.java**: Callback processing and error handling
- **Selector.java**: Test selection and execution logic
- **PathMatcher.java**: File matching logic
- **TestNameResolver.java**: Test name inference logic

## Support

If issues persist:
1. Report on GitHub Issues: https://github.com/yasuflatland-lf/watcher-maven-plugin/issues
2. Include debug logs in report (remove personal information)
3. Specify plugin version, JDK version, and OS
