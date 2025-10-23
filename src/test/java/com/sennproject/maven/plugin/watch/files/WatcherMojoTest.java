package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WatcherMojo configuration and methods
 * Following TDD principles with comprehensive coverage
 * <p>
 * Note: Since Maven plugin annotations use SOURCE retention, we cannot access them
 * via reflection at runtime. Instead, we read the generated plugin descriptor XML.
 */
class WatcherMojoTest {

    @TempDir
    Path tempDir;

    private WatcherMojo mojo;
    private MavenProject project;

    @BeforeEach
    void setUp() throws Exception {
        mojo = new WatcherMojo();

        // Create a real MavenProject instance
        Model model = new Model();
        model.setGroupId("com.test");
        model.setArtifactId("test-artifact");
        model.setVersion("1.0.0-TEST");
        model.setPackaging("jar");

        Build build = new Build();
        build.setDirectory(tempDir.resolve("target").toString());
        build.setOutputDirectory(tempDir.resolve("target/classes").toString());
        build.setTestOutputDirectory(tempDir.resolve("target/test-classes").toString());
        model.setBuild(build);

        project = new MavenProject(model);

        // Note: plugin field is left as null - printBanner() handles this gracefully

        // Use reflection to set private fields
        setField(mojo, "project", project);
        setField(mojo, "debounceMs", 750L);
        setField(mojo, "watchMode", true);
        setField(mojo, "recursive", true);
        setField(mojo, "rerunFailingTestsCount", 0);
        setField(mojo, "runOrder", "failedfirst");
        setField(mojo, "parallel", false);
        setField(mojo, "threadCount", 1);
        setField(mojo, "skipAfterFailureCount", 0);
        setField(mojo, "verbose", false);
        setField(mojo, "includes", new ArrayList<String>());
        setField(mojo, "excludes", new ArrayList<String>());
        setField(mojo, "additionalProperties", new HashMap<String, String>());
        setField(mojo, "watchDirectories", new ArrayList<File>());
    }

    @AfterEach
    void tearDown() {
        // Clean up
    }

    /**
     * Helper method to set private fields using reflection
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Helper method to invoke private methods and unwrap InvocationTargetException
     */
    private Object invokePrivateMethod(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }

        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    /**
     * Helper method to assert that a private method throws a specific exception type
     */
    private <T extends Throwable> T assertPrivateMethodThrows(
            Class<T> expectedType,
            Object target,
            String methodName,
            Object... args) {

        T exception = assertThrows(expectedType, () -> {
            invokePrivateMethod(target, methodName, args);
        });

        return exception;
    }

    /**
     * Functional interface for throwable operations
     */
    @FunctionalInterface
    private interface ThrowingCallable {
        void call() throws Exception;
    }


    // ========== Configuration Descriptor Tests ==========

    /**
     * Verify that WatcherMojo does not have a defaultPhase set,
     * ensuring it only runs when explicitly invoked via 'mvn watcher:watch'
     * and not during standard lifecycle phases like 'mvn test'.
     */
    @Test
    void shouldNotHaveDefaultPhase() throws Exception {
        // Read the plugin descriptor from the generated META-INF/maven/plugin.xml
        Path pluginDescriptorPath = Paths.get("target/classes/META-INF/maven/plugin.xml");

        // Plugin descriptor must exist - fail test if not found
        assertTrue(Files.exists(pluginDescriptorPath),
                "Plugin descriptor must exist at " + pluginDescriptorPath +
                        ". Run 'mvn compile' first to generate the descriptor.");

        String content = Files.readString(pluginDescriptorPath);
        assertNotNull(content, "Plugin descriptor content should not be null");
        assertFalse(content.isEmpty(), "Plugin descriptor content should not be empty");

        // Find the 'watch' mojo section
        Pattern mojoPattern = Pattern.compile(
                "<mojo>.*?<goal>watch</goal>.*?</mojo>",
                Pattern.DOTALL
        );
        Matcher mojoMatcher = mojoPattern.matcher(content);

        assertTrue(mojoMatcher.find(), "Should find 'watch' mojo in plugin descriptor");

        String watchMojoSection = mojoMatcher.group();

        // Check if the <phase> element exists and has a value
        Pattern phasePattern = Pattern.compile("<phase>([^<]*)</phase>");
        Matcher phaseMatcher = phasePattern.matcher(watchMojoSection);

        if (phaseMatcher.find()) {
            String phaseValue = phaseMatcher.group(1).trim();
            assertTrue(phaseValue.isEmpty(),
                    "WatcherMojo should not have a defaultPhase set. " +
                            "It should only run when explicitly invoked via 'mvn watcher:watch', " +
                            "not during standard Maven lifecycle phases like 'mvn test' or 'mvn spring-boot:run'. " +
                            "Found phase: " + phaseValue);
        }
        // If no <phase> element is found, that's what we want - the test passes
    }

