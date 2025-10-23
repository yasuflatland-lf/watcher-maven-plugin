package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Maven Plugin that monitors file changes and automatically runs affected tests
 */
@Mojo(name = "watch", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class WatcherMojo extends AbstractMojo {

    // ANSI color codes
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    /**
     * Maven project
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Plugin descriptor
     */
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor plugin;

    /**
     * Source directory
     */
    @Parameter(property = "watcher.sourceDirectory", defaultValue = "${project.build.sourceDirectory}", required = true)
    private File sourceDirectory;

    /**
     * Test source directory
     */
    @Parameter(property = "watcher.testSourceDirectory", defaultValue = "${project.build.testSourceDirectory}", required = true)
    private File testSourceDirectory;

    /**
     * Additional directories to watch
     */
    @Parameter(property = "watcher.watchDirectories")
    private List<File> watchDirectories = new ArrayList<>();

    /**
     * Debounce time for file change detection (milliseconds)
     */
    @Parameter(property = "watcher.debounceMs", defaultValue = "750")
    private long debounceMs;

    /**
     * Watch mode
     * true: continuous monitoring, false: detect changes once
     */
    @Parameter(property = "watcher.watchMode", defaultValue = "true")
    private boolean watchMode;

    /**
     * Whether to monitor directories recursively
     */
    @Parameter(property = "watcher.recursive", defaultValue = "true")
    private boolean recursive;

    /**
     * File patterns to include (Glob patterns)
     */
    @Parameter(property = "watcher.includes")
    private List<String> includes = new ArrayList<>();

    /**
     * File patterns to exclude (Glob patterns)
     */
    @Parameter(property = "watcher.excludes")
    private List<String> excludes = new ArrayList<>();

    /**
     * Number of times to rerun failed tests in Surefire plugin
     */
    @Parameter(property = "watcher.rerunFailingTestsCount", defaultValue = "0")
    private int rerunFailingTestsCount;

    /**
     * Test execution order for Surefire plugin
     */
    @Parameter(property = "watcher.runOrder", defaultValue = "filesystem")
    private String runOrder;

    /**
     * Whether to run tests in parallel with Surefire plugin
     */
    @Parameter(property = "watcher.parallel", defaultValue = "false")
    private boolean parallel;

    /**
     * Number of threads for parallel execution
     */
    @Parameter(property = "watcher.threadCount", defaultValue = "1")
    private int threadCount;

    /**
     * Number of tests to skip after failure
     */
    @Parameter(property = "watcher.skipAfterFailureCount", defaultValue = "0")
    private int skipAfterFailureCount;

    /**
     * Whether to output verbose logs
     */
    @Parameter(property = "watcher.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * Additional properties to pass to Maven test command
     */
    @Parameter(property = "watcher.additionalProperties")
    private Map<String, String> additionalProperties = new HashMap<>();

    /**
     * JUnit 5 tag expression to include tests (watcher-maven-plugin specific).
     * Takes precedence over 'groups' property.
     * Multiple tags can be combined using operators: , (OR), & (AND), ! (NOT).
     * If not specified, all tests will be executed regardless of tags.
     *
     * <p>
     * Example usage in pom.xml:
     * </p>
     *
     * <pre>
     * &lt;plugin&gt;
     *     &lt;groupId&gt;com.github.yasuflatland-lf&lt;/groupId&gt;
     *     &lt;artifactId&gt;watcher-maven-plugin&lt;/artifactId&gt;
     *     &lt;configuration&gt;
     *         &lt;groups&gt;WIP&lt;/groups&gt;
     *         &lt;excludedGroups&gt;slow,integration&lt;/excludedGroups&gt;
     *     &lt;/configuration&gt;
     * &lt;/plugin&gt;
     * </pre>
     *
     * <p>
     * Command line usage (both formats supported):
     * </p>
     * 
     * <pre>
     * mvn watcher:run -Dwatcher.groups=WIP              (recommended)
     * mvn watcher:run -Dgroups=WIP                      (Maven Surefire compatible)
     * mvn watcher:run -Dwatcher.groups="unit,fast" -Dwatcher.excludedGroups=slow
     * </pre>
     *
     * <p>
     * Note: If both 'watcher.groups' and 'groups' are specified, 'watcher.groups'
     * takes precedence.
     * </p>
     *
     * @since 1.1.0
     */
    @Parameter(property = "watcher.groups")
    private String watcherGroups;

    /**
     * JUnit 5 tag expression to include tests (Maven Surefire compatible).
     * Used as fallback if 'watcher.groups' is not specified.
     * This provides compatibility with standard Maven Surefire plugin.
     *
     * @since 1.1.0
     * @see #watcherGroups
     */
    @Parameter(property = "groups")
    private String groups;

    /**
     * JUnit 5 tag expression to exclude tests (watcher-maven-plugin specific).
     * Takes precedence over 'excludedGroups' property.
     * Multiple tags can be combined using operators: , (OR), & (AND), ! (NOT).
     * Excluded groups take precedence over included groups.
     *
     * @since 1.1.0
     * @see #watcherGroups
     */
    @Parameter(property = "watcher.excludedGroups")
    private String watcherExcludedGroups;

    /**
     * JUnit 5 tag expression to exclude tests (Maven Surefire compatible).
     * Used as fallback if 'watcher.excludedGroups' is not specified.
     * This provides compatibility with standard Maven Surefire plugin.
     *
     * @since 1.1.0
     * @see #watcherExcludedGroups
     */
    @Parameter(property = "excludedGroups")
    private String excludedGroups;

    /**
     * Whether Surefire should use file-based output.
     * When set to false (default), suppresses .surefire-* temporary files.
     * Set to true to enable file-based output for Surefire plugin.
     *
     * @since 1.2.0
     */
    @Parameter(property = "watcher.useFile", defaultValue = "false")
    private boolean useFile;

    /**
     * Temporary directory for java.io.tmpdir.
     * Default is "target/tmp" to redirect temporary files to target directory.
     * This prevents .surefire-* files from being created in the project root.
     *
     * @since 1.2.0
     */
    @Parameter(property = "watcher.tempDir", defaultValue = "target/tmp")
    private String tempDir;

    /**
     * Orchestrator for queuing file changes during test execution (watchMode=true
     * only)
     */
    private QueueOrchestrator queueOrchestrator;

    /**
     * Factory for creating FileWatcher instances.
     * Can be overridden for testing purposes.
     */
    private FileWatcherFactory fileWatcherFactory = new DefaultFileWatcherFactory();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        printBanner();
        validateConfiguration();

        WatcherConfig config = createConfig();
        Selector selector = new Selector(project, config, getLog());

        // Initialize queue orchestrator for watchMode=true
        if (watchMode) {
            queueOrchestrator = new QueueOrchestrator();
        }

        getLog().info("👀 Starting File Watcher Maven Plugin...");
        getLog().info("Source directory: " + sourceDirectory.getAbsolutePath());
        getLog().info("Test directory: " + testSourceDirectory.getAbsolutePath());
        getLog().info("Watch mode: " + (watchMode ? "continuous" : "one-shot"));
        getLog().info("Debounce: " + debounceMs + "ms");

        // Determine effective groups (watcher.groups takes precedence)
        String effectiveGroups = watcherGroups != null && !watcherGroups.trim().isEmpty()
                ? watcherGroups
                : groups;
        String effectiveExcludedGroups = watcherExcludedGroups != null && !watcherExcludedGroups.trim().isEmpty()
                ? watcherExcludedGroups
                : excludedGroups;

        if (effectiveGroups != null && !effectiveGroups.trim().isEmpty()) {
            getLog().info("Tag filtering - groups: " + effectiveGroups);
        }

        if (effectiveExcludedGroups != null && !effectiveExcludedGroups.trim().isEmpty()) {
            getLog().info("Tag filtering - excluded groups: " + effectiveExcludedGroups);
        }

        if (rerunFailingTestsCount > 0) {
            getLog().info("Rerun failing tests: " + rerunFailingTestsCount + " times");
        }

        try {
            // Collect directories to watch
            List<Path> watchPaths = collectWatchPaths();

            // Create PathMatcher
            PathMatcher pathMatcher = createPathMatcher();

            // Create FileWatcher using factory
            FileWatcher fileWatcher = fileWatcherFactory.create(
                    watchPaths,
                    debounceMs,
                    recursive,
                    pathMatcher,
                    getLog());

            // Start file monitoring
            fileWatcher.watch(createFileChangeHandler(selector), watchMode);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to watch files", e);
        }

        getLog().info("File Watcher stopped");
    }

    /**
     * Handle file changes
     */
    private void handleFileChanges(List<Path> changedFiles, Selector selector)
            throws MojoExecutionException {

        if (changedFiles == null || changedFiles.isEmpty()) {
            return;
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug(String.format("handleFileChanges called with %d file(s)", changedFiles.size()));
        }

        // If watchMode=true and test is running, enqueue changes
        if (watchMode && queueOrchestrator != null && queueOrchestrator.isTestRunning()) {
            getLog().info("Test is running. Queueing " + changedFiles.size() + " changed file(s)...");
            queueOrchestrator.enqueue(changedFiles);
            return;
        }

        // Process changes with queue
        processChangesWithQueue(changedFiles, selector);
    }

    /**
     * Process file changes with queue support
     */
    private void processChangesWithQueue(List<Path> changedFiles, Selector selector)
            throws MojoExecutionException {

        // Queue mode: watchMode=true
        if (watchMode && queueOrchestrator != null) {
            // First, add current changes to queue
            queueOrchestrator.enqueue(changedFiles);

            // Process queue until empty
            while (!queueOrchestrator.isEmpty()) {
                // Dequeue all files
                List<Path> queuedFiles = queueOrchestrator.dequeue();

                getLog().info(CYAN + "Processing " + queuedFiles.size() + " changed file(s) from queue..." + RESET);

                // Mark test as running
                queueOrchestrator.markTestRunning();

                try {
                    // Select and execute tests
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Calling Selector.selectTests()...");
                    }
                    List<String> testClasses = selector.selectTests(queuedFiles);

                    if (!testClasses.isEmpty()) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug(String.format("Calling Selector.executeTests() with %d test(s)...",
                                    testClasses.size()));
                        }
                        boolean success = selector.executeTests(testClasses);

                        if (!success) {
                            // Test failed: preserve queue and exit
                            getLog().warn("Tests failed. Preserving queue and waiting for next change...");
                            queueOrchestrator.requeueAtFront(queuedFiles);
                            break;
                        }
                    } else {
                        getLog().info("No tests to run for these changes");
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Selector.selectTests() returned empty list - no tests will be executed");
                        }
                    }
                } finally {
                    // Mark test as complete
                    queueOrchestrator.markTestComplete();
                }
            }
        } else {
            // Traditional processing (watchMode=false)
            if (getLog().isDebugEnabled()) {
                getLog().debug("Calling Selector.selectTests()...");
            }
            List<String> testClasses = selector.selectTests(changedFiles);

            if (testClasses.isEmpty()) {
                getLog().info("No tests to run for these changes");
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Selector.selectTests() returned empty list - no tests will be executed");
                }
                return;
            }

            if (getLog().isDebugEnabled()) {
                getLog().debug(String.format("Calling Selector.executeTests() with %d test(s)...", testClasses.size()));
            }
            boolean success = selector.executeTests(testClasses);

            if (!success && !watchMode) {
                throw new MojoExecutionException("Tests failed");
            }
        }
    }

    /**
     * Validate configuration
     */
    private void validateConfiguration() throws MojoExecutionException {
        if (!sourceDirectory.exists()) {
            throw new MojoExecutionException(
                    "Source directory does not exist: " + sourceDirectory.getAbsolutePath());
        }

        if (!testSourceDirectory.exists()) {
            throw new MojoExecutionException(
                    "Test source directory does not exist: " + testSourceDirectory.getAbsolutePath());
        }

        if (debounceMs < 0) {
            throw new MojoExecutionException("debounceMs must be >= 0");
        }

        if (rerunFailingTestsCount < 0) {
            throw new MojoExecutionException("rerunFailingTestsCount must be >= 0");
        }

        if (threadCount < 1) {
            throw new MojoExecutionException("threadCount must be >= 1");
        }
    }

    /**
     * Create WatcherConfig object
     */
    private WatcherConfig createConfig() {
        WatcherConfig config = new WatcherConfig();
        config.setSourceDirectory(sourceDirectory);
        config.setTestSourceDirectory(testSourceDirectory);
        config.setWatchDirectories(watchDirectories);
        config.setDebounceMs(debounceMs);
        config.setWatchMode(watchMode);
        config.setRecursive(recursive);
        config.setIncludes(includes);
        config.setExcludes(excludes);
        config.setRerunFailingTestsCount(rerunFailingTestsCount);
        config.setRunOrder(runOrder);
        config.setParallel(parallel);
        config.setThreadCount(threadCount);
        config.setSkipAfterFailureCount(skipAfterFailureCount);
        config.setVerbose(verbose);
        config.setAdditionalProperties(additionalProperties);
        config.setWatcherGroups(watcherGroups);
        config.setGroups(groups);
        config.setWatcherExcludedGroups(watcherExcludedGroups);
        config.setExcludedGroups(excludedGroups);
        config.setUseFile(useFile);
        config.setTempDir(tempDir);
        return config;
    }

    /**
     * Collect paths to watch
     */
    private List<Path> collectWatchPaths() {
        List<Path> paths = new ArrayList<>();
        paths.add(sourceDirectory.toPath());
        paths.add(testSourceDirectory.toPath());

        if (watchDirectories != null) {
            for (File dir : watchDirectories) {
                if (dir.exists() && dir.isDirectory()) {
                    paths.add(dir.toPath());
                } else {
                    getLog().warn("Watch directory does not exist or is not a directory: " + dir);
                }
            }
        }

        return paths;
    }

    /**
     * Create PathMatcher
     */
    private PathMatcher createPathMatcher() {
        if ((includes == null || includes.isEmpty()) &&
                (excludes == null || excludes.isEmpty())) {
            // Default: Java/Kotlin files (including test files)
            return PathMatcher.forTargetCode();
        }

        return PathMatcher.of(
                includes != null ? includes : new ArrayList<>(),
                excludes != null ? excludes : new ArrayList<>());
    }

    /**
     * Display plugin banner
     */
    private void printBanner() {
        // Get version from plugin descriptor (not project)
        String version = (plugin != null && plugin.getVersion() != null) ? plugin.getVersion() : "unknown";
        String title = "Watch Files Maven Plugin v" + version;

        // Calculate padding for centering
        int boxWidth = Math.max(46, title.length() + 4);
        int padding = (boxWidth - title.length() - 2) / 2;
        String paddedTitle = "║" + " ".repeat(padding) + title + " ".repeat(boxWidth - title.length() - padding - 2)
                + "║";
        String subtitle = "File change detection & auto testing";
        int subtitlePadding = (boxWidth - subtitle.length() - 2) / 2;
        String paddedSubtitle = "║" + " ".repeat(subtitlePadding) + subtitle
                + " ".repeat(boxWidth - subtitle.length() - subtitlePadding - 2) + "║";

        getLog().info("");
        getLog().info("╔" + "═".repeat(boxWidth - 2) + "╗");
        getLog().info(paddedTitle);
        getLog().info(paddedSubtitle);
        getLog().info("╚" + "═".repeat(boxWidth - 2) + "╝");
        getLog().info("");
    }

    /**
     * Creates a file change handler that processes changed files.
     * Package-private for testing purposes.
     *
     * @param selector The test selector to use for determining which tests to run
     * @return A Consumer that handles file changes
     */
    Consumer<List<Path>> createFileChangeHandler(Selector selector) {
        return changedFiles -> {
            try {
                handleFileChanges(changedFiles, selector);
            } catch (MojoExecutionException e) {
                getLog().error("Error handling file changes", e);
            }
        };
    }

    /**
     * Sets the FileWatcherFactory for testing purposes.
     * Package-private to allow access from tests.
     *
     * @param fileWatcherFactory The factory to use
     */
    void setFileWatcherFactory(FileWatcherFactory fileWatcherFactory) {
        this.fileWatcherFactory = fileWatcherFactory;
    }
}
