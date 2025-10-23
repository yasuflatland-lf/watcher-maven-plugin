package com.sennproject.maven.plugin.watch.files;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that holds file monitoring configuration
 * Used as Maven plugin parameters
 */
public class WatcherConfig {
    /**
     * Source directory to monitor
     */
    private File sourceDirectory;

    /**
     * Test source directory to monitor
     */
    private File testSourceDirectory;

    /**
     * Additional directories to monitor
     */
    private List<File> watchDirectories = new ArrayList<>();

    /**
     * Debounce time for file change detection (milliseconds)
     * Default is 750ms
     */
    private long debounceMs = 750;

    /**
     * Watch mode
     * true: continuous monitoring, false: detect changes once
     */
    private boolean watchMode = true;

    /**
     * Whether to monitor directories recursively
     */
    private boolean recursive = true;

    /**
     * File patterns to include (Glob patterns)
     */
    private List<String> includes = new ArrayList<>();

    /**
     * File patterns to exclude (Glob patterns)
     */
    private List<String> excludes = new ArrayList<>();

    /**
     * Number of times to rerun failed tests in Surefire plugin
     * 0 means no rerun
     */
    private int rerunFailingTestsCount = 0;

    /**
     * Test execution order for Surefire plugin
     * Default is filesystem
     * Options: alphabetical, reversealphabetical, random, hourly, failedfirst,
     * filesystem
     */
    private String runOrder = "filesystem";

    /**
     * Whether to run tests in parallel with Surefire plugin
     */
    private boolean parallel = false;

    /**
     * Number of threads for parallel execution
     */
    private int threadCount = 1;

    /**
     * Number of tests to skip after failure
     * 0 means no skipping
     */
    private int skipAfterFailureCount = 0;

    /**
     * Whether to output verbose logs
     */
    private boolean verbose = false;

    /**
     * Additional properties to pass to Maven test command
     */
    private Map<String, String> additionalProperties = new HashMap<>();

    /**
     * JUnit 5 tag expression to include tests (watcher-maven-plugin specific)
     * Takes precedence over 'groups'
     */
    private String watcherGroups;

    /**
     * JUnit 5 tag expression to include tests (Maven Surefire compatible)
     * Used as fallback
     */
    private String groups;

    /**
     * JUnit 5 tag expression to exclude tests (watcher-maven-plugin specific)
     * Takes precedence over 'excludedGroups'
     */
    private String watcherExcludedGroups;

    /**
     * JUnit 5 tag expression to exclude tests (Maven Surefire compatible)
     * Used as fallback
     */
    private String excludedGroups;

    /**
     * Whether Surefire should use file-based output
     * Default is false to suppress .surefire-* temporary files
     */
    private boolean useFile = false;

    /**
     * Temporary directory for java.io.tmpdir
     * Default is "target/tmp" to redirect temporary files to target directory
     */
    private String tempDir = "target/tmp";

    // Getters and Setters

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public File getTestSourceDirectory() {
        return testSourceDirectory;
    }

    public void setTestSourceDirectory(File testSourceDirectory) {
        this.testSourceDirectory = testSourceDirectory;
    }

    public List<File> getWatchDirectories() {
        return watchDirectories;
    }

    public void setWatchDirectories(List<File> watchDirectories) {
        this.watchDirectories = watchDirectories;
    }

    public long getDebounceMs() {
        return debounceMs;
    }

    public void setDebounceMs(long debounceMs) {
        this.debounceMs = debounceMs;
    }

    public boolean isWatchMode() {
        return watchMode;
    }

    public void setWatchMode(boolean watchMode) {
        this.watchMode = watchMode;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public int getRerunFailingTestsCount() {
        return rerunFailingTestsCount;
    }

    public void setRerunFailingTestsCount(int rerunFailingTestsCount) {
        this.rerunFailingTestsCount = rerunFailingTestsCount;
    }

    public String getRunOrder() {
        return runOrder;
    }

    public void setRunOrder(String runOrder) {
        this.runOrder = runOrder;
    }

    public boolean isParallel() {
        return parallel;
    }

    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getSkipAfterFailureCount() {
        return skipAfterFailureCount;
    }

    public void setSkipAfterFailureCount(int skipAfterFailureCount) {
        this.skipAfterFailureCount = skipAfterFailureCount;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Map<String, String> getAdditionalProperties() {
        return additionalProperties != null ? additionalProperties : new HashMap<>();
    }

    public void setAdditionalProperties(Map<String, String> additionalProperties) {
        this.additionalProperties = additionalProperties != null ? additionalProperties : new HashMap<>();
    }

    public String getWatcherGroups() {
        return watcherGroups;
    }

    public void setWatcherGroups(String watcherGroups) {
        this.watcherGroups = watcherGroups;
    }

    public String getGroups() {
        return groups;
    }

    public void setGroups(String groups) {
        this.groups = groups;
    }

    /**
     * Get effective groups with priority logic.
     * watcher.groups takes precedence over groups.
     *
     * @return effective groups value
     */
    public String getEffectiveGroups() {
        if (watcherGroups != null && !watcherGroups.trim().isEmpty()) {
            return watcherGroups;
        }
        return groups;
    }

    public String getWatcherExcludedGroups() {
        return watcherExcludedGroups;
    }

    public void setWatcherExcludedGroups(String watcherExcludedGroups) {
        this.watcherExcludedGroups = watcherExcludedGroups;
    }

    public String getExcludedGroups() {
        return excludedGroups;
    }

    public void setExcludedGroups(String excludedGroups) {
        this.excludedGroups = excludedGroups;
    }

    /**
     * Get effective excluded groups with priority logic.
     * watcher.excludedGroups takes precedence over excludedGroups.
     *
     * @return effective excluded groups value
     */
    public String getEffectiveExcludedGroups() {
        if (watcherExcludedGroups != null && !watcherExcludedGroups.trim().isEmpty()) {
            return watcherExcludedGroups;
        }
        return excludedGroups;
    }

    public boolean isUseFile() {
        return useFile;
    }

    public void setUseFile(boolean useFile) {
        this.useFile = useFile;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }
}
