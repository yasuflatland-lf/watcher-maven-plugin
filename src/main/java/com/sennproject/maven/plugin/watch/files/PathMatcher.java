package com.sennproject.maven.plugin.watch.files;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for file path matching
 * Filters files using Glob patterns or regular expressions
 */
public class PathMatcher {
    private final List<String> includes;
    private final List<String> excludes;
    private final List<java.nio.file.PathMatcher> includeMatchers;
    private final List<java.nio.file.PathMatcher> excludeMatchers;

    /**
     * Constructor
     */
    public PathMatcher(List<String> includes, List<String> excludes) {
        this.includes = includes != null ? includes : Collections.emptyList();
        this.excludes = excludes != null ? excludes : Collections.emptyList();

        // Initialize PathMatchers
        this.includeMatchers = this.includes.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher(toGlobSyntax(pattern)))
                .collect(Collectors.toList());

        this.excludeMatchers = this.excludes.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher(toGlobSyntax(pattern)))
                .collect(Collectors.toList());
    }

    /**
     * Determine if path matches conditions
     *
     * Matching logic:
     * 1. If matches excludes, exclude (false)
     * 2. If includes is empty, include all (true)
     * 3. Only include if matches includes (true)
     */
    public boolean matches(Path path) {
        // Exclude if matches exclude patterns
        for (java.nio.file.PathMatcher matcher : excludeMatchers) {
            if (matcher.matches(path)) {
                return false;
            }
        }

        // Include all if no include patterns specified
        if (includeMatchers.isEmpty()) {
            return true;
        }

        // Include only if matches include patterns
        for (java.nio.file.PathMatcher matcher : includeMatchers) {
            if (matcher.matches(path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determine if directory matches exclusion patterns
     *
     * This method checks if a directory should be excluded from watching.
     * It tries both the directory path as-is and with a trailing slash
     * to support directory-specific patterns like target or node_modules
     *
     * @param directoryPath The directory path to check
     * @return true if the directory matches any exclude pattern, false otherwise
     */
    public boolean matchesDirectory(Path directoryPath) {
        // Check if the directory itself matches exclude patterns
        for (java.nio.file.PathMatcher matcher : excludeMatchers) {
            if (matcher.matches(directoryPath)) {
                return true;
            }
        }

        // Also check with trailing slash for directory patterns
        String pathWithSlash = directoryPath.toString() + "/";
        Path pathWithTrailingSlash = directoryPath.getFileSystem().getPath(pathWithSlash);
        for (java.nio.file.PathMatcher matcher : excludeMatchers) {
            if (matcher.matches(pathWithTrailingSlash)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Convert pattern to glob syntax
     * Use as-is if already starts with glob: or regex:
     */
    private String toGlobSyntax(String pattern) {
        if (pattern.startsWith("glob:") || pattern.startsWith("regex:")) {
            return pattern;
        }
        return "glob:" + pattern;
    }

    // Factory methods

    /**
     * Create PathMatcher for default Java/Kotlin source files
     */
    public static PathMatcher forTargetCode() {
        return new PathMatcher(
            Arrays.asList("**/*.java", "**/*.kt"),
            Collections.emptyList()
        );
    }

    /**
     * Create PathMatcher with custom patterns
     */
    public static PathMatcher of(List<String> includes, List<String> excludes) {
        return new PathMatcher(includes, excludes);
    }

}
