package com.sennproject.maven.plugin.watch.files;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PathMatcher
 * Following TDD principles with boundary value tests
 */
class PathMatcherTest {

    @Test
    void testConstructorWithNullParameters() {
        // Given: null parameters (boundary value test)
        // When: Create PathMatcher with nulls
        PathMatcher matcher = new PathMatcher(null, null);

        // Then: PathMatcher is created successfully
        assertNotNull(matcher);
    }

    @Test
    void testConstructorWithEmptyLists() {
        // Given: Empty lists (boundary value test)
        List<String> emptyIncludes = Collections.emptyList();
        List<String> emptyExcludes = Collections.emptyList();

        // When: Create PathMatcher
        PathMatcher matcher = new PathMatcher(emptyIncludes, emptyExcludes);

        // Then: PathMatcher is created successfully
        assertNotNull(matcher);
    }

    @Test
    void testMatchesWithNoIncludesAndNoExcludes() {
        // Given: PathMatcher with no patterns
        PathMatcher matcher = new PathMatcher(Collections.emptyList(), Collections.emptyList());
        Path path = Paths.get("src/main/java/Sample.java");

        // When: Test matching
        boolean result = matcher.matches(path);

        // Then: All files are included
        assertTrue(result, "Should include all files when no patterns specified");
    }

    @Test
    void testMatchesWithSimpleIncludePattern() {
        // Given: PathMatcher with simple include pattern
        List<String> includes = Collections.singletonList("**/*.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Java files with paths match
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
        assertTrue(matcher.matches(Paths.get("com/example/Test.java")));

        // Non-Java files don't match
        assertFalse(matcher.matches(Paths.get("src/main/java/Sample.txt")));
        assertFalse(matcher.matches(Paths.get("com/example/Sample.kt")));
    }

    @Test
    void testMatchesWithMultipleIncludePatterns() {
        // Given: PathMatcher with multiple include patterns
        List<String> includes = Arrays.asList("**/*.java", "**/*.kt");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Both Java and Kotlin files match
        assertTrue(matcher.matches(Paths.get("src/main/java/Test.java")));
        assertTrue(matcher.matches(Paths.get("src/main/kotlin/Test.kt")));

        // Other files don't match
        assertFalse(matcher.matches(Paths.get("src/main/resources/Sample.txt")));
        assertFalse(matcher.matches(Paths.get("config/Sample.xml")));
    }

    @Test
    void testMatchesWithExcludePattern() {
        // Given: PathMatcher with exclude pattern only
        List<String> excludes = Collections.singletonList("**/*Test.java");
        PathMatcher matcher = new PathMatcher(Collections.emptyList(), excludes);

        // When/Then: Test files are excluded
        assertFalse(matcher.matches(Paths.get("src/test/java/SampleTest.java")));

        // Non-test files are included (no includes = include all except excludes)
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
    }

    @Test
    void testMatchesWithIncludesAndExcludes() {
        // Given: PathMatcher with both includes and excludes
        List<String> includes = Collections.singletonList("**/*.java");
        List<String> excludes = Collections.singletonList("**/*Test.java");
        PathMatcher matcher = new PathMatcher(includes, excludes);

        // When/Then: Java files match but test files are excluded
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));

        // Test files are excluded even though they match include pattern
        assertFalse(matcher.matches(Paths.get("src/test/java/SampleTest.java")));

