package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Factory interface for creating FileWatcher instances.
 * This allows for dependency injection and testing with mock implementations.
 */
@FunctionalInterface
public interface FileWatcherFactory {

    /**
     * Creates a new FileWatcher instance.
     *
     * @param watchPaths Directories to monitor
     * @param debounceMs Debounce time in milliseconds
     * @param recursive Whether to watch subdirectories recursively
     * @param pathMatcher Pattern matcher for filtering files
     * @param log Maven logger
     * @return A new FileWatcher instance
     * @throws IOException If an I/O error occurs during initialization
     */
    FileWatcher create(List<Path> watchPaths, long debounceMs, boolean recursive,
                      PathMatcher pathMatcher, Log log) throws IOException;
}
