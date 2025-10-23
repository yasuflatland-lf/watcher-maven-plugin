package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Default implementation of FileWatcherFactory.
 * Creates standard FileWatcher instances for production use.
 */
public class DefaultFileWatcherFactory implements FileWatcherFactory {

    @Override
    public FileWatcher create(List<Path> watchPaths, long debounceMs, boolean recursive,
                             PathMatcher pathMatcher, Log log) throws IOException {
        return new FileWatcher(watchPaths, debounceMs, recursive, pathMatcher, log);
    }
}