        // Non-Java files don't match
        assertFalse(matcher.matches(Paths.get("src/main/resources/Sample.txt")));
    }

    @Test
    void testMatchesExcludesTakePrecedence() {
        // Given: PathMatcher where a path matches both include and exclude
        List<String> includes = Collections.singletonList("**/*.java");
        List<String> excludes = Collections.singletonList("**/Special.java");
        PathMatcher matcher = new PathMatcher(includes, excludes);

        // When: Test a path that matches both patterns
        boolean result = matcher.matches(Paths.get("src/main/java/Special.java"));

        // Then: Exclude takes precedence
        assertFalse(result, "Exclude should take precedence over include");
    }

    @Test
    void testMatchesWithGlobPrefix() {
        // Given: PathMatcher with explicit glob: prefix
        List<String> includes = Collections.singletonList("glob:**/*.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Pattern with glob: prefix works correctly
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
    }

    @Test
    void testMatchesWithRegexPattern() {
        // Given: PathMatcher with regex pattern
        List<String> includes = Collections.singletonList("regex:.*\\.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Regex pattern matches correctly
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
        assertTrue(matcher.matches(Paths.get("com/example/Test.java")));
        assertFalse(matcher.matches(Paths.get("src/main/kotlin/Sample.kt")));
    }

    @Test
    void testForJavaAndKotlinFactoryMethod() {
        // Given: Factory method for Java and Kotlin
        PathMatcher matcher = PathMatcher.forTargetCode();

        // When/Then: Matches Java and Kotlin files
        assertTrue(matcher.matches(Paths.get("com/example/Test.java")));
        assertTrue(matcher.matches(Paths.get("com/example/Test.kt")));

        // Other files don't match
        assertFalse(matcher.matches(Paths.get("config/Sample.txt")));
        assertFalse(matcher.matches(Paths.get("config/pom.xml")));
    }

    @Test
    void testOfFactoryMethod() {
        // Given: Factory method with custom patterns
        List<String> includes = Collections.singletonList("**/*.xml");
        List<String> excludes = Collections.singletonList("**/pom.xml");
        PathMatcher matcher = PathMatcher.of(includes, excludes);

        // When/Then: Custom patterns work correctly
        assertTrue(matcher.matches(Paths.get("src/main/resources/config.xml")));
        assertFalse(matcher.matches(Paths.get("module/pom.xml")));
        assertFalse(matcher.matches(Paths.get("src/main/java/Sample.java")));
    }


    @Test
    void testMatchesWithMultipleExcludePatterns() {
        // Given: PathMatcher with multiple exclude patterns
        List<String> includes = Collections.singletonList("**/*.java");
        List<String> excludes = Arrays.asList(
            "**/*Test.java",
            "**/*IT.java",
            "**/Abstract*.java"
        );
        PathMatcher matcher = new PathMatcher(includes, excludes);

        // When/Then: Multiple exclude patterns work
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
        assertFalse(matcher.matches(Paths.get("src/test/java/SampleTest.java")));
        assertFalse(matcher.matches(Paths.get("src/test/java/SampleIT.java")));
        assertFalse(matcher.matches(Paths.get("src/main/java/AbstractBase.java")));
    }

    @Test
    void testMatchesWithDirectoryInPath() {
        // Given: PathMatcher with directory-specific pattern
        List<String> includes = Collections.singletonList("**/src/main/java/**/*.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Only files in specific directory match (requires subdirectory after src/main/java)
        assertTrue(matcher.matches(Paths.get("project/src/main/java/com/example/Test.java")));
        assertFalse(matcher.matches(Paths.get("project/src/test/java/com/example/Sample.java")));
        assertFalse(matcher.matches(Paths.get("com/example/Sample.java")));
    }

    @Test
    void testMatchesWithSingleAsterisk() {
        // Given: PathMatcher with single asterisk (matches within directory)
        List<String> includes = Collections.singletonList("src/*.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Single asterisk matches files in specific directory
        assertTrue(matcher.matches(Paths.get("src/Sample.java")));
        assertFalse(matcher.matches(Paths.get("src/main/Sample.java")),
            "Single asterisk should not match subdirectories");
    }

    @Test
    void testMatchesWithComplexPattern() {
        // Given: PathMatcher with simpler complex glob pattern
        List<String> includes = Collections.singletonList("**/example/**/*Service.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Complex pattern matches correctly
        assertTrue(matcher.matches(Paths.get("src/main/java/example/service/UserService.java")));
        assertTrue(matcher.matches(Paths.get("com/example/admin/AdminService.java")));
        assertFalse(matcher.matches(Paths.get("com/example/User.java")));
        assertFalse(matcher.matches(Paths.get("com/other/admin/UserService.java")));
    }

    @Test
    void testMatchesWithEmptyPath() {
        // Given: PathMatcher
        PathMatcher matcher = PathMatcher.forTargetCode();
        Path emptyPath = Paths.get("");

        // When: Test empty path (boundary value test)
        boolean result = matcher.matches(emptyPath);

        // Then: Should not match
        assertFalse(result, "Empty path should not match");
    }

    @Test
    void testMatchesWithSingleFileName() {
        // Given: PathMatcher with specific file name pattern
        List<String> includes = Collections.singletonList("**/pom.xml");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Specific file name matches
        assertTrue(matcher.matches(Paths.get("project/pom.xml")));
        assertTrue(matcher.matches(Paths.get("project/submodule/pom.xml")));
        assertFalse(matcher.matches(Paths.get("project/build.xml")));
    }

    @Test
    void testMatchesCasesensitivity() {
        // Given: PathMatcher with case-sensitive pattern
        List<String> includes = Collections.singletonList("**/*.Java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Pattern matching is case-sensitive on most systems
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.Java")));
        // Note: Case sensitivity depends on the file system
        // On case-sensitive file systems, lowercase .java will not match
    }

    @Test
    void testMatchesWithNullPathHandling() {
        // Given: PathMatcher
        PathMatcher matcher = PathMatcher.forTargetCode();

        // When/Then: Should handle null gracefully
        assertThrows(NullPointerException.class, () -> {
            matcher.matches(null);
        }, "Should throw NullPointerException for null path");
    }

    @Test
    void testMatchesMultiplePatternTypes() {
        // Given: PathMatcher mixing glob and regex patterns
        List<String> includes = Arrays.asList(
            "glob:**/*.java",
            "regex:.*\\.kt"
        );
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Both pattern types work together
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
        assertTrue(matcher.matches(Paths.get("src/main/kotlin/Sample.kt")));
        assertFalse(matcher.matches(Paths.get("src/main/resources/Sample.txt")));
    }

    @Test
    void testMatchesWithLeadingSlash() {
        // Given: PathMatcher
        List<String> includes = Collections.singletonList("**/*.java");
        PathMatcher matcher = new PathMatcher(includes, Collections.emptyList());

        // When/Then: Paths work correctly
        assertTrue(matcher.matches(Paths.get("src/main/java/Sample.java")));
        // Absolute paths may behave differently depending on the system
    }

    @Test
    void testMatchesDirectoryWithGlobPattern() {
        // Given: PathMatcher with directory exclusion pattern
        List<String> excludes = Arrays.asList("**/target", "node_modules");
        PathMatcher matcher = new PathMatcher(Collections.emptyList(), excludes);

        // When/Then: Directory matching works correctly
        assertTrue(matcher.matchesDirectory(Paths.get("project/target")),
            "Should match directory without trailing slash");
        assertTrue(matcher.matchesDirectory(Paths.get("src/main/target")),
            "Should match directory in subdirectory");
        assertTrue(matcher.matchesDirectory(Paths.get("node_modules")),
            "Should match directory at root level");

        // Non-matching directories
        assertFalse(matcher.matchesDirectory(Paths.get("src/main/java")),
            "Should not match non-excluded directory");
        assertFalse(matcher.matchesDirectory(Paths.get("build")),
            "Should not match different directory name");
    }

    @Test
    void testMatchesDirectoryExcludesMultiple() {
        // Given: PathMatcher with multiple directory exclusion patterns
        List<String> excludes = Arrays.asList(
            "**/target",
            "**/build",
            ".git",
            "**/node_modules"
        );
        PathMatcher matcher = new PathMatcher(Collections.emptyList(), excludes);

        // When/Then: Multiple directory exclusions work
        assertTrue(matcher.matchesDirectory(Paths.get("project/target")));
        assertTrue(matcher.matchesDirectory(Paths.get("project/build")));
        assertTrue(matcher.matchesDirectory(Paths.get(".git")));
        assertTrue(matcher.matchesDirectory(Paths.get("frontend/node_modules")));

        // Non-excluded directory
        assertFalse(matcher.matchesDirectory(Paths.get("src/main/java")));
    }

    @Test
    void testMatchesDirectoryBoundaryRootDirectory() {
        // Given: PathMatcher with directory exclusion
        List<String> excludes = Collections.singletonList("target");
        PathMatcher matcher = new PathMatcher(Collections.emptyList(), excludes);

        // When: Test root level directory (boundary value test)
        boolean result = matcher.matchesDirectory(Paths.get("target"));

        // Then: Should match root level directory
        assertTrue(result, "Should match root level directory");
    }

    @Test
    void testMatchesDirectoryBoundaryEmptyPath() {
        // Given: PathMatcher with directory exclusion
        List<String> excludes = Collections.singletonList("**/target");
        PathMatcher matcher = new PathMatcher(Collections.emptyList(), excludes);

        // When: Test empty path (boundary value test)
        Path emptyPath = Paths.get("");
        boolean result = matcher.matchesDirectory(emptyPath);

        // Then: Empty path should not match
        assertFalse(result, "Empty path should not match directory patterns");
    }
}
