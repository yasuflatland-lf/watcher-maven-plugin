package com.sennproject.maven.plugin.watch.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility to infer test class names from source files
 * Generates test names based on naming conventions
 */
public class TestNameResolver {
    // Test class suffix patterns
    private static final List<String> TEST_SUFFIXES = Arrays.asList("Test", "Tests", "IT", "IntegrationTest");

    // Prevent instantiation as this is a utility class
    private TestNameResolver() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Generate list of test class names to execute from changed files
     * @param changedFiles List of changed files
     * @param sourceRoot Source root directory
     * @return List of fully qualified test class names
     */
    public static List<String> resolve(List<Path> changedFiles, Path sourceRoot) {
        return changedFiles.stream()
                .filter(TestNameResolver::isSourceFile)
                .flatMap(file -> generateTestNames(file, sourceRoot).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Generate list of test class names to execute from a single file
     * @param sourcePath Source file path
     * @param sourceRoot Source root directory
     * @return List of fully qualified test class names
     */
    public static List<String> resolve(Path sourcePath, Path sourceRoot) {
        if (!isSourceFile(sourcePath)) {
            return Collections.emptyList();
        }
        return generateTestNames(sourcePath, sourceRoot);
    }

    /**
     * Generate all possible test class names from source file
     */
    private static List<String> generateTestNames(Path sourcePath, Path sourceRoot) {
        String packageName = extractPackageName(sourcePath, sourceRoot);
        String className = extractClassName(sourcePath);

        List<String> testNames = new ArrayList<>();

        // Suffix patterns: FooTest, FooTests, FooIT, FooIntegrationTest
        for (String suffix : TEST_SUFFIXES) {
            String testClassName = className + suffix;
            if (!packageName.isEmpty()) {
                testNames.add(packageName + "." + testClassName);
            } else {
                testNames.add(testClassName);
            }
        }

        return testNames;
    }

    /**
     * Extract class name from file path
     * Example: /path/to/Foo.java -> Foo
     */
    private static String extractClassName(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".java")) {
            return fileName.substring(0, fileName.length() - 5);
        } else if (fileName.endsWith(".kt")) {
            return fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }

    /**
     * Extract package name from source file
     * Example: /project/src/main/java/com/example/Foo.java -> com.example
     */
    private static String extractPackageName(Path sourcePath, Path sourceRoot) {
        Path parent = sourcePath.getParent();
        if (parent == null) {
            return "";
        }

        try {
            Path relativePath = sourceRoot.relativize(parent);
            return relativePath.toString().replace('/', '.');
        } catch (IllegalArgumentException e) {
            // When sourceRoot and sourcePath are on different file systems
            return "";
        }
    }

    /**
     * Determine if path is a source file
     * Excludes test files themselves
     */
    private static boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString();

        // Exclude test files
        for (String suffix : TEST_SUFFIXES) {
            if (fileName.endsWith(suffix + ".java") || fileName.endsWith(suffix + ".kt")) {
                return false;
            }
        }

        // Only Java or Kotlin files
        return fileName.endsWith(".java") || fileName.endsWith(".kt");
    }

    /**
     * Generate file name pattern from test class names
     * Format used by Surefire's -Dtest parameter
     */
    public static String toTestParameter(List<String> testClasses) {
        return String.join(",", testClasses);
    }

    /**
     * Convert fully qualified class name to simple class name
     */
    public static String toSimpleClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot == -1) {
            return fullyQualifiedName;
        }
        return fullyQualifiedName.substring(lastDot + 1);
    }
}
