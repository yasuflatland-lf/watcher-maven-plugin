package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;

/**
 * Integration tests for the complete file change detection flow.
 * Verifies that file changes trigger the full pipeline:
 * FileWatcher -> PathMatcher -> Selector.selectTests() -> Selector.executeTests()
 */
class FileChangeIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private Log mockLog;

    private FileWatcher fileWatcher;
    private MavenProject project;
    private WatcherConfig config;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        // Create project structure
        Path srcDir = tempDir.resolve("src/main/java");
        Path testDir = tempDir.resolve("src/test/java");
        Files.createDirectories(srcDir);
        Files.createDirectories(testDir);

        // Initialize configuration
        config = new WatcherConfig();
        config.setSourceDirectory(srcDir.toFile());
        config.setTestSourceDirectory(testDir.toFile());
        config.setVerbose(false);

        // Create actual MavenProject instance
        Model model = new Model();
        model.setGroupId("com.test");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");
        project = new MavenProject(model);
        project.setFile(tempDir.resolve("pom.xml").toFile());
    }

    @AfterEach
    void tearDown() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
    }

    /**
     * Tests that when a source file changes, the full flow executes:
     * 1. FileWatcher detects the change
     * 2. PathMatcher filters the file
     * 3. Selector.selectTests() is called to find related tests
     * 4. Selector.executeTests() is called with the test list
     *
     * This test uses a spy on Selector to verify executeTests() is called
     * without actually running Maven.
     */
    @Test
    void testFileChangeTriggersExecuteTests() throws Exception {
        // Given: A source file and its corresponding test file
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path sourceFile = srcDir.resolve("Calculator.java");
        Path testFile = testDir.resolve("CalculatorTest.java");
        Files.createFile(testFile);

        // Create a spy on Selector to verify executeTests is called
        Selector realSelector = new Selector(project, config, mockLog);
        Selector spySelector = spy(realSelector);

        // Mock executeTests to avoid actually running Maven
        lenient().when(spySelector.executeTests(anyList())).thenReturn(true);

        // Create FileWatcher with PathMatcher
        PathMatcher pathMatcher = PathMatcher.forTargetCode();
        fileWatcher = new FileWatcher(srcDir, 100, false, pathMatcher, mockLog);

        // Latch to wait for callback
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> capturedTests = new AtomicReference<>();

        // Start watching in a separate thread
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(changedFiles -> {
                    // Simulate the handleFileChanges logic from WatcherMojo
                    List<String> testClasses = spySelector.selectTests(changedFiles);
                    capturedTests.set(testClasses);

                    if (!testClasses.isEmpty()) {
                        try {
                            spySelector.executeTests(testClasses);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create and modify the source file
        Thread.sleep(200); // Wait for watcher to initialize
        Files.createFile(sourceFile);
        Files.write(sourceFile, "public class Calculator {}".getBytes());

        // Then: Verify the full flow executed
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "File change should be detected");

        // Verify selectTests was called and returned the correct test
        List<String> tests = capturedTests.get();
        assertNotNull(tests, "selectTests should have been called");
        assertFalse(tests.isEmpty(), "selectTests should have found tests");
        assertTrue(tests.contains("CalculatorTest"), "Should find CalculatorTest");

        // Verify executeTests was called with the test list
        verify(spySelector, times(1)).executeTests(argThat(list ->
            list != null && !list.isEmpty() && list.contains("CalculatorTest")
        ));

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that when a test file itself changes, executeTests is called
     * directly with that test class.
     */
    @Test
    void testTestFileChangeTriggersExecuteTests() throws Exception {
        // Given: A test file
        Path testDir = config.getTestSourceDirectory().toPath();
        Path testFile = testDir.resolve("SampleTest.java");

        // Create a spy on Selector
        Selector realSelector = new Selector(project, config, mockLog);
        Selector spySelector = spy(realSelector);
        lenient().when(spySelector.executeTests(anyList())).thenReturn(true);

        // Create FileWatcher
        PathMatcher pathMatcher = PathMatcher.forTargetCode();
        fileWatcher = new FileWatcher(testDir, 100, false, pathMatcher, mockLog);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> capturedTests = new AtomicReference<>();

        // Start watching
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(changedFiles -> {
                    List<String> testClasses = spySelector.selectTests(changedFiles);
                    capturedTests.set(testClasses);

                    if (!testClasses.isEmpty()) {
                        try {
                            spySelector.executeTests(testClasses);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create and modify the test file
        Thread.sleep(200);
        Files.createFile(testFile);
        Files.write(testFile, "public class SampleTest {}".getBytes());

        // Then: Verify executeTests was called with the test class
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "Test file change should be detected");

        List<String> tests = capturedTests.get();
        assertNotNull(tests);
        assertFalse(tests.isEmpty());
        assertTrue(tests.contains("SampleTest"));

        verify(spySelector, times(1)).executeTests(argThat(list ->
            list != null && !list.isEmpty() && list.contains("SampleTest")
        ));

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that when a source file changes but no corresponding test exists,
     * executeTests is NOT called.
     */
    @Test
    void testFileChangeWithNoTestsDoesNotCallExecuteTests() throws Exception {
        // Given: A source file with NO corresponding test
        Path srcDir = config.getSourceDirectory().toPath();
        Path sourceFile = srcDir.resolve("NoTest.java");

        // Create a spy on Selector
        Selector realSelector = new Selector(project, config, mockLog);
        Selector spySelector = spy(realSelector);
        lenient().when(spySelector.executeTests(anyList())).thenReturn(true);

        // Create FileWatcher
        PathMatcher pathMatcher = PathMatcher.forTargetCode();
        fileWatcher = new FileWatcher(srcDir, 100, false, pathMatcher, mockLog);

        CountDownLatch latch = new CountDownLatch(1);

        // Start watching
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(changedFiles -> {
                    List<String> testClasses = spySelector.selectTests(changedFiles);

                    if (!testClasses.isEmpty()) {
                        try {
                            spySelector.executeTests(testClasses);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create source file without test
        Thread.sleep(200);
        Files.createFile(sourceFile);
        Files.write(sourceFile, "public class NoTest {}".getBytes());

        // Then: executeTests should NOT be called
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "File change should be detected");

        // Verify executeTests was NOT called (because no tests were found)
        verify(spySelector, never()).executeTests(any());

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher properly uses PathMatcher to filter files.
     * Only files matching the PathMatcher pattern should trigger the callback.
     */
    @Test
    void testPathMatcherFiltersNonMatchingFiles() throws Exception {
        // Given: A directory with PathMatcher that only accepts .java files
        Path srcDir = config.getSourceDirectory().toPath();

        Selector realSelector = new Selector(project, config, mockLog);
        Selector spySelector = spy(realSelector);
        doReturn(true).when(spySelector).executeTests(anyList());

        PathMatcher pathMatcher = PathMatcher.forTargetCode(); // Only .java and .kt files
        fileWatcher = new FileWatcher(srcDir, 100, false, pathMatcher, mockLog);

        CountDownLatch latch = new CountDownLatch(1);

        // Start watching
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(changedFiles -> {
                    List<String> testClasses = spySelector.selectTests(changedFiles);
                    if (!testClasses.isEmpty()) {
                        try {
                            spySelector.executeTests(testClasses);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create both .java and .txt files
        Thread.sleep(200);
        Path javaFile = srcDir.resolve("Match.java");
        Path txtFile = srcDir.resolve("NoMatch.txt");

        Files.createFile(txtFile); // Create .txt first
        Thread.sleep(50);
        Files.createFile(javaFile); // Then .java
        Files.write(javaFile, "public class Match {}".getBytes());

        // Then: Only .java file should trigger the callback
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        // The latch will count down even if no tests are found, but executeTests should only
        // be called if .java files are processed

        fileWatcher.stop();
        watchThread.join(1000);

        // Verify that selectTests was called (meaning .java file passed the filter)
        verify(spySelector, atLeastOnce()).selectTests(any());
    }
}
