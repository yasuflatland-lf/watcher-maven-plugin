package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Test implementation of FileWatcherFactory.
 * Creates non-blocking FileWatcher instances for testing purposes.
 * <p>
 * This factory creates FileWatcher instances that immediately trigger the callback
 * with the provided test files and then return, avoiding the blocking watch loop
 * that would prevent tests from completing.
 */
public class TestFileWatcherFactory implements FileWatcherFactory {

    private final List<Path> filesToTrigger;
    private final Consumer<List<Path>> callbackCapture;

    /**
     * Creates a test factory.
     *
     * @param filesToTrigger Files to trigger when watch() is called (can be empty)
     * @param callbackCapture Optional consumer to capture the callback for verification (can be null)
     */
    public TestFileWatcherFactory(List<Path> filesToTrigger, Consumer<List<Path>> callbackCapture) {
        this.filesToTrigger = filesToTrigger;
        this.callbackCapture = callbackCapture;
    }

    @Override
    public FileWatcher create(List<Path> watchPaths, long debounceMs, boolean recursive,
                             PathMatcher pathMatcher, Log log) throws IOException {
        return new FileWatcher(watchPaths, debounceMs, recursive, pathMatcher, log) {
            @Override
            public void watch(Consumer<List<Path>> callback, boolean watchMode) {
                // Capture callback if requested
                if (callbackCapture != null) {
                    callbackCapture.accept(filesToTrigger);
                }

                // Trigger callback with test files if any
                if (!filesToTrigger.isEmpty()) {
                    callback.accept(filesToTrigger);
                }

                // Return immediately without blocking (do not call super.watch())
            }
        };
    }
}