    /**
     * Verify that WatcherMojo can be instantiated
     */
    @Test
    void shouldBeInstantiable() {
        assertDoesNotThrow(() -> new WatcherMojo(),
                "WatcherMojo should be instantiable");
    }

    // ========== Validation Tests ==========

    /**
     * TDD: Test that validation fails when source directory does not exist
     */
    @Test
    void shouldFailValidationWhenSourceDirectoryDoesNotExist() throws Exception {
        // Given: source directory does not exist
        File nonExistentDir = new File(tempDir.toFile(), "non-existent-source");
        File existingDir = tempDir.toFile();

        setField(mojo, "sourceDirectory", nonExistentDir);
        setField(mojo, "testSourceDirectory", existingDir);

        // When/Then: validation should throw exception
        MojoExecutionException exception = assertPrivateMethodThrows(
                MojoExecutionException.class,
                mojo,
                "validateConfiguration"
        );

        assertTrue(exception.getMessage().contains("Source directory does not exist"));
    }

    /**
     * TDD: Test that validation fails when test directory does not exist
     */
    @Test
    void shouldFailValidationWhenTestDirectoryDoesNotExist() throws Exception {
        // Given: test directory does not exist
        File existingDir = tempDir.toFile();
        File nonExistentDir = new File(tempDir.toFile(), "non-existent-test");

        setField(mojo, "sourceDirectory", existingDir);
        setField(mojo, "testSourceDirectory", nonExistentDir);

        // When/Then: validation should throw exception
        MojoExecutionException exception = assertPrivateMethodThrows(
                MojoExecutionException.class,
                mojo,
                "validateConfiguration"
        );

        assertTrue(exception.getMessage().contains("Test source directory does not exist"));
    }

    /**
     * TDD: Test that validation fails when debounceMs is negative
     */
    @Test
    void shouldFailValidationWhenDebounceMsIsNegative() throws Exception {
        // Given: debounceMs is negative
        setField(mojo, "sourceDirectory", tempDir.toFile());
        setField(mojo, "testSourceDirectory", tempDir.toFile());
        setField(mojo, "debounceMs", -100L);

        // When/Then: validation should throw exception
        MojoExecutionException exception = assertPrivateMethodThrows(
                MojoExecutionException.class,
                mojo,
                "validateConfiguration"
        );

        assertTrue(exception.getMessage().contains("debounceMs must be >= 0"));
    }

    /**
     * TDD: Test that validation succeeds when all parameters are valid
     */
    @Test
    void shouldPassValidationWhenAllParametersAreValid() throws Exception {
        // Given: all directories exist and parameters are valid
        setField(mojo, "sourceDirectory", tempDir.toFile());
        setField(mojo, "testSourceDirectory", tempDir.toFile());
        setField(mojo, "debounceMs", 750L);

        // When/Then: validation should not throw exception
        assertDoesNotThrow(() -> {
            java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("validateConfiguration");
            method.setAccessible(true);
            method.invoke(mojo);
        });
    }

    // ========== Configuration Creation Tests ==========

    /**
     * TDD: Test that createConfig() creates proper WatcherConfig object
     */
    @Test
    void shouldCreateConfigWithAllParameters() throws Exception {
        // Given: all parameters are set
        File sourceDir = tempDir.toFile();
        File testDir = tempDir.toFile();
        List<String> includes = List.of("**/*.java");
        List<String> excludes = List.of("**/target/**");

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "includes", includes);
        setField(mojo, "excludes", excludes);
        setField(mojo, "debounceMs", 1000L);
        setField(mojo, "watchMode", false);

        // When: createConfig is called
        java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("createConfig");
        method.setAccessible(true);
        WatcherConfig config = (WatcherConfig) method.invoke(mojo);

