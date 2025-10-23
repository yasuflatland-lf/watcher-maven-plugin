package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Class that selects tests to be executed from changed files
 * and runs tests using the Surefire plugin
 */
public class Selector {
    // ANSI color codes
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final MavenProject project;
    private final WatcherConfig config;
    private final Log log;

    public Selector(MavenProject project, WatcherConfig config, Log log) {
        this.project = project;
        this.config = config;
        this.log = log;
    }

    /**
     * Select tests to be executed from changed files
     *
     * @param changedFiles List of changed files
     * @return List of test class names to execute
     */
    public List<String> selectTests(List<Path> changedFiles) {
        if (log.isDebugEnabled()) {
            log.debug("Selector.selectTests() called with " +
                (changedFiles == null ? "null" : changedFiles.size() + " file(s)"));
        }

        if (changedFiles == null || changedFiles.isEmpty()) {
            log.info("No changed files detected");
            return Collections.emptyList();
        }

        log.info(CYAN + "Changed files:" + RESET);
        changedFiles.forEach(file -> log.info(CYAN + "  - " + file + RESET));

        Path sourceRoot = config.getSourceDirectory().toPath();
        Path testRoot = config.getTestSourceDirectory().toPath();

        // Separate files into source files and test files
        List<Path> sourceFiles = new ArrayList<>();
        List<Path> testFiles = new ArrayList<>();

        for (Path path : changedFiles) {
            if (path.startsWith(testRoot)) {
                testFiles.add(path);
            } else if (path.startsWith(sourceRoot)) {
                sourceFiles.add(path);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Categorized files - source: " + sourceFiles.size() + ", test: " + testFiles.size());
        }

        // Use LinkedHashSet to maintain order and avoid duplicates
        Set<String> testClasses = new LinkedHashSet<>();

        // Process test files: run directly
        if (!testFiles.isEmpty()) {
            List<String> directTests = resolveTestFilesToClassNames(testFiles, testRoot);
            testClasses.addAll(directTests);
            if (config.isVerbose()) {
                log.info("Changed test files (will run directly):");
                directTests.forEach(test -> log.info("  - " + test));
            }
        }

        // Process source files: infer corresponding tests
        if (!sourceFiles.isEmpty()) {
            List<String> inferredTests = TestNameResolver.resolve(sourceFiles, sourceRoot);
            List<String> existingTests = filterExistingTests(inferredTests);
            testClasses.addAll(existingTests);
            if (config.isVerbose()) {
                log.info("Changed source files (inferred tests):");
                existingTests.forEach(test -> log.info("  - " + test));
            }
        }

        List<String> result = new ArrayList<>(testClasses);

        if (result.isEmpty()) {
            log.warn("No test classes found for changed files");
        } else {
            log.info(CYAN + "Selected tests:" + RESET);
            result.forEach(test -> log.info(CYAN + "  - " + test + RESET));
        }

        return result;
    }

    /**
     * Filter only existing test classes
     *
     * @param testClasses List of test class names
     * @return List of existing test classes
     */
    private List<String> filterExistingTests(List<String> testClasses) {
        Path testRoot = config.getTestSourceDirectory().toPath();
        List<String> existingTests = new ArrayList<>();

        for (String testClass : testClasses) {
            // Convert fully qualified class name to file path
            String relativePath = testClass.replace('.', '/');
            Path javaPath = testRoot.resolve(relativePath + ".java");
            Path kotlinPath = testRoot.resolve(relativePath + ".kt");

            if (javaPath.toFile().exists() || kotlinPath.toFile().exists()) {
                existingTests.add(testClass);
            }
        }

        return existingTests;
    }

    /**
     * Convert test file paths to fully qualified class names
     *
     * @param testFiles List of test file paths
     * @param testRoot  Test source root directory
     * @return List of fully qualified class names
     */
    private List<String> resolveTestFilesToClassNames(List<Path> testFiles, Path testRoot) {
        List<String> classNames = new ArrayList<>();

        for (Path testFile : testFiles) {
            Path relativePath = testRoot.relativize(testFile);
            String pathString = relativePath.toString();

            // Remove file extension
            if (pathString.endsWith(".java")) {
                pathString = pathString.substring(0, pathString.length() - 5);
            } else if (pathString.endsWith(".kt")) {
                pathString = pathString.substring(0, pathString.length() - 3);
            }

            // Convert path separator to dot
            String className = pathString.replace(File.separatorChar, '.');
            classNames.add(className);
        }

        return classNames;
    }

    /**
     * Execute tests using Surefire plugin
     *
     * @param testClasses List of test classes to execute
     * @return true if test execution succeeded
     * @throws MojoExecutionException if an error occurs during test execution
     */
    public boolean executeTests(List<String> testClasses) throws MojoExecutionException {
        if (log.isDebugEnabled()) {
            log.debug("Selector.executeTests() called with " +
                (testClasses == null ? "null" : testClasses.size() + " test class(es)"));
        }

        if (testClasses == null || testClasses.isEmpty()) {
            log.info("No tests to execute");
            return true;
        }

        log.info(CYAN + "==========================================" + RESET);
        log.info(CYAN + "Executing tests..." + RESET);
        log.info(CYAN + "==========================================" + RESET);

        try {
            // Build Maven command
            List<String> command = buildMavenCommand(testClasses);

            if (config.isVerbose()) {
                log.info("Maven command: " + String.join(" ", command));
            }

            // Execute Maven using ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(project.getBasedir());
            processBuilder.inheritIO(); // Inherit standard input/output

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return true;
            } else {
                log.error("==========================================");
                log.error("Tests failed with exit code: " + exitCode);
                log.error("==========================================");
                return false;
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute tests", e);
        }
    }

    /**
     * Build Maven command
     *
     * @param testClasses List of test classes to execute
     * @return List of Maven command arguments
     */
    private List<String> buildMavenCommand(List<String> testClasses) {
        List<String> command = new ArrayList<>();

        // Determine Maven executable
        String mvnCommand = determineMavenCommand();
        command.add(mvnCommand);

        // Batch mode
        command.add("--batch-mode");

        // test goal
        command.add("test");

        // Specify test classes
        String testParam = TestNameResolver.toTestParameter(testClasses);
        command.add("-Dtest=" + testParam);

        // Tag filtering configuration (using effective values with priority logic)
        String effectiveGroups = config.getEffectiveGroups();
        if (effectiveGroups != null && !effectiveGroups.trim().isEmpty()) {
            command.add("-Dgroups=" + effectiveGroups);
            if (config.isVerbose()) {
                log.info("  Tag filtering enabled - groups: " + effectiveGroups);
            }
        }

        String effectiveExcludedGroups = config.getEffectiveExcludedGroups();
        if (effectiveExcludedGroups != null && !effectiveExcludedGroups.trim().isEmpty()) {
            command.add("-DexcludedGroups=" + effectiveExcludedGroups);
            if (config.isVerbose()) {
                log.info("  Tag filtering enabled - excluded groups: " + effectiveExcludedGroups);
            }
        }

        // rerunFailingTestsCount configuration
        if (config.getRerunFailingTestsCount() > 0) {
            command.add("-Dsurefire.rerunFailingTestsCount=" + config.getRerunFailingTestsCount());
        }

        // runOrder configuration
        if (config.getRunOrder() != null && !config.getRunOrder().isEmpty()) {
            String runOrder = config.getRunOrder().toLowerCase();

            // Warn if using runOrder that generates statistics files
            if ("balanced".equals(runOrder) || "failedfirst".equals(runOrder)) {
                if (config.isVerbose()) {
                    log.warn("runOrder '" + config.getRunOrder() + "' generates statistics files.");
                    log.warn("Consider using 'filesystem' (default), 'alphabetical', 'random', or 'reverse' to avoid .surefire-* files.");
                }
            }

            command.add("-Dsurefire.runOrder=" + config.getRunOrder());
            // Store run history in target directory to avoid .surefire-* files in project root
            command.add("-Dsurefire.runHistoryDirectory=" + project.getBuild().getDirectory() + "/.surefire-history");
            // Disable statistics file generation
            command.add("-Dsurefire.runOrder.memorystatus=false");
        }

        // Parallel execution configuration
        if (config.isParallel()) {
            command.add("-Dparallel=methods");
            command.add("-DthreadCount=" + config.getThreadCount());
        }

        // skipAfterFailureCount configuration
        if (config.getSkipAfterFailureCount() > 0) {
            command.add("-Dsurefire.skipAfterFailureCount=" + config.getSkipAfterFailureCount());
        }

        // useFile configuration (default: false to suppress .surefire-* files)
        command.add("-Dsurefire.useFile=" + config.isUseFile());

        // Suppress surefire temporary directory creation
        command.add("-Dsurefire.tempDir=none");

        // Additional surefire configurations to suppress temp file generation
        command.add("-Dsurefire.trimStackTrace=false");
        command.add("-Dsurefire.redirectTestOutputToFile=false");
        command.add("-Dsurefire.useSystemClassLoader=false");

        // Suppress fork communication files by setting forkCount to 0 (run tests in-process)
        // This prevents .surefire-<random> files from being created
        // Note: Commented out to preserve test isolation - use runOrder settings instead
        // command.add("-Dsurefire.forkCount=0");
        // command.add("-Dsurefire.reuseForks=false");

        // Set java.io.tmpdir to redirect temporary files to target directory
        String tempDir = config.getTempDir();
        if (tempDir != null && !tempDir.trim().isEmpty()) {
            File tmpDir = new File(project.getBasedir(), tempDir);
            command.add("-Djava.io.tmpdir=" + tmpDir.getAbsolutePath());
        }

        // Additional properties
        Map<String, String> additionalProps = config.getAdditionalProperties();
        if (additionalProps != null && !additionalProps.isEmpty()) {
            for (Map.Entry<String, String> entry : additionalProps.entrySet()) {
                command.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
        }

        return command;
    }

    /**
     * Determine Maven executable
     * Use Maven Wrapper if available, otherwise use mvn
     *
     * @return Path to Maven executable
     */
    private String determineMavenCommand() {
        File mvnw = new File(project.getBasedir(), "mvnw");
        File mvnwCmd = new File(project.getBasedir(), "mvnw.cmd");

        if (mvnw.exists() && mvnw.canExecute()) {
            return "./mvnw";
        } else if (mvnwCmd.exists() && mvnwCmd.canExecute()) {
            return "mvnw.cmd";
        } else {
            return "mvn";
        }
    }
}
