package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Class for monitoring file system changes
 * Uses Java NIO WatchService to detect file changes under specified directories
 */
public class FileWatcher {
    private final List<Path> paths;
    private final long debounceMs;
    private final boolean recursive;
    private final PathMatcher pathMatcher;
    private final Log log;

    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeys;
    private final AtomicBoolean running;

    /**
     * Constructor for multiple paths
     */
    public FileWatcher(List<Path> paths, long debounceMs, boolean recursive, PathMatcher pathMatcher, Log log)
            throws IOException {
        this.paths = paths;
        this.debounceMs = debounceMs;
        this.recursive = recursive;
        this.pathMatcher = pathMatcher;
        this.log = log;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchKeys = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Constructor for single path
     */
    public FileWatcher(Path path, long debounceMs, boolean recursive, PathMatcher pathMatcher, Log log)
            throws IOException {
        this(Collections.singletonList(path), debounceMs, recursive, pathMatcher, log);
    }

    /**
     * Start file monitoring
     * @param onChanged Callback function when changes are detected
     * @param watchMode true: continuous monitoring, false: detect changes once
     */
    public void watch(Consumer<List<Path>> onChanged, boolean watchMode) throws IOException, InterruptedException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("FileWatcher is already running");
        }

        try {
            // Register directories to watch
            if (log.isDebugEnabled()) {
                log.debug("Starting directory registration...");
            }
            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    registerDirectory(path);
                    if (log.isDebugEnabled()) {
                        log.debug("Registered directory: " + path);
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Directory registration complete. Starting file monitoring loop...");
            }

            Set<Path> changedFiles = new HashSet<>();
            long lastChangeTime = System.currentTimeMillis();
            boolean hasDetectedChange = false;

            while (running.get()) {
                // Set short polling interval
                WatchKey key = watchService.poll(100, TimeUnit.MILLISECONDS);

                if (key != null) {
                    Path dir = watchKeys.get(key);
                    if (dir != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("File system event detected in directory: " + dir);
                        }
                        int beforeSize = changedFiles.size();
                        processWatchEvents(key, dir, changedFiles);
                        int afterSize = changedFiles.size();
                        if (afterSize > beforeSize && log.isDebugEnabled()) {
                            log.debug("Added " + (afterSize - beforeSize) + " file(s) to changed files set");
                        }
                        lastChangeTime = System.currentTimeMillis();
                    }

                    // Re-register directory
                    if (!key.reset()) {
                        watchKeys.remove(key);
                    }
                }

                // Debounce processing: execute callback after specified time since last change
                if (!changedFiles.isEmpty() &&
                    System.currentTimeMillis() - lastChangeTime > debounceMs) {

                    if (log.isDebugEnabled()) {
                        log.debug("Debounce period elapsed. Processing " + changedFiles.size() + " changed file(s)");
                        log.debug("Changed files:");
                        for (Path file : changedFiles) {
                            log.debug("  - " + file);
                        }
                    }

                    List<Path> filteredFiles = changedFiles.stream()
                            .filter(this::shouldWatch)
                            .collect(Collectors.toList());

                    if (log.isDebugEnabled()) {
                        log.debug("After filtering: " + filteredFiles.size() + " file(s)");
                        if (!filteredFiles.isEmpty()) {
                            log.debug("Filtered files:");
                            for (Path file : filteredFiles) {
                                log.debug("  - " + file);
                            }
                            log.debug("Invoking callback (handleFileChanges) to process changes...");
                        }
                    }

                    if (!filteredFiles.isEmpty()) {
                        onChanged.accept(filteredFiles);
                        hasDetectedChange = true;
                    }

                    changedFiles.clear();

                    // Exit if one-shot mode
                    if (!watchMode) {
                        break;
                    }
                }

                // Exit if change detected in one-shot mode
                if (!watchMode && hasDetectedChange) {
                    break;
                }
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * Stop monitoring
     */
    public void stop() {
        running.set(false);
        try {
            watchService.close();
        } catch (IOException e) {
            // Ignore close exception
        }
    }

    /**
     * Register directory for monitoring
     */
    private void registerDirectory(Path path) throws IOException {
        if (recursive) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    // Skip directories that match exclusion patterns
                    if (pathMatcher != null && pathMatcher.matchesDirectory(dir)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Skipping excluded directory: " + dir);
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    registerSingleDirectory(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            registerSingleDirectory(path);
        }
    }

    /**
     * Register single directory
     */
    private void registerSingleDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(
            watchService,
            ENTRY_CREATE,
            ENTRY_DELETE,
            ENTRY_MODIFY
        );
        watchKeys.put(key, dir);
    }

    /**
     * Process WatchEvents and collect changed files
     */
    private void processWatchEvents(WatchKey key, Path dir, Set<Path> changedFiles) throws IOException {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();

            // Skip on overflow
            if (kind == OVERFLOW) {
                log.warn("WatchEvent OVERFLOW detected - some file changes may have been missed");
                continue;
            }

            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path fileName = ev.context();
            Path changedPath = dir.resolve(fileName);

            if (log.isDebugEnabled()) {
                log.debug("Event: " + kind.name() + " - " + changedPath);
            }

            // Add newly created directories to monitoring targets
            if (kind == ENTRY_CREATE && Files.isDirectory(changedPath) && recursive) {
                // Check if the new directory should be excluded
                if (pathMatcher != null && pathMatcher.matchesDirectory(changedPath)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping newly created excluded directory: " + changedPath);
                    }
                } else {
                    registerDirectory(changedPath);
                }
            }

            if (Files.exists(changedPath) && Files.isRegularFile(changedPath)) {
                changedFiles.add(changedPath);
            }
        }
    }

    /**
     * Determine if path should be monitored
     */
    private boolean shouldWatch(Path path) {
        // Monitor all files if PathMatcher is not configured
        if (pathMatcher == null) {
            return isJavaOrKotlinFile(path);
        }

        return pathMatcher.matches(path);
    }

    /**
     * Determine if file is Java or Kotlin
     */
    private boolean isJavaOrKotlinFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".kt");
    }
}
