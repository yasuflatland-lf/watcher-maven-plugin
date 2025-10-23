package com.sennproject.maven.plugin.watch.files;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Selector
 * Following t_wada's TDD principles to ensure coverage
 */
class SelectorTest {

    @TempDir
    Path tempDir;

    private MavenProject project;

    @Mock
    private Log log;

    private WatcherConfig config;
    private Selector selector;

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

        selector = new Selector(project, config, log);
    }

    @Test
    void testSelectTestsWithEmptyList() {
        // Given: Empty list (boundary value test)
        List<Path> changedFiles = Collections.emptyList();

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Empty list is returned
        assertTrue(tests.isEmpty());
        verify(log).info("No changed files detected");
    }

    @Test
    void testSelectTestsWithNullList() {
        // Given: null list (boundary value test)
        List<Path> changedFiles = null;

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Empty list is returned
        assertTrue(tests.isEmpty());
        verify(log).info("No changed files detected");
    }

    @Test
    void testSelectTestsWithSingleSourceFile() throws IOException {
        // Given: Single source file
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path sourceFile = srcDir.resolve("Calculator.java");
        Files.createFile(sourceFile);

        Path testFile = testDir.resolve("CalculatorTest.java");
        Files.createFile(testFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Corresponding test is returned
        assertFalse(tests.isEmpty());
        assertTrue(tests.contains("CalculatorTest"));
    }

    @Test
    void testSelectTestsWithPackageStructure() throws IOException {
        // Given: Source file with package structure
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path packageDir = srcDir.resolve("com/example");
        Files.createDirectories(packageDir);
        Path sourceFile = packageDir.resolve("Service.java");
        Files.createFile(sourceFile);

        Path testPackageDir = testDir.resolve("com/example");
        Files.createDirectories(testPackageDir);
        Path testFile = testPackageDir.resolve("ServiceTest.java");
        Files.createFile(testFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Fully qualified test name is returned
        assertFalse(tests.isEmpty());
        assertTrue(tests.contains("com.example.ServiceTest"));
    }

    @Test
    void testSelectTestsFiltersNonExistingTests() throws IOException {
        // Given: Source file with no corresponding test file
        Path srcDir = config.getSourceDirectory().toPath();
        Path sourceFile = srcDir.resolve("NoTest.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Empty list is returned
        assertTrue(tests.isEmpty());
        verify(log).warn("No test classes found for changed files");
    }

    @Test
    void testSelectTestsWithMultipleSourceFiles() throws IOException {
        // Given: Multiple source files
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path source1 = srcDir.resolve("Foo.java");
        Path source2 = srcDir.resolve("Bar.java");
        Files.createFile(source1);
        Files.createFile(source2);

        Files.createFile(testDir.resolve("FooTest.java"));
        Files.createFile(testDir.resolve("BarTest.java"));

        List<Path> changedFiles = Arrays.asList(source1, source2);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Both tests are returned
        assertEquals(2, tests.size());
        assertTrue(tests.contains("FooTest"));
        assertTrue(tests.contains("BarTest"));
    }

    @Test
    void testSelectTestsIgnoresTestDirectoryChanges() throws IOException {
        // Given: File changes in test directory
        Path testDir = config.getTestSourceDirectory().toPath();
        Path testFile = testDir.resolve("SomeTest.java");
        Files.createFile(testFile);

        List<Path> changedFiles = Collections.singletonList(testFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Test file is run directly
        assertEquals(1, tests.size());
        assertEquals("SomeTest", tests.get(0));
    }

    @Test
    void testSelectTestsWithVerboseLogging() throws IOException {
        // Given: Verbose logging enabled
        config.setVerbose(true);

        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path sourceFile = srcDir.resolve("Verbose.java");
        Files.createFile(sourceFile);
        Files.createFile(testDir.resolve("VerboseTest.java"));

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        selector.selectTests(changedFiles);

        // Then: Verbose logs are output
        verify(log, atLeastOnce()).info(contains("Changed files:"));
        verify(log, atLeastOnce()).info(contains("Selected tests:"));
    }

    @Test
    void testSelectTestsWithKotlinFile() throws IOException {
        // Given: Kotlin file
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path sourceFile = srcDir.resolve("Service.kt");
        Files.createFile(sourceFile);
        Files.createFile(testDir.resolve("ServiceTest.kt"));

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Kotlin test is returned
        assertFalse(tests.isEmpty());
        assertTrue(tests.contains("ServiceTest"));
    }

    @Test
    void testSelectTestsWithDeepPackageStructure() throws IOException {
        // Given: Deep package hierarchy
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path deepPackage = srcDir.resolve("com/example/module/submodule/domain");
        Files.createDirectories(deepPackage);
        Path sourceFile = deepPackage.resolve("Entity.java");
        Files.createFile(sourceFile);

        Path testDeepPackage = testDir.resolve("com/example/module/submodule/domain");
        Files.createDirectories(testDeepPackage);
        Files.createFile(testDeepPackage.resolve("EntityTest.java"));

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Deep package test is returned
        assertFalse(tests.isEmpty());
        assertTrue(tests.contains("com.example.module.submodule.domain.EntityTest"));
    }

    @Test
    void testExecuteTestsWithEmptyList() throws MojoExecutionException {
        // Given: Empty test list (boundary value test)
        List<String> testClasses = Collections.emptyList();

        // When: Execute tests
        boolean result = selector.executeTests(testClasses);

        // Then: true is returned (no tests means success)
        assertTrue(result);
        verify(log).info("No tests to execute");
    }

    @Test
    void testExecuteTestsWithNullList() throws MojoExecutionException {
        // Given: null list (boundary value test)
        List<String> testClasses = null;

        // When: Execute tests
        boolean result = selector.executeTests(testClasses);

        // Then: true is returned
        assertTrue(result);
        verify(log).info("No tests to execute");
    }

    @Test
    void testConfigurationWithAllParameters() {
        // Given: All parameters set
        config.setRerunFailingTestsCount(3);
        config.setRunOrder("filesystem");
        config.setParallel(true);
        config.setThreadCount(4);
        config.setSkipAfterFailureCount(5);

        // When/Then: Configuration is correctly maintained
        assertEquals(3, config.getRerunFailingTestsCount());
        assertEquals("filesystem", config.getRunOrder());
        assertTrue(config.isParallel());
        assertEquals(4, config.getThreadCount());
        assertEquals(5, config.getSkipAfterFailureCount());
    }

    @Test
    void testConfigurationBoundaryValues() {
        // Given: Boundary value configuration
        // When: Set minimum values
        config.setRerunFailingTestsCount(0);
        config.setThreadCount(1);
        config.setSkipAfterFailureCount(0);

        // Then: Boundary values are correctly set
        assertEquals(0, config.getRerunFailingTestsCount());
        assertEquals(1, config.getThreadCount());
        assertEquals(0, config.getSkipAfterFailureCount());

        // When: Set maximum values
        config.setRerunFailingTestsCount(Integer.MAX_VALUE);
        config.setThreadCount(Integer.MAX_VALUE);

        // Then: Maximum values are correctly set
        assertEquals(Integer.MAX_VALUE, config.getRerunFailingTestsCount());
        assertEquals(Integer.MAX_VALUE, config.getThreadCount());
    }

    @Test
    void testMultipleTestSuffixPatterns() throws IOException {
        // Given: Test files with different suffix patterns
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path sourceFile = srcDir.resolve("Integration.java");
        Files.createFile(sourceFile);

        // Create test files with multiple suffix patterns
        Files.createFile(testDir.resolve("IntegrationTest.java"));
        Files.createFile(testDir.resolve("IntegrationIT.java"));

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Tests matching multiple patterns are returned
        assertTrue(tests.size() >= 2);
        assertTrue(tests.contains("IntegrationTest"));
        assertTrue(tests.contains("IntegrationIT"));
    }

    @Test
    void testSelectTestsRemovesDuplicates() throws IOException {
        // Given: Potentially duplicate changed files
        Path srcDir = config.getSourceDirectory().toPath();
        Path testDir = config.getTestSourceDirectory().toPath();

        Path sourceFile = srcDir.resolve("Duplicate.java");
        Files.createFile(sourceFile);
        Files.createFile(testDir.resolve("DuplicateTest.java"));

        // Pass the same file multiple times
        List<Path> changedFiles = Arrays.asList(sourceFile, sourceFile);

        // When: Select tests
        List<String> tests = selector.selectTests(changedFiles);

        // Then: Duplicates are removed
        assertEquals(1, tests.stream().filter(t -> t.equals("DuplicateTest")).count());
    }

    // Helper method to call private buildMavenCommand method using reflection
    @SuppressWarnings("unchecked")
    private List<String> buildMavenCommand(Selector selector, List<String> testClasses) throws Exception {
        Method method = Selector.class.getDeclaredMethod("buildMavenCommand", List.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(selector, testClasses);
    }

    @Test
    void testBuildMavenCommandWithGroups() throws Exception {
        // Given: Config with groups set
        config.setGroups("WIP");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: groups parameter is included
        assertTrue(command.contains("-Dgroups=WIP"));
    }

    @Test
    void testBuildMavenCommandWithExcludedGroups() throws Exception {
        // Given: Config with excludedGroups set
        config.setExcludedGroups("slow");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: excludedGroups parameter is included
        assertTrue(command.contains("-DexcludedGroups=slow"));
    }

    @Test
    void testBuildMavenCommandWithBothGroupsAndExcludedGroups() throws Exception {
        // Given: Config with both groups and excludedGroups set
        config.setGroups("unit");
        config.setExcludedGroups("integration");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Both parameters are included
        assertTrue(command.contains("-Dgroups=unit"));
        assertTrue(command.contains("-DexcludedGroups=integration"));
    }

    @Test
    void testBuildMavenCommandWithoutGroupsParameter() throws Exception {
        // Given: Config without groups (boundary value test - null)
        config.setGroups(null);
        config.setExcludedGroups(null);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: groups parameters are not included
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("-Dgroups=")));
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("-DexcludedGroups=")));
    }

    @Test
    void testBuildMavenCommandWithEmptyGroupsParameter() throws Exception {
        // Given: Config with empty groups (boundary value test - empty string)
        config.setGroups("   ");
        config.setExcludedGroups("");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: groups parameters are not included (trimmed empty strings are excluded)
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("-Dgroups=")));
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("-DexcludedGroups=")));
    }

    @Test
    void testBuildMavenCommandWithComplexTagExpression() throws Exception {
        // Given: Config with complex tag expression
        config.setGroups("(unit | integration) & !slow");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Complex expression is included correctly
        assertTrue(command.contains("-Dgroups=(unit | integration) & !slow"));
    }

    @Test
    void testBuildMavenCommandWithMultipleTags() throws Exception {
        // Given: Config with multiple tags
        config.setGroups("WIP,fast,unit");
        config.setExcludedGroups("slow,integration");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Multiple tags are included
        assertTrue(command.contains("-Dgroups=WIP,fast,unit"));
        assertTrue(command.contains("-DexcludedGroups=slow,integration"));
    }

    @Test
    void testBuildMavenCommandGroupsPositionInCommand() throws Exception {
        // Given: Config with groups set
        config.setGroups("WIP");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Verify command structure and groups position (after -Dtest parameter)
        int testParamIndex = -1;
        int groupsParamIndex = -1;

        for (int i = 0; i < command.size(); i++) {
            if (command.get(i).startsWith("-Dtest=")) {
                testParamIndex = i;
            }
            if (command.get(i).startsWith("-Dgroups=")) {
                groupsParamIndex = i;
            }
        }

        // groups should appear after -Dtest
        assertTrue(testParamIndex >= 0);
        assertTrue(groupsParamIndex >= 0);
        assertTrue(groupsParamIndex > testParamIndex);
    }

    @Test
    void testBuildMavenCommandWithWatcherGroups() throws Exception {
        // Given: Config with watcher.groups set
        config.setWatcherGroups("WIP");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: watcher.groups parameter is used
        assertTrue(command.contains("-Dgroups=WIP"));
    }

    @Test
    void testBuildMavenCommandPriorityWatcherGroupsOverGroups() throws Exception {
        // Given: Config with both watcher.groups and groups set
        config.setWatcherGroups("WIP");
        config.setGroups("unit");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: watcher.groups takes precedence
        assertTrue(command.contains("-Dgroups=WIP"));
        assertFalse(command.contains("-Dgroups=unit"));
    }

    @Test
    void testBuildMavenCommandGroupsFallback() throws Exception {
        // Given: Config with only groups set (Surefire compatible)
        config.setGroups("unit");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: groups is used as fallback
        assertTrue(command.contains("-Dgroups=unit"));
    }

    @Test
    void testBuildMavenCommandWithWatcherExcludedGroups() throws Exception {
        // Given: Config with watcher.excludedGroups set
        config.setWatcherExcludedGroups("slow");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: watcher.excludedGroups parameter is used
        assertTrue(command.contains("-DexcludedGroups=slow"));
    }

    @Test
    void testBuildMavenCommandPriorityWatcherExcludedGroupsOverExcludedGroups() throws Exception {
        // Given: Config with both watcher.excludedGroups and excludedGroups set
        config.setWatcherExcludedGroups("slow");
        config.setExcludedGroups("integration");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: watcher.excludedGroups takes precedence
        assertTrue(command.contains("-DexcludedGroups=slow"));
        assertFalse(command.contains("-DexcludedGroups=integration"));
    }

    @Test
    void testBuildMavenCommandExcludedGroupsFallback() throws Exception {
        // Given: Config with only excludedGroups set (Surefire compatible)
        config.setExcludedGroups("integration");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: excludedGroups is used as fallback
        assertTrue(command.contains("-DexcludedGroups=integration"));
    }

    @Test
    void testBuildMavenCommandWithBothWatcherAndSurefireStyle() throws Exception {
        // Given: Config with all four parameters set
        config.setWatcherGroups("WIP");
        config.setGroups("unit");
        config.setWatcherExcludedGroups("slow");
        config.setExcludedGroups("integration");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: watcher.* parameters take precedence
        assertTrue(command.contains("-Dgroups=WIP"));
        assertTrue(command.contains("-DexcludedGroups=slow"));
        assertFalse(command.contains("-Dgroups=unit"));
        assertFalse(command.contains("-DexcludedGroups=integration"));
    }

    // ========== Additional buildMavenCommand Tests ==========

    /**
     * Test buildMavenCommand with rerunFailingTestsCount
     */
    @Test
    void testBuildMavenCommandWithRerunFailingTestsCount() throws Exception {
        // Given: Config with rerunFailingTestsCount set
        config.setRerunFailingTestsCount(3);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: rerunFailingTestsCount parameter is included
        assertTrue(command.contains("-Dsurefire.rerunFailingTestsCount=3"));
    }

    /**
     * Test buildMavenCommand with rerunFailingTestsCount boundary (0)
     */
    @Test
    void testBuildMavenCommandWithRerunFailingTestsCountZero() throws Exception {
        // Given: Config with rerunFailingTestsCount = 0 (boundary value)
        config.setRerunFailingTestsCount(0);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: rerunFailingTestsCount parameter should NOT be included
        assertFalse(command.stream().anyMatch(arg -> arg.contains("rerunFailingTestsCount")));
    }

    /**
     * Test buildMavenCommand with runOrder
     */
    @Test
    void testBuildMavenCommandWithRunOrder() throws Exception {
        // Given: Config with runOrder set
        config.setRunOrder("alphabetical");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: runOrder parameter is included with memorystatus disabled
        assertTrue(command.contains("-Dsurefire.runOrder=alphabetical"));
        assertTrue(command.contains("-Dsurefire.runOrder.memorystatus=false"));
    }

    /**
     * Test buildMavenCommand with runOrder=balanced (generates warning)
     */
    @Test
    void testBuildMavenCommandWithRunOrderBalanced() throws Exception {
        // Given: Config with runOrder set to 'balanced' (verbose mode to see warnings)
        config.setRunOrder("balanced");
        config.setVerbose(true);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: runOrder parameter is included with memorystatus disabled
        assertTrue(command.contains("-Dsurefire.runOrder=balanced"));
        assertTrue(command.contains("-Dsurefire.runOrder.memorystatus=false"));
        // Verify warning was logged (through mock)
        verify(log).warn(contains("generates statistics files"));
    }

    /**
     * Test buildMavenCommand with runOrder=failedfirst (generates warning)
     */
    @Test
    void testBuildMavenCommandWithRunOrderFailedFirst() throws Exception {
        // Given: Config with runOrder set to 'failedfirst' (verbose mode to see
        // warnings)
        config.setRunOrder("failedfirst");
        config.setVerbose(true);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: runOrder parameter is included with memorystatus disabled
        assertTrue(command.contains("-Dsurefire.runOrder=failedfirst"));
        assertTrue(command.contains("-Dsurefire.runOrder.memorystatus=false"));
        // Verify warning was logged (through mock)
        verify(log).warn(contains("generates statistics files"));
    }

    /**
     * Test buildMavenCommand with null runOrder (boundary value)
     */
    @Test
    void testBuildMavenCommandWithNullRunOrder() throws Exception {
        // Given: Config with null runOrder (boundary value)
        config.setRunOrder(null);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: runOrder parameter should NOT be included
        assertFalse(command.stream().anyMatch(arg -> arg.contains("runOrder")));
    }

    /**
     * Test buildMavenCommand with empty runOrder (boundary value)
     */
    @Test
    void testBuildMavenCommandWithEmptyRunOrder() throws Exception {
        // Given: Config with empty runOrder (boundary value)
        config.setRunOrder("");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: runOrder parameter should NOT be included
        assertFalse(command.stream().anyMatch(arg -> arg.contains("runOrder")));
    }

    /**
     * Test buildMavenCommand with parallel execution
     */
    @Test
    void testBuildMavenCommandWithParallelExecution() throws Exception {
        // Given: Config with parallel execution enabled
        config.setParallel(true);
        config.setThreadCount(4);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: parallel and threadCount parameters are included
        assertTrue(command.contains("-Dparallel=methods"));
        assertTrue(command.contains("-DthreadCount=4"));
    }

    /**
     * Test buildMavenCommand without parallel execution (boundary)
     */
    @Test
    void testBuildMavenCommandWithoutParallelExecution() throws Exception {
        // Given: Config with parallel execution disabled (boundary value)
        config.setParallel(false);
        config.setThreadCount(4); // threadCount is set but parallel is false

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: parallel parameters should NOT be included
        assertFalse(command.stream().anyMatch(arg -> arg.contains("parallel")));
        assertFalse(command.stream().anyMatch(arg -> arg.contains("threadCount")));
    }

    /**
     * Test buildMavenCommand with skipAfterFailureCount
     */
    @Test
    void testBuildMavenCommandWithSkipAfterFailureCount() throws Exception {
        // Given: Config with skipAfterFailureCount set
        config.setSkipAfterFailureCount(5);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: skipAfterFailureCount parameter is included
        assertTrue(command.contains("-Dsurefire.skipAfterFailureCount=5"));
    }

    /**
     * Test buildMavenCommand with skipAfterFailureCount boundary (0)
     */
    @Test
    void testBuildMavenCommandWithSkipAfterFailureCountZero() throws Exception {
        // Given: Config with skipAfterFailureCount = 0 (boundary value)
        config.setSkipAfterFailureCount(0);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: skipAfterFailureCount parameter should NOT be included
        assertFalse(command.stream().anyMatch(arg -> arg.contains("skipAfterFailureCount")));
    }

    /**
     * Test buildMavenCommand with additionalProperties
     */
    @Test
    void testBuildMavenCommandWithAdditionalProperties() throws Exception {
        // Given: Config with additional properties
        java.util.Map<String, String> additionalProps = new java.util.HashMap<>();
        additionalProps.put("property1", "value1");
        additionalProps.put("property2", "value2");
        config.setAdditionalProperties(additionalProps);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: additional properties are included
        assertTrue(command.contains("-Dproperty1=value1"));
        assertTrue(command.contains("-Dproperty2=value2"));
    }

    /**
     * Test buildMavenCommand with null additionalProperties (boundary)
     */
    @Test
    void testBuildMavenCommandWithNullAdditionalProperties() throws Exception {
        // Given: Config with null additionalProperties (boundary value)
        config.setAdditionalProperties(null);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Should not throw exception
        assertNotNull(command);
        assertTrue(command.contains("test"));
    }

    /**
     * Test buildMavenCommand with empty additionalProperties (boundary)
     */
    @Test
    void testBuildMavenCommandWithEmptyAdditionalProperties() throws Exception {
        // Given: Config with empty additionalProperties (boundary value)
        config.setAdditionalProperties(new java.util.HashMap<>());

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Should not include any additional properties
        assertNotNull(command);
        assertTrue(command.contains("test"));
    }

    /**
     * Test buildMavenCommand with all options enabled
     */
    @Test
    void testBuildMavenCommandWithAllOptions() throws Exception {
        // Given: Config with all options set
        config.setRerunFailingTestsCount(2);
        config.setRunOrder("failedfirst");
        config.setParallel(true);
        config.setThreadCount(8);
        config.setSkipAfterFailureCount(3);

        java.util.Map<String, String> props = new java.util.HashMap<>();
        props.put("custom", "value");
        config.setAdditionalProperties(props);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: All parameters are included
        assertTrue(command.contains("-Dsurefire.rerunFailingTestsCount=2"));
        assertTrue(command.contains("-Dsurefire.runOrder=failedfirst"));
        assertTrue(command.contains("-Dparallel=methods"));
        assertTrue(command.contains("-DthreadCount=8"));
        assertTrue(command.contains("-Dsurefire.skipAfterFailureCount=3"));
        assertTrue(command.contains("-Dsurefire.useFile=false"));
        assertTrue(command.contains("-Dsurefire.redirectTestOutputToFile=false"));
        assertTrue(command.contains("-Dsurefire.useSystemClassLoader=false"));
        // Fork parameters commented out to preserve test isolation
        // assertTrue(command.contains("-Dsurefire.forkCount=0"));
        // assertTrue(command.contains("-Dsurefire.reuseForks=false"));
        assertTrue(command.contains("-Dcustom=value"));
    }

    /**
     * Test buildMavenCommand includes useFile with default value (false)
     */
    @Test
    void testBuildMavenCommandWithDefaultUseFile() throws Exception {
        // Given: WatcherConfig with default useFile (false)
        WatcherConfig config = new WatcherConfig();
        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: useFile parameter is included with false value to suppress .surefire-*
        // files
        assertTrue(command.contains("-Dsurefire.useFile=false"));
        assertTrue(command.contains("-Dsurefire.trimStackTrace=false"));
        assertTrue(command.contains("-Dsurefire.redirectTestOutputToFile=false"));
        assertTrue(command.contains("-Dsurefire.useSystemClassLoader=false"));
        // Fork parameters commented out to preserve test isolation
        // assertTrue(command.contains("-Dsurefire.forkCount=0"));
        // assertTrue(command.contains("-Dsurefire.reuseForks=false"));
    }

    /**
     * Test buildMavenCommand includes useFile when set to true
     */
    @Test
    void testBuildMavenCommandWithUseFileTrue() throws Exception {
        // Given: WatcherConfig with useFile set to true
        config.setUseFile(true);
        List<String> testClasses = Collections.singletonList("com.example.FooTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: useFile parameter is included with true value
        assertTrue(command.contains("-Dsurefire.useFile=true"));
        assertTrue(command.contains("-Dsurefire.trimStackTrace=false"));
        assertTrue(command.contains("-Dsurefire.redirectTestOutputToFile=false"));
        assertTrue(command.contains("-Dsurefire.useSystemClassLoader=false"));
        // Fork parameters commented out to preserve test isolation
        // assertTrue(command.contains("-Dsurefire.forkCount=0"));
        // assertTrue(command.contains("-Dsurefire.reuseForks=false"));
    }

    // ========== determineMavenCommand Tests ==========

    /**
     * Helper method to call private determineMavenCommand method using reflection
     */
    private String determineMavenCommand(Selector selector) throws Exception {
        Method method = Selector.class.getDeclaredMethod("determineMavenCommand");
        method.setAccessible(true);
        return (String) method.invoke(selector);
    }

    /**
     * Test determineMavenCommand falls back to 'mvn' when no wrapper exists
     */
    @Test
    void testDetermineMavenCommandWithoutWrapper() throws Exception {
        // Given: No Maven wrapper files exist in project basedir
        // (tempDir is clean, no mvnw or mvnw.cmd)
        Selector selector = new Selector(project, config, log);

        // When: Determine Maven command
        String command = determineMavenCommand(selector);

        // Then: Should fall back to 'mvn'
        assertEquals("mvn", command);
    }

    /**
     * Test determineMavenCommand uses mvnw when available (Unix)
     */
    @Test
    void testDetermineMavenCommandWithMvnwWrapper() throws Exception {
        // Given: mvnw exists and is executable
        Path mvnw = tempDir.resolve("mvnw");
        Files.createFile(mvnw);
        mvnw.toFile().setExecutable(true);

        // When: Determine Maven command
        Selector selector = new Selector(project, config, log);
        String command = determineMavenCommand(selector);

        // Then: Should use ./mvnw
        assertEquals("./mvnw", command);
    }

    /**
     * Test determineMavenCommand uses mvnw.cmd when available (Windows)
     */
    @Test
    void testDetermineMavenCommandWithMvnwCmdWrapper() throws Exception {
        // Given: mvnw.cmd exists and is executable (but not mvnw)
        Path mvnwCmd = tempDir.resolve("mvnw.cmd");
        Files.createFile(mvnwCmd);
        mvnwCmd.toFile().setExecutable(true);

        // When: Determine Maven command
        Selector selector = new Selector(project, config, log);
        String command = determineMavenCommand(selector);

        // Then: Should use mvnw.cmd
        assertEquals("mvnw.cmd", command);
    }

    /**
     * Test determineMavenCommand prefers mvnw over mvnw.cmd
     */
    @Test
    void testDetermineMavenCommandPrefersMvnw() throws Exception {
        // Given: Both mvnw and mvnw.cmd exist
        Path mvnw = tempDir.resolve("mvnw");
        Path mvnwCmd = tempDir.resolve("mvnw.cmd");
        Files.createFile(mvnw);
        Files.createFile(mvnwCmd);
        mvnw.toFile().setExecutable(true);
        mvnwCmd.toFile().setExecutable(true);

        // When: Determine Maven command
        Selector selector = new Selector(project, config, log);
        String command = determineMavenCommand(selector);

        // Then: Should prefer ./mvnw (Unix)
        assertEquals("./mvnw", command);
    }

    /**
     * Test determineMavenCommand requires executable permission
     */
    @Test
    void testDetermineMavenCommandRequiresExecutablePermission() throws Exception {
        // Given: mvnw exists but is NOT executable
        Path mvnw = tempDir.resolve("mvnw");
        Files.createFile(mvnw);
        mvnw.toFile().setExecutable(false);

        // When: Determine Maven command
        Selector selector = new Selector(project, config, log);
        String command = determineMavenCommand(selector);

        // Then: Should fall back to 'mvn' (not executable)
        assertEquals("mvn", command);
    }

    // ========== executeTests Exception Tests ==========

    /**
     * Test executeTests logs error when tests fail (returns false)
     * This is an integration-style test that actually attempts to run Maven
     */
    @Test
    void testExecuteTestsReturnsFalseWhenTestsFail() throws Exception {
        // Given: Non-existent test class (Maven will fail)
        List<String> testClasses = Collections.singletonList("com.nonexistent.FakeTest");

        // When: Execute tests
        boolean result = selector.executeTests(testClasses);

        // Then: Should return false (tests failed)
        assertFalse(result);

        // Verify error log was called
        verify(log, atLeastOnce()).error(contains("Tests failed"));
    }

    // ========== java.io.tmpdir Tests ==========

    /**
     * Test buildMavenCommand includes -Djava.io.tmpdir parameter
     */
    @Test
    void testBuildMavenCommandIncludesJavaIoTmpdir() throws Exception {
        // Given: Config with default tempDir
        config.setTempDir("target/tmp");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.ExampleTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: -Djava.io.tmpdir parameter is included with absolute path
        File expectedTmpDir = new File(project.getBasedir(), "target/tmp");
        String expectedParam = "-Djava.io.tmpdir=" + expectedTmpDir.getAbsolutePath();

        assertTrue(command.contains(expectedParam),
                "Command should contain -Djava.io.tmpdir parameter. Command: " + command);
        assertTrue(command.contains("-Dsurefire.tempDir=none"),
                "Command should also contain -Dsurefire.tempDir=none");
    }

    /**
     * Test buildMavenCommand with custom tempDir
     */
    @Test
    void testBuildMavenCommandWithCustomTempDir() throws Exception {
        // Given: Config with custom tempDir
        config.setTempDir("custom/temp");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.ExampleTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: -Djava.io.tmpdir parameter uses custom path
        File expectedTmpDir = new File(project.getBasedir(), "custom/temp");
        String expectedParam = "-Djava.io.tmpdir=" + expectedTmpDir.getAbsolutePath();

        assertTrue(command.contains(expectedParam),
                "Command should contain custom -Djava.io.tmpdir parameter. Command: " + command);
    }

    /**
     * Test buildMavenCommand with null tempDir (boundary value)
     */
    @Test
    void testBuildMavenCommandWithNullTempDir() throws Exception {
        // Given: Config with null tempDir (boundary value test)
        config.setTempDir(null);

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.ExampleTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Should handle null gracefully (no java.io.tmpdir parameter)
        boolean hasJavaIoTmpdir = command.stream()
                .anyMatch(arg -> arg.startsWith("-Djava.io.tmpdir="));

        assertFalse(hasJavaIoTmpdir,
                "Command should not contain -Djava.io.tmpdir when tempDir is null");
    }

    /**
     * Test buildMavenCommand with empty tempDir (boundary value)
     */
    @Test
    void testBuildMavenCommandWithEmptyTempDir() throws Exception {
        // Given: Config with empty tempDir (boundary value test)
        config.setTempDir("");

        Selector selector = new Selector(project, config, log);
        List<String> testClasses = Collections.singletonList("com.example.ExampleTest");

        // When: Build Maven command
        List<String> command = buildMavenCommand(selector, testClasses);

        // Then: Should handle empty string gracefully (no java.io.tmpdir parameter)
        boolean hasJavaIoTmpdir = command.stream()
                .anyMatch(arg -> arg.startsWith("-Djava.io.tmpdir="));

        assertFalse(hasJavaIoTmpdir,
                "Command should not contain -Djava.io.tmpdir when tempDir is empty");
    }
}
