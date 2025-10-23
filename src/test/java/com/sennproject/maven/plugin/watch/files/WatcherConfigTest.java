package com.sennproject.maven.plugin.watch.files;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WatcherConfig
 * Following TDD principles, including boundary value tests
 */
class WatcherConfigTest {

    @Test
    void testDefaultValues() {
        // Given: New WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Default values are set
        assertEquals(750, config.getDebounceMs());
        assertTrue(config.isWatchMode());
        assertTrue(config.isRecursive());
        assertEquals(0, config.getRerunFailingTestsCount());
        assertEquals("filesystem", config.getRunOrder());
        assertFalse(config.isParallel());
        assertEquals(1, config.getThreadCount());
        assertEquals(0, config.getSkipAfterFailureCount());
        assertFalse(config.isVerbose());
        assertFalse(config.isUseFile());
    }

    @Test
    void testSetAndGetSourceDirectory() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();
        File sourceDir = new File("/path/to/source");

        // When: Set sourceDirectory
        config.setSourceDirectory(sourceDir);

        // Then: Can be retrieved correctly
        assertEquals(sourceDir, config.getSourceDirectory());
    }

    @Test
    void testSetAndGetTestSourceDirectory() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();
        File testDir = new File("/path/to/test");

        // When: Set testSourceDirectory
        config.setTestSourceDirectory(testDir);

        // Then: Can be retrieved correctly
        assertEquals(testDir, config.getTestSourceDirectory());
    }

    @Test
    void testSetAndGetWatchDirectories() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();
        File dir1 = new File("/path/to/watch1");
        File dir2 = new File("/path/to/watch2");

        // When: Set watchDirectories
        config.setWatchDirectories(Arrays.asList(dir1, dir2));

        // Then: Can be retrieved correctly
        assertEquals(2, config.getWatchDirectories().size());
        assertTrue(config.getWatchDirectories().contains(dir1));
        assertTrue(config.getWatchDirectories().contains(dir2));
    }

    @Test
    void testSetAndGetDebounceMsBoundaryValues() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Boundary value tests
        // Minimum value (0)
        config.setDebounceMs(0);
        assertEquals(0, config.getDebounceMs());

        // Normal value
        config.setDebounceMs(1000);
        assertEquals(1000, config.getDebounceMs());

        // Large value
        config.setDebounceMs(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, config.getDebounceMs());
    }

    @Test
    void testSetAndGetWatchMode() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Toggle watchMode
        config.setWatchMode(false);
        assertFalse(config.isWatchMode());

        config.setWatchMode(true);
        assertTrue(config.isWatchMode());
    }

    @Test
    void testSetAndGetRecursive() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Toggle recursive
        config.setRecursive(false);
        assertFalse(config.isRecursive());

        config.setRecursive(true);
        assertTrue(config.isRecursive());
    }

    @Test
    void testSetAndGetIncludes() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set includes
        config.setIncludes(Arrays.asList("**/*.java", "**/*.kt"));

        // Then: Can be retrieved correctly
        assertEquals(2, config.getIncludes().size());
        assertTrue(config.getIncludes().contains("**/*.java"));
        assertTrue(config.getIncludes().contains("**/*.kt"));
    }

    @Test
    void testSetAndGetExcludes() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set excludes
        config.setExcludes(Collections.singletonList("**/*Test.java"));

        // Then: Can be retrieved correctly
        assertEquals(1, config.getExcludes().size());
        assertTrue(config.getExcludes().contains("**/*Test.java"));
    }

    @Test
    void testSetAndGetRerunFailingTestsCountBoundaryValues() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Boundary value tests
        // Minimum value (0)
        config.setRerunFailingTestsCount(0);
        assertEquals(0, config.getRerunFailingTestsCount());

        // Normal value
        config.setRerunFailingTestsCount(3);
        assertEquals(3, config.getRerunFailingTestsCount());

        // Large value
        config.setRerunFailingTestsCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, config.getRerunFailingTestsCount());
    }

    @Test
    void testSetAndGetRunOrder() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Set runOrder
        config.setRunOrder("alphabetical");
        assertEquals("alphabetical", config.getRunOrder());

        config.setRunOrder("random");
        assertEquals("random", config.getRunOrder());
    }

    @Test
    void testSetAndGetParallel() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Toggle parallel
        config.setParallel(true);
        assertTrue(config.isParallel());

        config.setParallel(false);
        assertFalse(config.isParallel());
    }

    @Test
    void testSetAndGetThreadCountBoundaryValues() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Boundary value tests
        // Minimum value (1)
        config.setThreadCount(1);
        assertEquals(1, config.getThreadCount());

        // Normal value
        config.setThreadCount(4);
        assertEquals(4, config.getThreadCount());

        // Large value
        config.setThreadCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, config.getThreadCount());
    }

    @Test
    void testSetAndGetSkipAfterFailureCount() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Set skipAfterFailureCount
        config.setSkipAfterFailureCount(5);
        assertEquals(5, config.getSkipAfterFailureCount());

        config.setSkipAfterFailureCount(0);
        assertEquals(0, config.getSkipAfterFailureCount());
    }

    @Test
    void testSetAndGetVerbose() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Toggle verbose
        config.setVerbose(true);
        assertTrue(config.isVerbose());

        config.setVerbose(false);
        assertFalse(config.isVerbose());
    }

    @Test
    void testEmptyCollectionsBoundaryTest() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set empty collections (boundary value test)
        config.setWatchDirectories(Collections.emptyList());
        config.setIncludes(Collections.emptyList());
        config.setExcludes(Collections.emptyList());

        // Then: Empty collections are returned
        assertTrue(config.getWatchDirectories().isEmpty());
        assertTrue(config.getIncludes().isEmpty());
        assertTrue(config.getExcludes().isEmpty());
    }

    @Test
    void testSetAndGetGroups() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set groups
        config.setGroups("WIP");

        // Then: Can be retrieved correctly
        assertEquals("WIP", config.getGroups());
    }

    @Test
    void testSetAndGetExcludedGroups() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set excludedGroups
        config.setExcludedGroups("slow,integration");

        // Then: Can be retrieved correctly
        assertEquals("slow,integration", config.getExcludedGroups());
    }

    @Test
    void testSetAndGetBothGroups() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set both groups and excludedGroups
        config.setGroups("unit");
        config.setExcludedGroups("slow");

        // Then: Both can be retrieved correctly
        assertEquals("unit", config.getGroups());
        assertEquals("slow", config.getExcludedGroups());
    }

    @Test
    void testGroupsDefaultsToNull() {
        // Given: New WatcherConfig (boundary value test)
        WatcherConfig config = new WatcherConfig();

        // When/Then: Default values are null
        assertNull(config.getGroups());
        assertNull(config.getExcludedGroups());
    }

    @Test
    void testSetGroupsToNull() {
        // Given: WatcherConfig with groups set
        WatcherConfig config = new WatcherConfig();
        config.setGroups("WIP");
        config.setExcludedGroups("slow");

        // When: Set to null (boundary value test)
        config.setGroups(null);
        config.setExcludedGroups(null);

        // Then: null is returned
        assertNull(config.getGroups());
        assertNull(config.getExcludedGroups());
    }

    @Test
    void testSetGroupsToEmptyString() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set to empty string (boundary value test)
        config.setGroups("");
        config.setExcludedGroups("");

        // Then: Empty string is returned
        assertEquals("", config.getGroups());
        assertEquals("", config.getExcludedGroups());
    }

    @Test
    void testSetGroupsWithComplexExpression() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set complex tag expression
        config.setGroups("(unit | integration) & !slow");

        // Then: Expression is stored correctly
        assertEquals("(unit | integration) & !slow", config.getGroups());
    }

    @Test
    void testSetGroupsWithMultipleTags() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set multiple tags
        config.setGroups("WIP,fast,unit");
        config.setExcludedGroups("slow,integration");

        // Then: Multiple tags are stored correctly
        assertEquals("WIP,fast,unit", config.getGroups());
        assertEquals("slow,integration", config.getExcludedGroups());
    }

    @Test
    void testSetAndGetWatcherGroups() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set watcher-specific groups
        config.setWatcherGroups("WIP");

        // Then: Can be retrieved correctly
        assertEquals("WIP", config.getWatcherGroups());
    }

    @Test
    void testSetAndGetWatcherExcludedGroups() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set watcher-specific excluded groups
        config.setWatcherExcludedGroups("slow");

        // Then: Can be retrieved correctly
        assertEquals("slow", config.getWatcherExcludedGroups());
    }

    @Test
    void testGetEffectiveGroupsWithWatcherGroupsOnly() {
        // Given: WatcherConfig with watcher.groups set
        WatcherConfig config = new WatcherConfig();
        config.setWatcherGroups("WIP");

        // When: Get effective groups
        String effective = config.getEffectiveGroups();

        // Then: watcher.groups is returned
        assertEquals("WIP", effective);
    }

    @Test
    void testGetEffectiveGroupsWithGroupsOnly() {
        // Given: WatcherConfig with groups set (Surefire compatible)
        WatcherConfig config = new WatcherConfig();
        config.setGroups("unit");

        // When: Get effective groups
        String effective = config.getEffectiveGroups();

        // Then: groups is returned (fallback)
        assertEquals("unit", effective);
    }

    @Test
    void testGetEffectiveGroupsPriorityWatcherGroupsOverGroups() {
        // Given: WatcherConfig with both watcher.groups and groups set
        WatcherConfig config = new WatcherConfig();
        config.setWatcherGroups("WIP");
        config.setGroups("unit");

        // When: Get effective groups
        String effective = config.getEffectiveGroups();

        // Then: watcher.groups takes precedence
        assertEquals("WIP", effective);
    }

    @Test
    void testGetEffectiveGroupsWithBothNull() {
        // Given: WatcherConfig with both null (boundary test)
        WatcherConfig config = new WatcherConfig();
        config.setWatcherGroups(null);
        config.setGroups(null);

        // When: Get effective groups
        String effective = config.getEffectiveGroups();

        // Then: null is returned
        assertNull(effective);
    }

    @Test
    void testGetEffectiveGroupsWithWatcherGroupsEmpty() {
        // Given: WatcherConfig with watcher.groups empty, groups set
        WatcherConfig config = new WatcherConfig();
        config.setWatcherGroups("   ");
        config.setGroups("unit");

        // When: Get effective groups
        String effective = config.getEffectiveGroups();

        // Then: groups is returned (fallback because watcher.groups is empty)
        assertEquals("unit", effective);
    }

    @Test
    void testGetEffectiveExcludedGroupsWithWatcherExcludedGroupsOnly() {
        // Given: WatcherConfig with watcher.excludedGroups set
        WatcherConfig config = new WatcherConfig();
        config.setWatcherExcludedGroups("slow");

        // When: Get effective excluded groups
        String effective = config.getEffectiveExcludedGroups();

        // Then: watcher.excludedGroups is returned
        assertEquals("slow", effective);
    }

    @Test
    void testGetEffectiveExcludedGroupsWithExcludedGroupsOnly() {
        // Given: WatcherConfig with excludedGroups set (Surefire compatible)
        WatcherConfig config = new WatcherConfig();
        config.setExcludedGroups("integration");

        // When: Get effective excluded groups
        String effective = config.getEffectiveExcludedGroups();

        // Then: excludedGroups is returned (fallback)
        assertEquals("integration", effective);
    }

    @Test
    void testGetEffectiveExcludedGroupsPriorityWatcherExcludedGroupsOverExcludedGroups() {
        // Given: WatcherConfig with both watcher.excludedGroups and excludedGroups set
        WatcherConfig config = new WatcherConfig();
        config.setWatcherExcludedGroups("slow");
        config.setExcludedGroups("integration");

        // When: Get effective excluded groups
        String effective = config.getEffectiveExcludedGroups();

        // Then: watcher.excludedGroups takes precedence
        assertEquals("slow", effective);
    }

    @Test
    void testGetEffectiveExcludedGroupsWithBothNull() {
        // Given: WatcherConfig with both null (boundary test)
        WatcherConfig config = new WatcherConfig();
        config.setWatcherExcludedGroups(null);
        config.setExcludedGroups(null);

        // When: Get effective excluded groups
        String effective = config.getEffectiveExcludedGroups();

        // Then: null is returned
        assertNull(effective);
    }

    @Test
    void testGetEffectiveExcludedGroupsWithWatcherExcludedGroupsEmpty() {
        // Given: WatcherConfig with watcher.excludedGroups empty, excludedGroups set
        WatcherConfig config = new WatcherConfig();
        config.setWatcherExcludedGroups("   ");
        config.setExcludedGroups("integration");

        // When: Get effective excluded groups
        String effective = config.getEffectiveExcludedGroups();

        // Then: excludedGroups is returned (fallback because watcher.excludedGroups is empty)
        assertEquals("integration", effective);
    }

    @Test
    void testSetAndGetUseFile() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When/Then: Toggle useFile
        config.setUseFile(true);
        assertTrue(config.isUseFile());

        config.setUseFile(false);
        assertFalse(config.isUseFile());
    }

    @Test
    void testUseFileDefaultsToFalse() {
        // Given: New WatcherConfig (boundary value test)
        WatcherConfig config = new WatcherConfig();

        // When/Then: Default value is false to suppress .surefire-* files
        assertFalse(config.isUseFile());
    }

    @Test
    void testTempDirGetterSetter() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set custom tempDir
        config.setTempDir("custom/temp");

        // Then: Can be retrieved correctly
        assertEquals("custom/temp", config.getTempDir());
    }

    @Test
    void testTempDirDefaultValue() {
        // Given & When: New WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // Then: Default value is "target/tmp"
        assertEquals("target/tmp", config.getTempDir());
    }

    @Test
    void testTempDirBoundaryNull() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set to null (boundary value test)
        config.setTempDir(null);

        // Then: null is returned
        assertNull(config.getTempDir());
    }

    @Test
    void testTempDirBoundaryEmptyString() {
        // Given: WatcherConfig
        WatcherConfig config = new WatcherConfig();

        // When: Set to empty string (boundary value test)
        config.setTempDir("");

        // Then: Empty string is returned
        assertEquals("", config.getTempDir());
    }
}
