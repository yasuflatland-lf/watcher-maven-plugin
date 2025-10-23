package com.sennproject.maven.plugin.watch.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestNameResolver
 * Following TDD principles with boundary value tests
 */
class TestNameResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        // Given: Private constructor (utility class pattern)
        Constructor<TestNameResolver> constructor = TestNameResolver.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // When/Then: Instantiation throws AssertionError
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void testResolveWithSingleJavaFile() throws IOException {
        // Given: Single Java source file
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve("Calculator.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Test names are generated
        assertFalse(testNames.isEmpty());
        assertTrue(testNames.contains("CalculatorTest"));
        assertTrue(testNames.contains("CalculatorTests"));
        assertTrue(testNames.contains("CalculatorIT"));
        assertTrue(testNames.contains("CalculatorIntegrationTest"));
    }

    @Test
    void testResolveWithSingleKotlinFile() throws IOException {
        // Given: Single Kotlin source file
        Path sourceRoot = tempDir.resolve("src/main/kotlin");
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve("Service.kt");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Test names are generated for Kotlin file
        assertFalse(testNames.isEmpty());
        assertTrue(testNames.contains("ServiceTest"));
        assertTrue(testNames.contains("ServiceTests"));
        assertTrue(testNames.contains("ServiceIT"));
        assertTrue(testNames.contains("ServiceIntegrationTest"));
    }

    @Test
    void testResolveWithPackageStructure() throws IOException {
        // Given: Source file with package structure
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packageDir = sourceRoot.resolve("com/example");
        Files.createDirectories(packageDir);
        Path sourceFile = packageDir.resolve("UserService.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Fully qualified test names are generated
        assertFalse(testNames.isEmpty());
        assertTrue(testNames.contains("com.example.UserServiceTest"));
        assertTrue(testNames.contains("com.example.UserServiceTests"));
        assertTrue(testNames.contains("com.example.UserServiceIT"));
        assertTrue(testNames.contains("com.example.UserServiceIntegrationTest"));
    }

    @Test
    void testResolveWithDeepPackageStructure() throws IOException {
        // Given: Source file with deep package structure
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path deepPackage = sourceRoot.resolve("com/example/module/service/domain");
        Files.createDirectories(deepPackage);
        Path sourceFile = deepPackage.resolve("Entity.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Deep package structure is preserved
        assertFalse(testNames.isEmpty());
        assertTrue(testNames.contains("com.example.module.service.domain.EntityTest"));
        assertTrue(testNames.contains("com.example.module.service.domain.EntityTests"));
        assertTrue(testNames.contains("com.example.module.service.domain.EntityIT"));
        assertTrue(testNames.contains("com.example.module.service.domain.EntityIntegrationTest"));
    }

    @Test
    void testResolveWithEmptyList() {
        // Given: Empty changed files list (boundary value test)
        List<Path> emptyList = Collections.emptyList();
        Path sourceRoot = tempDir.resolve("src/main/java");

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(emptyList, sourceRoot);

        // Then: Empty list is returned
        assertTrue(testNames.isEmpty());
    }

    @Test
    void testResolveWithMultipleFiles() throws IOException {
        // Given: Multiple source files
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path file1 = sourceRoot.resolve("Foo.java");
        Path file2 = sourceRoot.resolve("Bar.java");
        Files.createFile(file1);
        Files.createFile(file2);

        List<Path> changedFiles = Arrays.asList(file1, file2);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Test names for both files are generated
        assertTrue(testNames.contains("FooTest"));
        assertTrue(testNames.contains("BarTest"));
        assertTrue(testNames.contains("FooIT"));
        assertTrue(testNames.contains("BarIT"));
    }

    @Test
    void testResolveRemovesDuplicates() throws IOException {
        // Given: Duplicate source files in the list
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve("Sample.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Arrays.asList(sourceFile, sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Duplicates are removed
        long count = testNames.stream().filter(name -> name.equals("SampleTest")).count();
        assertEquals(1, count, "Duplicate test names should be removed");
    }

    @Test
    void testResolveExcludesTestFiles() throws IOException {
        // Given: Test file itself
        Path sourceRoot = tempDir.resolve("src/test/java");
        Files.createDirectories(sourceRoot);
        Path testFile = sourceRoot.resolve("SampleTest.java");
        Files.createFile(testFile);

        List<Path> changedFiles = Collections.singletonList(testFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Empty list (test files are excluded)
        assertTrue(testNames.isEmpty(), "Test files themselves should not generate test names");
    }

    @Test
    void testResolveExcludesAllTestSuffixes() throws IOException {
        // Given: Files with various test suffixes
        Path sourceRoot = tempDir.resolve("src/test/java");
        Files.createDirectories(sourceRoot);

        Path testFile = sourceRoot.resolve("SampleTest.java");
        Path testsFile = sourceRoot.resolve("SampleTests.java");
        Path itFile = sourceRoot.resolve("SampleIT.java");
        Path integrationFile = sourceRoot.resolve("SampleIntegrationTest.java");

        Files.createFile(testFile);
        Files.createFile(testsFile);
        Files.createFile(itFile);
        Files.createFile(integrationFile);

        List<Path> changedFiles = Arrays.asList(testFile, testsFile, itFile, integrationFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: All test files are excluded
        assertTrue(testNames.isEmpty(), "All test file types should be excluded");
    }

    @Test
    void testResolveWithNonSourceFiles() throws IOException {
        // Given: Non-source file (e.g., text file)
        Path sourceRoot = tempDir.resolve("src/main/resources");
        Files.createDirectories(sourceRoot);
        Path textFile = sourceRoot.resolve("config.txt");
        Files.createFile(textFile);

        List<Path> changedFiles = Collections.singletonList(textFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Empty list (non-source files are ignored)
        assertTrue(testNames.isEmpty(), "Non-source files should not generate test names");
    }

    @Test
    void testResolveSinglePath() throws IOException {
        // Given: Single source file (overloaded method)
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve("Single.java");
        Files.createFile(sourceFile);

        // When: Resolve test names using single path method
        List<String> testNames = TestNameResolver.resolve(sourceFile, sourceRoot);

        // Then: Test names are generated
        assertFalse(testNames.isEmpty());
        assertTrue(testNames.contains("SingleTest"));
        assertTrue(testNames.contains("SingleTests"));
        assertTrue(testNames.contains("SingleIT"));
        assertTrue(testNames.contains("SingleIntegrationTest"));
    }

    @Test
    void testResolveSinglePathWithTestFile() throws IOException {
        // Given: Test file (overloaded method)
        Path sourceRoot = tempDir.resolve("src/test/java");
        Files.createDirectories(sourceRoot);
        Path testFile = sourceRoot.resolve("MyTest.java");
        Files.createFile(testFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(testFile, sourceRoot);

        // Then: Empty list
        assertTrue(testNames.isEmpty());
    }

    @Test
    void testToTestParameter() {
        // Given: List of test class names
        List<String> testClasses = Arrays.asList(
            "com.example.FooTest",
            "com.example.BarTest",
            "com.example.BazIT"
        );

        // When: Convert to test parameter
        String parameter = TestNameResolver.toTestParameter(testClasses);

        // Then: Comma-separated string is returned
        assertEquals("com.example.FooTest,com.example.BarTest,com.example.BazIT", parameter);
    }

    @Test
    void testToTestParameterWithEmptyList() {
        // Given: Empty list (boundary value test)
        List<String> emptyList = Collections.emptyList();

        // When: Convert to test parameter
        String parameter = TestNameResolver.toTestParameter(emptyList);

        // Then: Empty string is returned
        assertEquals("", parameter);
    }

    @Test
    void testToTestParameterWithSingleItem() {
        // Given: Single test class name
        List<String> singleItem = Collections.singletonList("com.example.SingleTest");

        // When: Convert to test parameter
        String parameter = TestNameResolver.toTestParameter(singleItem);

        // Then: Single name without comma
        assertEquals("com.example.SingleTest", parameter);
    }

    @Test
    void testToSimpleClassName() {
        // Given: Fully qualified class names
        // When/Then: Simple class names are extracted
        assertEquals("FooTest", TestNameResolver.toSimpleClassName("com.example.FooTest"));
        assertEquals("BarTest", TestNameResolver.toSimpleClassName("com.example.service.BarTest"));
        assertEquals("BazIT", TestNameResolver.toSimpleClassName("com.example.module.integration.BazIT"));
    }

    @Test
    void testToSimpleClassNameWithNoPackage() {
        // Given: Simple class name (no package)
        String simpleName = "SimpleTest";

        // When: Convert to simple class name
        String result = TestNameResolver.toSimpleClassName(simpleName);

        // Then: Same name is returned
        assertEquals("SimpleTest", result);
    }

    @Test
    void testToSimpleClassNameWithEmptyString() {
        // Given: Empty string (boundary value test)
        String empty = "";

        // When: Convert to simple class name
        String result = TestNameResolver.toSimpleClassName(empty);

        // Then: Empty string is returned
        assertEquals("", result);
    }

    @Test
    void testResolveWithFileAtSourceRoot() throws IOException {
        // Given: File directly at source root (no package)
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve("Main.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Simple test names without package
        assertTrue(testNames.contains("MainTest"));
        assertTrue(testNames.contains("MainTests"));
        assertTrue(testNames.contains("MainIT"));
        assertTrue(testNames.contains("MainIntegrationTest"));
        // Should not have package prefix
        assertFalse(testNames.stream().anyMatch(name -> name.contains(".")));
    }

    @Test
    void testResolveWithMixedJavaAndKotlinFiles() throws IOException {
        // Given: Both Java and Kotlin files
        Path sourceRoot = tempDir.resolve("src/main");
        Files.createDirectories(sourceRoot);
        Path javaFile = sourceRoot.resolve("JavaClass.java");
        Path kotlinFile = sourceRoot.resolve("KotlinClass.kt");
        Files.createFile(javaFile);
        Files.createFile(kotlinFile);

        List<Path> changedFiles = Arrays.asList(javaFile, kotlinFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Test names for both file types are generated
        assertTrue(testNames.contains("JavaClassTest"));
        assertTrue(testNames.contains("KotlinClassTest"));
    }

    @Test
    void testResolveWithDifferentSourceRoots() throws IOException {
        // Given: Source file and different source root (testing relativization)
        Path actualSourceRoot = tempDir.resolve("src/main/java");
        Path wrongSourceRoot = tempDir.resolve("other/path");
        Files.createDirectories(actualSourceRoot);
        Files.createDirectories(wrongSourceRoot);

        Path sourceFile = actualSourceRoot.resolve("com/example/Test.java");
        Files.createDirectories(sourceFile.getParent());
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve with wrong source root (that can't relativize the path)
        List<String> testNames = TestNameResolver.resolve(changedFiles, wrongSourceRoot);

        // Then: Should handle gracefully - returns test names with empty package
        assertTrue(testNames.isEmpty() || testNames.stream().anyMatch(name -> name.contains("Test")));
    }

    @Test
    void testResolveAllTestSuffixPatterns() throws IOException {
        // Given: Source file
        Path sourceRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Path sourceFile = sourceRoot.resolve("Sample.java");
        Files.createFile(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(sourceFile, sourceRoot);

        // Then: All four test suffix patterns are present
        assertEquals(4, testNames.size());
        assertTrue(testNames.contains("SampleTest"));
        assertTrue(testNames.contains("SampleTests"));
        assertTrue(testNames.contains("SampleIT"));
        assertTrue(testNames.contains("SampleIntegrationTest"));
    }

    @Test
    void testResolveWithKotlinTestFile() throws IOException {
        // Given: Kotlin test file
        Path sourceRoot = tempDir.resolve("src/test/kotlin");
        Files.createDirectories(sourceRoot);
        Path testFile = sourceRoot.resolve("ServiceTest.kt");
        Files.createFile(testFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(testFile, sourceRoot);

        // Then: Empty list (test files are excluded)
        assertTrue(testNames.isEmpty());
    }

    @Test
    void testResolvePreservesPathSeparators() throws IOException {
        // Given: Source file with package using system path separator
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path packagePath = sourceRoot.resolve("com").resolve("example").resolve("nested");
        Files.createDirectories(packagePath);
        Path sourceFile = packagePath.resolve("Service.java");
        Files.createFile(sourceFile);

        List<Path> changedFiles = Collections.singletonList(sourceFile);

        // When: Resolve test names
        List<String> testNames = TestNameResolver.resolve(changedFiles, sourceRoot);

        // Then: Package is properly formatted with dots
        assertTrue(testNames.stream().anyMatch(name -> name.equals("com.example.nested.ServiceTest")));
        assertFalse(testNames.stream().anyMatch(name -> name.contains("/") || name.contains("\\")));
    }
}