        // Then: config should have all values set
        assertNotNull(config);
        assertEquals(sourceDir, config.getSourceDirectory());
        assertEquals(testDir, config.getTestSourceDirectory());
        assertEquals(includes, config.getIncludes());
        assertEquals(excludes, config.getExcludes());
        assertEquals(1000L, config.getDebounceMs());
        assertFalse(config.isWatchMode());
    }

    // ========== PathMatcher Creation Tests ==========

    /**
     * TDD: Test that createPathMatcher returns default matcher when no includes/excludes
     */
    @Test
    void shouldCreateDefaultPathMatcherWhenNoIncludesOrExcludes() throws Exception {
        // Given: no includes or excludes
        setField(mojo, "includes", new ArrayList<String>());
        setField(mojo, "excludes", new ArrayList<String>());

        // When: createPathMatcher is called
        java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("createPathMatcher");
        method.setAccessible(true);
        PathMatcher matcher = (PathMatcher) method.invoke(mojo);

        // Then: should return default matcher (matches .java and .kt files)
        assertNotNull(matcher);
        // PathMatcher uses full paths, so we need to create realistic paths
        assertTrue(matcher.matches(tempDir.resolve("src/main/java/Test.java")));
        assertTrue(matcher.matches(tempDir.resolve("src/main/kotlin/Test.kt")));
        assertFalse(matcher.matches(tempDir.resolve("src/main/resources/Test.txt")));
    }

    /**
     * TDD: Test that createPathMatcher uses custom includes/excludes
     */
    @Test
    void shouldCreateCustomPathMatcherWhenIncludesOrExcludesProvided() throws Exception {
        // Given: custom includes and excludes
        List<String> includes = List.of("**/*.java");
        List<String> excludes = List.of("**/Test*.java");
        setField(mojo, "includes", includes);
        setField(mojo, "excludes", excludes);

        // When: createPathMatcher is called
        java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("createPathMatcher");
        method.setAccessible(true);
        PathMatcher matcher = (PathMatcher) method.invoke(mojo);

        // Then: should use custom patterns
        assertNotNull(matcher);
        assertTrue(matcher.matches(tempDir.resolve("src/main/java/Foo.java")));
        assertFalse(matcher.matches(tempDir.resolve("src/main/java/TestFoo.java"))); // excluded
    }

    // ========== Watch Paths Collection Tests ==========

    /**
     * TDD: Test that collectWatchPaths includes source and test directories
     */
    @Test
    void shouldCollectSourceAndTestDirectories() throws Exception {
        // Given: source and test directories exist
        File sourceDir = tempDir.resolve("src").toFile();
        File testDir = tempDir.resolve("test").toFile();
        sourceDir.mkdir();
        testDir.mkdir();

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "watchDirectories", new ArrayList<File>());

        // When: collectWatchPaths is called
        java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("collectWatchPaths");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Path> paths = (List<Path>) method.invoke(mojo);

        // Then: should contain both directories
        assertNotNull(paths);
        assertEquals(2, paths.size());
        assertTrue(paths.contains(sourceDir.toPath()));
        assertTrue(paths.contains(testDir.toPath()));
    }

    /**
     * TDD: Test that collectWatchPaths includes additional watch directories
     */
    @Test
    void shouldCollectAdditionalWatchDirectories() throws Exception {
        // Given: additional watch directories exist
        File sourceDir = tempDir.resolve("src").toFile();
        File testDir = tempDir.resolve("test").toFile();
        File additionalDir = tempDir.resolve("resources").toFile();
        sourceDir.mkdir();
        testDir.mkdir();
        additionalDir.mkdir();

        List<File> additionalDirs = new ArrayList<>();
        additionalDirs.add(additionalDir);

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "watchDirectories", additionalDirs);

        // When: collectWatchPaths is called
        java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("collectWatchPaths");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Path> paths = (List<Path>) method.invoke(mojo);

        // Then: should contain all directories
        assertNotNull(paths);
        assertEquals(3, paths.size());
        assertTrue(paths.contains(sourceDir.toPath()));
        assertTrue(paths.contains(testDir.toPath()));
        assertTrue(paths.contains(additionalDir.toPath()));
    }

    /**
     * TDD: Test that collectWatchPaths skips non-existent directories
     */
    @Test
    void shouldSkipNonExistentAdditionalDirectories() throws Exception {
        // Given: one additional directory doesn't exist
        File sourceDir = tempDir.resolve("src").toFile();
        File testDir = tempDir.resolve("test").toFile();
        File nonExistentDir = new File(tempDir.toFile(), "non-existent");
        sourceDir.mkdir();
        testDir.mkdir();

        List<File> additionalDirs = new ArrayList<>();
        additionalDirs.add(nonExistentDir);

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "watchDirectories", additionalDirs);

        // When: collectWatchPaths is called
        java.lang.reflect.Method method = WatcherMojo.class.getDeclaredMethod("collectWatchPaths");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Path> paths = (List<Path>) method.invoke(mojo);

        // Then: should only contain existing directories
        assertNotNull(paths);
        assertEquals(2, paths.size());
        assertTrue(paths.contains(sourceDir.toPath()));
        assertTrue(paths.contains(testDir.toPath()));
        assertFalse(paths.contains(nonExistentDir.toPath()));
    }

    // ========== handleFileChanges Tests ==========

    /**
     * Test handleFileChanges with normal file changes
     */
    @Test
    void handleFileChanges_shouldLogAndProcessNormalFileChanges() throws Exception {
        // Given: Valid directories and some changed files
        File sourceDir = tempDir.resolve("src/main/java").toFile();
        File testDir = tempDir.resolve("src/test/java").toFile();
        sourceDir.mkdirs();
        testDir.mkdirs();

        // Create test files
        Path testFile = testDir.toPath().resolve("ExampleTest.java");
        Files.createFile(testFile);

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "watchMode", true);

        // Create WatcherConfig and Selector
        WatcherConfig config = new WatcherConfig();
        config.setSourceDirectory(sourceDir);
        config.setTestSourceDirectory(testDir);
        config.setVerbose(false);

        Selector selector = new Selector(project, config, mojo.getLog());

        List<Path> changedFiles = List.of(testFile);

        // When: handleFileChanges is called
        // Note: This will try to actually run Maven tests, but will fail gracefully
        // The test verifies the method can be invoked without throwing exceptions in watchMode
        Method method = WatcherMojo.class.getDeclaredMethod("handleFileChanges", List.class, Selector.class);
        method.setAccessible(true);

        // Then: Should not throw exception in watchMode even if tests fail
        assertDoesNotThrow(() -> {
            try {
                method.invoke(mojo, changedFiles, selector);
            } catch (InvocationTargetException e) {
                // If it's a MojoExecutionException in watchMode, that shouldn't happen
                if (e.getCause() instanceof MojoExecutionException) {
                    throw (MojoExecutionException) e.getCause();
                }
            }
        });
    }

    /**
     * Test handleFileChanges with empty changed files list
     */
    @Test
    void handleFileChanges_shouldHandleEmptyChangedFiles() throws Exception {
        // Given: Empty changed files list
        File sourceDir = tempDir.resolve("src/main/java").toFile();
        File testDir = tempDir.resolve("src/test/java").toFile();
        sourceDir.mkdirs();
        testDir.mkdirs();

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);

        WatcherConfig config = new WatcherConfig();
        config.setSourceDirectory(sourceDir);
        config.setTestSourceDirectory(testDir);

        Selector selector = new Selector(project, config, mojo.getLog());
        List<Path> changedFiles = Collections.emptyList();

        // When: handleFileChanges is called
        Method method = WatcherMojo.class.getDeclaredMethod("handleFileChanges", List.class, Selector.class);
        method.setAccessible(true);

        // Then: Should handle gracefully (no tests to run)
        assertDoesNotThrow(() -> {
            try {
                method.invoke(mojo, changedFiles, selector);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                }
            }
        });
    }

    /**
     * Test handleFileChanges throws exception when tests fail in one-shot mode
     */
    @Test
    void handleFileChanges_shouldThrowExceptionWhenTestsFailInOneShotMode() throws Exception {
        // Given: watchMode = false (one-shot mode)
        File sourceDir = tempDir.resolve("src/main/java").toFile();
        File testDir = tempDir.resolve("src/test/java").toFile();
        sourceDir.mkdirs();
        testDir.mkdirs();

        // Create a test file that will be selected
        Path testFile = testDir.toPath().resolve("FailingTest.java");
        Files.createFile(testFile);

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "watchMode", false); // one-shot mode

        WatcherConfig config = new WatcherConfig();
        config.setSourceDirectory(sourceDir);
        config.setTestSourceDirectory(testDir);
        config.setVerbose(false);

        Selector selector = new Selector(project, config, mojo.getLog());
        List<Path> changedFiles = List.of(testFile);

        // When/Then: Should throw MojoExecutionException when tests fail in one-shot mode
        Method method = WatcherMojo.class.getDeclaredMethod("handleFileChanges", List.class, Selector.class);
        method.setAccessible(true);

        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> {
            try {
                method.invoke(mojo, changedFiles, selector);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof MojoExecutionException) {
                    throw (MojoExecutionException) e.getCause();
                }
                throw e;
            }
        });

        assertEquals("Tests failed", exception.getMessage());
    }

    /**
     * Test handleFileChanges continues in watchMode even when tests fail
     */
    @Test
    void handleFileChanges_shouldContinueWhenTestsFailInWatchMode() throws Exception {
        // Given: watchMode = true
        File sourceDir = tempDir.resolve("src/main/java").toFile();
        File testDir = tempDir.resolve("src/test/java").toFile();
        sourceDir.mkdirs();
        testDir.mkdirs();

        Path testFile = testDir.toPath().resolve("FailingTest.java");
        Files.createFile(testFile);

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);
        setField(mojo, "watchMode", true); // watch mode

        WatcherConfig config = new WatcherConfig();
        config.setSourceDirectory(sourceDir);
        config.setTestSourceDirectory(testDir);

        Selector selector = new Selector(project, config, mojo.getLog());
        List<Path> changedFiles = List.of(testFile);

        // When: handleFileChanges is called in watchMode
        Method method = WatcherMojo.class.getDeclaredMethod("handleFileChanges", List.class, Selector.class);
        method.setAccessible(true);

        // Then: Should NOT throw exception even if tests fail (watchMode continues)
        assertDoesNotThrow(() -> {
            try {
                method.invoke(mojo, changedFiles, selector);
            } catch (InvocationTargetException e) {
                // Should not throw MojoExecutionException in watchMode
                if (e.getCause() instanceof MojoExecutionException) {
                    fail("Should not throw MojoExecutionException in watchMode");
                }
            }
        });
    }

    /**
     * Test handleFileChanges with no matching tests
     */
    @Test
    void handleFileChanges_shouldHandleNoMatchingTests() throws Exception {
        // Given: Source file with no corresponding test
        File sourceDir = tempDir.resolve("src/main/java").toFile();
        File testDir = tempDir.resolve("src/test/java").toFile();
        sourceDir.mkdirs();
        testDir.mkdirs();

        Path sourceFile = sourceDir.toPath().resolve("NoTest.java");
        Files.createFile(sourceFile);

        setField(mojo, "sourceDirectory", sourceDir);
        setField(mojo, "testSourceDirectory", testDir);

        WatcherConfig config = new WatcherConfig();
        config.setSourceDirectory(sourceDir);
        config.setTestSourceDirectory(testDir);

        Selector selector = new Selector(project, config, mojo.getLog());
        List<Path> changedFiles = List.of(sourceFile);

        // When: handleFileChanges is called
        Method method = WatcherMojo.class.getDeclaredMethod("handleFileChanges", List.class, Selector.class);
        method.setAccessible(true);

        // Then: Should handle gracefully (logs "No tests to run")
        assertDoesNotThrow(() -> {
            try {
                method.invoke(mojo, changedFiles, selector);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                }
            }
        });
    }

    // ========================================
    // execute() tests using TestFileWatcherFactory
    // ========================================

    @Test
    void shouldExecuteWithTestFileWatcherFactory() throws Exception {
        // Given: Setup directories
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());
        setField(mojo, "watchMode", false); // one-shot mode

        // Create a test file to trigger
        Path testFile = testDir.resolve("ExampleTest.java");
        Files.writeString(testFile, "public class ExampleTest {}");

        // Use TestFileWatcherFactory with empty trigger list
        mojo.setFileWatcherFactory(new TestFileWatcherFactory(List.of(), null));

        // When: execute() is called
        // Then: Should complete without blocking
        assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    void shouldExecuteAndTriggerFileChange() throws Exception {
        // Given: Setup directories
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());
        setField(mojo, "watchMode", false);

        // Create test file
        Path testFile = testDir.resolve("ExampleTest.java");
        Files.writeString(testFile, "public class ExampleTest {}");

        // Capture callback execution
        List<List<Path>> capturedChanges = new ArrayList<>();

        // Use TestFileWatcherFactory that triggers with testFile
        mojo.setFileWatcherFactory(new TestFileWatcherFactory(
            List.of(testFile),
            capturedChanges::add
        ));

        // When: execute() is called
        assertDoesNotThrow(() -> mojo.execute());

        // Then: Callback should have been triggered with the test file
        assertEquals(1, capturedChanges.size());
        assertEquals(List.of(testFile), capturedChanges.get(0));
    }

    @Test
    void shouldExecuteWithWatchMode() throws Exception {
        // Given: Setup directories
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());
        setField(mojo, "watchMode", true); // continuous watch mode

        // Use TestFileWatcherFactory with empty trigger list
        mojo.setFileWatcherFactory(new TestFileWatcherFactory(List.of(), null));

        // When: execute() is called
        // Then: Should complete without blocking (TestFileWatcherFactory prevents infinite loop)
        assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    void shouldExecuteWithEmptyDirectories() throws Exception {
        // Given: Empty directories (no Java files)
        Path srcDir = tempDir.resolve("empty-src");
        Path testDir = tempDir.resolve("empty-test");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());
        setField(mojo, "watchMode", false);

        // Use TestFileWatcherFactory
        mojo.setFileWatcherFactory(new TestFileWatcherFactory(List.of(), null));

        // When/Then: Should handle gracefully (no files to trigger)
        assertDoesNotThrow(() -> mojo.execute());
    }

    // ========================================
    // createFileChangeHandler() tests
    // ========================================

    @Test
    void shouldCreateFileChangeHandler() throws Exception {
        // Given: Setup directories
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());

        // Create test file
        Path testFile = testDir.resolve("ExampleTest.java");
        Files.writeString(testFile, "public class ExampleTest {}");

        WatcherConfig config = invokeMethod(mojo, "createConfig");
        Selector selector = new Selector(project, config, mojo.getLog());

        // When: Create handler
        Consumer<List<Path>> handler = mojo.createFileChangeHandler(selector);

        // Then: Handler should not be null
        assertNotNull(handler);

        // And: Should be able to call it without exception
        assertDoesNotThrow(() -> handler.accept(List.of(testFile)));
    }

    @Test
    void shouldHandleFileChangesViaHandler() throws Exception {
        // Given: Setup directories with actual test
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());

        // Create a real test file
        Path testFile = testDir.resolve("SampleTest.java");
        Files.writeString(testFile, "public class SampleTest {}");

        WatcherConfig config = invokeMethod(mojo, "createConfig");
        Selector selector = new Selector(project, config, mojo.getLog());

        // When: Create and invoke handler
        Consumer<List<Path>> handler = mojo.createFileChangeHandler(selector);

        // Then: Should handle the file changes
        assertDoesNotThrow(() -> handler.accept(List.of(testFile)));
    }

    @Test
    void shouldHandleEmptyFileList() throws Exception {
        // Given: Setup
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());

        WatcherConfig config = invokeMethod(mojo, "createConfig");
        Selector selector = new Selector(project, config, mojo.getLog());

        // When: Create handler and pass empty list
        Consumer<List<Path>> handler = mojo.createFileChangeHandler(selector);

        // Then: Should handle empty list gracefully
        assertDoesNotThrow(() -> handler.accept(List.of()));
    }

    @Test
    void shouldHandleNonExistentFile() throws Exception {
        // Given: Setup
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        setField(mojo, "sourceDirectory", srcDir.toFile());
        setField(mojo, "testSourceDirectory", testDir.toFile());

        WatcherConfig config = invokeMethod(mojo, "createConfig");
        Selector selector = new Selector(project, config, mojo.getLog());

        // When: Create handler and pass non-existent file
        Consumer<List<Path>> handler = mojo.createFileChangeHandler(selector);
        Path nonExistentFile = testDir.resolve("DoesNotExist.java");

        // Then: Should handle gracefully (just logs, no exception)
        assertDoesNotThrow(() -> handler.accept(List.of(nonExistentFile)));
    }

    // ========================================
    // Helper method for invoking private methods
    // ========================================

    /**
     * Helper method to invoke private methods using reflection
     */
    @SuppressWarnings("unchecked")
    private <T> T invokeMethod(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return (T) method.invoke(target, args);
    }
}
