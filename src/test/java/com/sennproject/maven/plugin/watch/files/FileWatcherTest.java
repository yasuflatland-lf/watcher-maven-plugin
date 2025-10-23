package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileWatcher
 * Following TDD principles with boundary value tests
 */
class FileWatcherTest {

    @TempDir
    Path tempDir;

    private FileWatcher fileWatcher;
    private List<Path> changedPaths;
    private Log mockLog;

    @BeforeEach
    void setUp() {
        changedPaths = new ArrayList<>();
        mockLog = Mockito.mock(Log.class);
    }

    @AfterEach
    void tearDown() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
    }

    /**
     * Tests that FileWatcher can be constructed with a single directory path.
     * Verifies basic constructor functionality for the most common use case.
     */
    @Test
    void testConstructorWithSinglePath() throws IOException {
        // Given: Single path
        Path watchPath = tempDir;

        // When: Create FileWatcher
        fileWatcher = new FileWatcher(watchPath, 100, false, null, mockLog);

        // Then: FileWatcher is created successfully
        assertNotNull(fileWatcher);
    }

    /**
     * Tests that FileWatcher can be constructed with multiple directory paths.
     * Verifies constructor handles multiple watch targets correctly.
     */
    @Test
    void testConstructorWithMultiplePaths() throws IOException {
        // Given: Multiple paths
        Path path1 = tempDir.resolve("dir1");
        Path path2 = tempDir.resolve("dir2");
        Files.createDirectories(path1);
        Files.createDirectories(path2);
        List<Path> paths = Arrays.asList(path1, path2);

        // When: Create FileWatcher
        fileWatcher = new FileWatcher(paths, 100, false, null, mockLog);

        // Then: FileWatcher is created successfully
        assertNotNull(fileWatcher);
    }

    /**
     * Tests that FileWatcher detects when new files are created in the watched
     * directory.
     * Verifies the core file watching functionality works correctly.
     */
    @Test
    void testWatchDetectsNewFileCreation() throws IOException, InterruptedException {
        // Given: FileWatcher monitoring a directory
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);
        CountDownLatch latch = new CountDownLatch(1);

        // Start watching in a separate thread
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create a new file
        Thread.sleep(200); // Wait for watcher to initialize
        Path newFile = watchDir.resolve("NewFile.java");
        Files.createFile(newFile);
        Files.write(newFile, "public class NewFile {}".getBytes());

        // Then: Change is detected
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "File change should be detected");
        assertFalse(changedPaths.isEmpty());
        assertTrue(changedPaths.stream().anyMatch(p -> p.getFileName().toString().equals("NewFile.java")));

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher detects when existing files are modified.
     * Verifies that file content changes trigger the watch callback.
     */
    @Test
    void testWatchDetectsFileModification() throws IOException, InterruptedException {
        // Given: FileWatcher and an existing file
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);
        Path existingFile = watchDir.resolve("Existing.java");
        Files.createFile(existingFile);
        Files.write(existingFile, "initial content".getBytes());

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);
        CountDownLatch latch = new CountDownLatch(1);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Modify the file
        Thread.sleep(200);
        Files.write(existingFile, "modified content".getBytes());

        // Then: Change is detected
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "File modification should be detected");
        assertFalse(changedPaths.isEmpty());

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher with recursive mode enabled detects changes in
     * subdirectories.
     * Verifies that recursive watching works correctly for nested directory
     * structures.
     */
    @Test
    void testWatchWithRecursiveMode() throws IOException, InterruptedException {
        // Given: FileWatcher with recursive mode enabled
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 100, true, null, mockLog);
        CountDownLatch latch = new CountDownLatch(1);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create a file in a subdirectory
        Thread.sleep(200);
        Path subDir = watchDir.resolve("subdir");
        Files.createDirectories(subDir);
        Thread.sleep(200); // Wait for subdirectory to be registered
        Path newFile = subDir.resolve("Deep.java");
        Files.createFile(newFile);
        Files.write(newFile, "public class Deep {}".getBytes());

        // Then: Change in subdirectory is detected
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "File change in subdirectory should be detected");

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher with recursive mode disabled ignores changes in
     * subdirectories.
     * Verifies that non-recursive mode only watches the immediate directory level.
     */
    @Test
    void testWatchWithNonRecursiveMode() throws IOException, InterruptedException {
        // Given: FileWatcher with recursive mode disabled
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);
        Path subDir = watchDir.resolve("subdir");
        Files.createDirectories(subDir);

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);
        AtomicInteger callCount = new AtomicInteger(0);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    callCount.incrementAndGet();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create a file in subdirectory (should not be watched)
        Thread.sleep(200);
        Path subFile = subDir.resolve("NotWatched.java");
        Files.createFile(subFile);

        // Then: Wait a bit and verify no detection
        Thread.sleep(500);
        assertEquals(0, callCount.get(), "Subdirectory changes should not be detected in non-recursive mode");

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher with PathMatcher only detects files matching the
     * filter criteria.
     * Verifies that file filtering works correctly, ignoring non-matching files.
     */
    @Test
    void testWatchWithPathMatcher() throws IOException, InterruptedException {
        // Given: FileWatcher with PathMatcher filtering Java files
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        PathMatcher pathMatcher = PathMatcher.forTargetCode();
        fileWatcher = new FileWatcher(watchDir, 100, false, pathMatcher, mockLog);
        CountDownLatch latch = new CountDownLatch(1);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create both Java and non-Java files
        Thread.sleep(200);
        Path javaFile = watchDir.resolve("Match.java");
        Path txtFile = watchDir.resolve("NoMatch.txt");
        Files.createFile(javaFile);
        Files.createFile(txtFile);
        Files.write(javaFile, "public class Match {}".getBytes());
        Files.write(txtFile, "text content".getBytes());

        // Then: Only Java file is detected
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "Java file should be detected");
        assertTrue(changedPaths.stream().anyMatch(p -> p.getFileName().toString().equals("Match.java")));
        assertFalse(changedPaths.stream().anyMatch(p -> p.getFileName().toString().equals("NoMatch.txt")));

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher in continuous watch mode detects multiple file changes
     * over time.
     * Verifies that the watcher remains active and detects subsequent changes.
     */
    @Test
    void testWatchModeDetectsMultipleChanges() throws IOException, InterruptedException {
        // Given: FileWatcher in watch mode (continuous monitoring)
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);
        AtomicInteger changeCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changeCount.incrementAndGet();
                    latch.countDown();
                }, true);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create multiple files over time
        Thread.sleep(200);
        Files.createFile(watchDir.resolve("File1.java"));
        Thread.sleep(300);
        Files.createFile(watchDir.resolve("File2.java"));

        // Then: Multiple changes are detected
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected, "Multiple changes should be detected");
        assertTrue(changeCount.get() >= 2, "Should detect at least 2 changes");

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that calling stop() properly terminates the FileWatcher and its
     * monitoring thread.
     * Verifies clean shutdown behavior and resource cleanup.
     */
    @Test
    void testStopStopsWatching() throws IOException, InterruptedException {
        // Given: Running FileWatcher
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                }, true);
            } catch (IOException | InterruptedException e) {
                // Expected when stopped
            }
        });
        watchThread.start();
        Thread.sleep(200);

        // When: Stop the watcher
        fileWatcher.stop();
        watchThread.join(1000);

        // Then: Thread should complete
        assertFalse(watchThread.isAlive(), "Watch thread should stop after calling stop()");
    }

    /**
     * Tests that attempting to start FileWatcher when already running throws
     * IllegalStateException.
     * Verifies proper state management and prevents concurrent watch operations.
     */
    @Test
    void testDoubleStartThrowsException() throws IOException, InterruptedException {
        // Given: Running FileWatcher
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);

        Thread watchThread1 = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                }, true);
            } catch (IOException | InterruptedException e) {
                // Expected
            }
        });
        watchThread1.start();
        Thread.sleep(200);

        // When/Then: Starting again should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            fileWatcher.watch(paths -> {
            }, true);
        });

        fileWatcher.stop();
        watchThread1.join(1000);
    }

    /**
     * Tests that FileWatcher debounces rapid file changes into a single callback.
     * Verifies that multiple changes within the debounce period are aggregated
     * together.
     */
    @Test
    void testDebounceAggregatesChanges() throws IOException, InterruptedException {
        // Given: FileWatcher with debounce time
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 500, false, null, mockLog);
        AtomicInteger callCount = new AtomicInteger(0);
        List<List<Path>> allChanges = new ArrayList<>();

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    callCount.incrementAndGet();
                    synchronized (allChanges) {
                        allChanges.add(new ArrayList<>(paths));
                    }
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create multiple files quickly
        Thread.sleep(200);
        Files.createFile(watchDir.resolve("File1.java"));
        Thread.sleep(50);
        Files.createFile(watchDir.resolve("File2.java"));
        Thread.sleep(50);
        Files.createFile(watchDir.resolve("File3.java"));

        // Then: Changes are aggregated into one callback
        Thread.sleep(1000);
        assertEquals(1, callCount.get(), "Debounce should aggregate changes into one callback");
        synchronized (allChanges) {
            assertFalse(allChanges.isEmpty());
            assertTrue(allChanges.get(0).size() >= 2, "Multiple changes should be aggregated");
        }

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher can be constructed with an empty path list.
     * Verifies boundary condition handling for edge case scenarios.
     */
    @Test
    void testWatchWithEmptyPathList() throws IOException {
        // Given: Empty path list (boundary value test)
        List<Path> emptyPaths = Collections.emptyList();

        // When: Create FileWatcher
        fileWatcher = new FileWatcher(emptyPaths, 100, false, null, mockLog);

        // Then: FileWatcher is created successfully
        assertNotNull(fileWatcher);
    }

    /**
     * Tests that FileWatcher only reports changes to regular files, not
     * directories.
     * Verifies that directory creation/modification events are filtered out.
     */
    @Test
    void testWatchFiltersNonRegularFiles() throws IOException, InterruptedException {
        // Given: FileWatcher monitoring a directory
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        fileWatcher = new FileWatcher(watchDir, 100, false, null, mockLog);
        CountDownLatch latch = new CountDownLatch(1);

        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    latch.countDown();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create a subdirectory (not a regular file)
        Thread.sleep(200);
        Files.createDirectories(watchDir.resolve("NewDirectory"));

        // Also create a regular file to trigger the callback
        Thread.sleep(100);
        Files.createFile(watchDir.resolve("File.java"));

        // Then: Only regular files are included
        boolean detected = latch.await(3, TimeUnit.SECONDS);
        assertTrue(detected);
        assertTrue(changedPaths.stream().allMatch(p -> {
            try {
                return Files.isRegularFile(p);
            } catch (Exception e) {
                return false;
            }
        }));

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher excludes changes in directories matching exclusion
     * patterns.
     * Verifies that PathMatcher exclusion rules work correctly for existing
     * directories.
     */
    @Test
    void testExcludeDirectoryFromWatching() throws IOException, InterruptedException {
        // Given: FileWatcher with PathMatcher that excludes 'target' directory
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);
        Path targetDir = watchDir.resolve("target");
        Files.createDirectories(targetDir);

        List<String> excludes = Collections.singletonList("**/target");
        PathMatcher pathMatcher = PathMatcher.of(Collections.emptyList(), excludes);
        fileWatcher = new FileWatcher(watchDir, 100, true, pathMatcher, mockLog);

        AtomicInteger callCount = new AtomicInteger(0);
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    callCount.incrementAndGet();
                }, false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create a file in the excluded directory
        Thread.sleep(200);
        Path excludedFile = targetDir.resolve("ExcludedFile.java");
        Files.createFile(excludedFile);
        Files.write(excludedFile, "public class ExcludedFile {}".getBytes());

        // Then: No changes should be detected (file in excluded directory is ignored)
        Thread.sleep(500);
        assertEquals(0, callCount.get(), "Changes in excluded directory should not be detected");
        assertTrue(changedPaths.isEmpty(), "No files should be detected in excluded directory");

        fileWatcher.stop();
        watchThread.join(1000);
    }

    /**
     * Tests that FileWatcher excludes changes in newly created directories matching
     * exclusion patterns.
     * Verifies that exclusion rules apply to directories created during watch
     * operation.
     */
    @Test
    void testExcludeNewlyCreatedDirectory() throws IOException, InterruptedException {
        // Given: FileWatcher with PathMatcher that excludes 'build' directory
        Path watchDir = tempDir.resolve("watch");
        Files.createDirectories(watchDir);

        List<String> excludes = Collections.singletonList("**/build");
        PathMatcher pathMatcher = PathMatcher.of(Collections.emptyList(), excludes);
        fileWatcher = new FileWatcher(watchDir, 100, true, pathMatcher, mockLog);

        AtomicInteger callCount = new AtomicInteger(0);
        Thread watchThread = new Thread(() -> {
            try {
                fileWatcher.watch(paths -> {
                    changedPaths.addAll(paths);
                    callCount.incrementAndGet();
                }, true); // Use watch mode for continuous monitoring
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        watchThread.start();

        // When: Create a new 'build' directory during execution
        Thread.sleep(200);
        Path buildDir = watchDir.resolve("build");
        Files.createDirectories(buildDir);
        Thread.sleep(200); // Wait for directory creation to be processed

        // Create a file inside the newly created excluded directory
        Path excludedFile = buildDir.resolve("ExcludedFile.java");
        Files.createFile(excludedFile);
        Files.write(excludedFile, "public class ExcludedFile {}".getBytes());

        // Then: No changes should be detected from the excluded directory
        Thread.sleep(500);
        assertEquals(0, callCount.get(),
                "Changes in newly created excluded directory should not be detected");
        assertTrue(changedPaths.isEmpty(),
                "No files should be detected in newly created excluded directory");

        fileWatcher.stop();
        watchThread.join(1000);
    }
}
