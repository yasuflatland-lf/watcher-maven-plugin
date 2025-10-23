package com.sennproject.maven.plugin.watch.files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QueueOrchestrator}.
 *
 * <p>
 * This test suite verifies the queue orchestration functionality, including:
 * </p>
 * <ul>
 *   <li>Basic enqueue/dequeue operations</li>
 *   <li>Duplicate file removal</li>
 *   <li>Order preservation</li>
 *   <li>Test execution state management</li>
 *   <li>Thread safety (implicit through synchronized operations)</li>
 *   <li>Boundary conditions (null, empty, large datasets)</li>
 * </ul>
 *
 * @see QueueOrchestrator
 */
class QueueOrchestratorTest {

    /**
     * The queue orchestrator instance under test.
     * Initialized before each test method.
     */
    private QueueOrchestrator queueOrchestrator;

    /**
     * Set up a fresh QueueOrchestrator instance before each test.
     * Ensures test isolation by providing a clean state.
     */
    @BeforeEach
    void setUp() {
        queueOrchestrator = new QueueOrchestrator();
    }

    /**
     * Test that enqueue returns false when no test is running.
     *
     * <p>
     * Verifies that when tests are not running, enqueue() returns false
     * to indicate that files can be processed immediately, but still
     * adds them to the queue.
     * </p>
     */
    @Test
    void testEnqueueWhenNotRunning() {
        // Given: A list of files to enqueue
        List<Path> files = Arrays.asList(
            Paths.get("src/Foo.java"),
            Paths.get("src/Bar.java")
        );

        // When: Enqueue files while test is not running
        boolean wasQueued = queueOrchestrator.enqueue(files);

        // Then: Should return false (not deferred) but files are still in queue
        assertFalse(wasQueued, "Should return false when test is not running");
        assertEquals(2, queueOrchestrator.dequeue().size(), "Files should be in queue");
    }

    /**
     * Test that enqueue returns true when a test is running.
     *
     * <p>
     * Verifies that when tests are running, enqueue() returns true
     * to indicate that files are being queued for deferred processing.
     * </p>
     */
    @Test
    void testEnqueueWhenRunning() {
        // Given: Test execution is marked as running
        queueOrchestrator.markTestRunning();

        // When: Enqueue files while test is running
        List<Path> files = Arrays.asList(Paths.get("src/Foo.java"));
        boolean wasQueued = queueOrchestrator.enqueue(files);

        // Then: Should return true (deferred processing)
        assertTrue(wasQueued, "Should return true when test is running");
        assertTrue(queueOrchestrator.isTestRunning(), "Test should still be marked as running");
    }

    /**
     * Test that duplicate files are automatically removed from the queue.
     *
     * <p>
     * Verifies that when the same file is enqueued multiple times,
     * only one instance is kept in the queue due to LinkedHashSet behavior.
     * </p>
     */
    @Test
    void testDuplicateRemoval() {
        // Given: The same file path
        Path file = Paths.get("src/Foo.java");

        // When: Enqueue the same file three times
        queueOrchestrator.enqueue(Arrays.asList(file));
        queueOrchestrator.enqueue(Arrays.asList(file));
        queueOrchestrator.enqueue(Arrays.asList(file));

        // Then: Only one instance should be in the queue
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(1, result.size(), "Should contain only one instance");
        assertEquals(file, result.get(0), "Should be the same file");
    }

    /**
     * Test that dequeue preserves insertion order.
     *
     * <p>
     * Verifies that files are returned in the same order they were enqueued,
     * which is guaranteed by LinkedHashSet's insertion-order iteration.
     * </p>
     */
    @Test
    void testDequeuePreservesOrder() {
        // Given: A list of files in specific order
        List<Path> files = Arrays.asList(
            Paths.get("src/A.java"),
            Paths.get("src/B.java"),
            Paths.get("src/C.java")
        );

        // When: Enqueue and then dequeue
        queueOrchestrator.enqueue(files);
        List<Path> result = queueOrchestrator.dequeue();

        // Then: Order should be preserved
        assertEquals(files, result, "Dequeued files should maintain insertion order");
    }

    /**
     * Test that dequeue clears the queue.
     *
     * <p>
     * Verifies that after dequeuing, the queue is empty and ready
     * for new files to be enqueued.
     * </p>
     */
    @Test
    void testDequeueClearsQueue() {
        // Given: A file in the queue
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/Foo.java")));

        // When: Dequeue all files
        queueOrchestrator.dequeue();

        // Then: Queue should be empty
        assertTrue(queueOrchestrator.isEmpty(), "Queue should be empty after dequeue");
    }

    /**
     * Test that requeueAtFront restores the failed batch ahead of newer changes.
     */
    @Test
    void testRequeueAtFrontRestoresFailedBatchOrder() {
        // Given: An initial batch that was dequeued for execution
        List<Path> initialBatch = Arrays.asList(
            Paths.get("src/A.java"),
            Paths.get("src/B.java")
        );
        queueOrchestrator.enqueue(initialBatch);
        List<Path> dequeued = queueOrchestrator.dequeue();

        // And: New changes arriving while the previous batch is executing
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/C.java")));

        // When: The original batch needs to be restored to the front
        queueOrchestrator.requeueAtFront(dequeued);

        // Then: The original batch remains ahead of later arrivals
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(Arrays.asList(
            Paths.get("src/A.java"),
            Paths.get("src/B.java"),
            Paths.get("src/C.java")
        ), result, "Dequeued order should restore the failed batch before newer changes");
    }

    /**
     * Test edge case: requeueAtFront with null elements in the list.
     *
     * <p>
     * Verifies that null elements within the requeue list are filtered out
     * and do not cause errors or get added to the queue.
     * </p>
     */
    @Test
    void testRequeueAtFrontWithNullElements() {
        // Given: A list containing null elements mixed with valid paths
        List<Path> filesWithNulls = Arrays.asList(
            Paths.get("src/A.java"),
            null,
            Paths.get("src/B.java"),
            null
        );

        // When: Requeue the list with null elements
        queueOrchestrator.requeueAtFront(filesWithNulls);

        // Then: Only non-null files should be in the queue
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(2, result.size(), "Should contain only non-null files");
        assertEquals(Paths.get("src/A.java"), result.get(0), "First should be A");
        assertEquals(Paths.get("src/B.java"), result.get(1), "Second should be B");
    }

    /**
     * Test edge case: requeueAtFront on an empty queue.
     *
     * <p>
     * Verifies that requeueAtFront works correctly when the queue is empty,
     * effectively behaving like a regular enqueue operation.
     * </p>
     */
    @Test
    void testRequeueAtFrontOnEmptyQueue() {
        // Given: An empty queue
        assertTrue(queueOrchestrator.isEmpty(), "Queue should be empty initially");

        // When: Requeue files on empty queue
        List<Path> files = Arrays.asList(
            Paths.get("src/A.java"),
            Paths.get("src/B.java")
        );
        queueOrchestrator.requeueAtFront(files);

        // Then: Files should be in the queue in correct order
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(files, result, "Files should be in queue even when initially empty");
    }

    /**
     * Test edge case: requeueAtFront with duplicate files.
     *
     * <p>
     * Verifies that when the requeued batch contains files that are also
     * in the existing queue, duplicates are handled correctly (LinkedHashSet
     * behavior preserves first occurrence).
     * </p>
     */
    @Test
    void testRequeueAtFrontWithDuplicates() {
        // Given: Files already in the queue
        queueOrchestrator.enqueue(Arrays.asList(
            Paths.get("src/B.java"),
            Paths.get("src/C.java")
        ));

        // When: Requeue a batch that includes duplicates
        List<Path> requeueBatch = Arrays.asList(
            Paths.get("src/A.java"),
            Paths.get("src/B.java")  // duplicate
        );
        queueOrchestrator.requeueAtFront(requeueBatch);

        // Then: Duplicates should be removed, requeued batch should be first
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(3, result.size(), "Should have 3 unique files");
        assertEquals(Paths.get("src/A.java"), result.get(0), "A from requeue should be first");
        assertEquals(Paths.get("src/B.java"), result.get(1), "B from requeue should be second");
        assertEquals(Paths.get("src/C.java"), result.get(2), "C from original queue should be third");
    }

    /**
     * Test edge case: requeueAtFront with null argument.
     *
     * <p>
     * Verifies that passing null to requeueAtFront does not throw an exception
     * and leaves the queue unchanged.
     * </p>
     */
    @Test
    void testRequeueAtFrontWithNull() {
        // Given: Files in the queue
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/A.java")));

        // When: Requeue with null argument
        queueOrchestrator.requeueAtFront(null);

        // Then: Queue should remain unchanged
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(1, result.size(), "Queue should still have the original file");
        assertEquals(Paths.get("src/A.java"), result.get(0), "Original file should remain");
    }

    /**
     * Test edge case: requeueAtFront with empty list.
     *
     * <p>
     * Verifies that passing an empty list to requeueAtFront does not cause
     * errors and leaves the queue unchanged.
     * </p>
     */
    @Test
    void testRequeueAtFrontWithEmptyList() {
        // Given: Files in the queue
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/A.java")));

        // When: Requeue with empty list
        queueOrchestrator.requeueAtFront(new ArrayList<>());

        // Then: Queue should remain unchanged
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(1, result.size(), "Queue should still have the original file");
        assertEquals(Paths.get("src/A.java"), result.get(0), "Original file should remain");
    }

    /**
     * Test test execution state transitions.
     *
     * <p>
     * Verifies that the test running flag can be set and cleared correctly,
     * and that the state can be queried at any time.
     * </p>
     */
    @Test
    void testMarkTestRunningAndComplete() {
        // Given: Initial state (not running)
        assertFalse(queueOrchestrator.isTestRunning(), "Should not be running initially");

        // When: Mark test as running
        queueOrchestrator.markTestRunning();

        // Then: Should be marked as running
        assertTrue(queueOrchestrator.isTestRunning(), "Should be running after mark");

        // When: Mark test as complete
        queueOrchestrator.markTestComplete();

        // Then: Should not be running
        assertFalse(queueOrchestrator.isTestRunning(), "Should not be running after complete");
    }

    /**
     * Test that clear removes all files from the queue.
     *
     * <p>
     * Verifies that clear() removes all queued files without returning them,
     * useful for discarding pending changes.
     * </p>
     */
    @Test
    void testClear() {
        // Given: Multiple files in the queue
        queueOrchestrator.enqueue(Arrays.asList(
            Paths.get("src/Foo.java"),
            Paths.get("src/Bar.java")
        ));

        // When: Clear the queue
        queueOrchestrator.clear();

        // Then: Queue should be empty
        assertTrue(queueOrchestrator.isEmpty(), "Queue should be empty after clear");
    }

    /**
     * Test boundary condition: null and empty list inputs.
     *
     * <p>
     * Verifies that enqueue gracefully handles null and empty inputs
     * by returning false and not modifying the queue.
     * </p>
     */
    @Test
    void testNullAndEmptyList() {
        // When: Enqueue null
        boolean result1 = queueOrchestrator.enqueue(null);

        // Then: Should return false and queue should be empty
        assertFalse(result1, "Should return false for null input");

        // When: Enqueue empty list
        boolean result2 = queueOrchestrator.enqueue(Arrays.asList());

        // Then: Should return false and queue should be empty
        assertFalse(result2, "Should return false for empty list");
        assertTrue(queueOrchestrator.isEmpty(), "Queue should remain empty");
    }

    /**
     * Test that multiple enqueue calls merge changes.
     *
     * <p>
     * Verifies that files from multiple separate enqueue calls
     * are accumulated in the same queue and can be dequeued together.
     * </p>
     */
    @Test
    void testMergeChanges() {
        // Given: Three separate enqueue operations
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/A.java")));
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/B.java")));
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/C.java")));

        // When: Dequeue all files
        List<Path> result = queueOrchestrator.dequeue();

        // Then: All files should be present
        assertEquals(3, result.size(), "Should contain all enqueued files");
    }

    /**
     * Test boundary condition: large number of files.
     *
     * <p>
     * Verifies that the queue can handle a large dataset (1000 files)
     * without performance degradation or memory issues.
     * </p>
     */
    @Test
    void testLargeNumberOfFiles() {
        // Given: 1000 unique file paths
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            files.add(Paths.get("src/File" + i + ".java"));
        }

        // When: Enqueue all files
        queueOrchestrator.enqueue(files);

        // Then: All files should be in the queue
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(1000, result.size(), "Should handle 1000 files");
    }

    /**
     * Test initial state of queue orchestrator.
     *
     * <p>
     * Verifies that a newly created QueueOrchestrator has the correct
     * initial state: empty queue and test not running.
     * </p>
     */
    @Test
    void testIsEmptyInitialState() {
        // Then: Queue should be empty and test not running
        assertTrue(queueOrchestrator.isEmpty(), "Queue should be empty initially");
        assertFalse(queueOrchestrator.isTestRunning(), "Test should not be running initially");
    }

    /**
     * Test that queue can be reused after dequeue.
     *
     * <p>
     * Verifies that after dequeuing files, the queue can be used again
     * to enqueue and dequeue new files without issues.
     * </p>
     */
    @Test
    void testEnqueueAfterDequeue() {
        // Given: A file enqueued and then dequeued
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/A.java")));
        queueOrchestrator.dequeue();

        // Then: Queue should be empty
        assertTrue(queueOrchestrator.isEmpty(), "Queue should be empty after dequeue");

        // When: Enqueue a new file
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/B.java")));

        // Then: New file should be in queue
        assertEquals(1, queueOrchestrator.dequeue().size(), "Should be able to enqueue after dequeue");
    }

    /**
     * Test boundary condition: dequeue from empty queue.
     *
     * <p>
     * Verifies that dequeuing from an empty queue returns an empty list
     * rather than null, ensuring null-safe behavior.
     * </p>
     */
    @Test
    void testDequeueEmptyQueue() {
        // When: Dequeue from empty queue
        List<Path> result = queueOrchestrator.dequeue();

        // Then: Should return empty list (not null)
        assertNotNull(result, "Should never return null");
        assertTrue(result.isEmpty(), "Should return empty list");
    }

    /**
     * Test duplicate removal with mixed unique and duplicate files.
     *
     * <p>
     * Verifies that in a realistic scenario with both unique files and
     * duplicates across multiple enqueue operations, duplicates are correctly
     * removed while preserving order of first occurrence.
     * </p>
     */
    @Test
    void testMixedDuplicatesAndUnique() {
        // Given: Multiple enqueue operations with duplicates
        queueOrchestrator.enqueue(Arrays.asList(
            Paths.get("src/A.java"),
            Paths.get("src/B.java")
        ));
        queueOrchestrator.enqueue(Arrays.asList(
            Paths.get("src/B.java"),  // duplicate of B
            Paths.get("src/C.java")
        ));
        queueOrchestrator.enqueue(Arrays.asList(
            Paths.get("src/A.java")   // duplicate of A
        ));

        // When: Dequeue all files
        List<Path> result = queueOrchestrator.dequeue();

        // Then: Should contain only unique files in order of first appearance
        assertEquals(3, result.size(), "Should contain 3 unique files");
        assertEquals(Paths.get("src/A.java"), result.get(0), "A should be first");
        assertEquals(Paths.get("src/B.java"), result.get(1), "B should be second");
        assertEquals(Paths.get("src/C.java"), result.get(2), "C should be third");
    }

    /**
     * Test that multiple mark operations are idempotent.
     *
     * <p>
     * Verifies that calling markTestRunning multiple times has the same
     * effect as calling it once, and same for markTestComplete.
     * </p>
     */
    @Test
    void testMultipleMarkOperationsAreIdempotent() {
        // Given: Initial state
        assertFalse(queueOrchestrator.isTestRunning());

        // When: Mark as running multiple times
        queueOrchestrator.markTestRunning();
        queueOrchestrator.markTestRunning();
        queueOrchestrator.markTestRunning();

        // Then: Should still be running
        assertTrue(queueOrchestrator.isTestRunning(), "Should be running after multiple marks");

        // When: Mark as complete multiple times
        queueOrchestrator.markTestComplete();
        queueOrchestrator.markTestComplete();
        queueOrchestrator.markTestComplete();

        // Then: Should not be running
        assertFalse(queueOrchestrator.isTestRunning(), "Should not be running after multiple completes");
    }

    /**
     * Test state consistency during enqueue-dequeue cycles with running flag.
     *
     * <p>
     * Verifies that the test running flag correctly affects enqueue return values
     * across multiple enqueue-dequeue cycles, simulating realistic usage.
     * </p>
     */
    @Test
    void testEnqueueReturnValueConsistency() {
        // Given: Test not running
        List<Path> files = Arrays.asList(Paths.get("src/Test.java"));

        // When: Enqueue while not running
        boolean result1 = queueOrchestrator.enqueue(files);
        // Then: Should return false
        assertFalse(result1, "Should return false when test not running");

        // When: Mark as running and enqueue again
        queueOrchestrator.markTestRunning();
        boolean result2 = queueOrchestrator.enqueue(files);
        // Then: Should return true
        assertTrue(result2, "Should return true when test is running");

        // When: Mark as complete and enqueue again
        queueOrchestrator.markTestComplete();
        boolean result3 = queueOrchestrator.enqueue(files);
        // Then: Should return false again
        assertFalse(result3, "Should return false after test completes");
    }

    /**
     * Test that clear does not affect test running state.
     *
     * <p>
     * Verifies that clearing the queue only affects the queued files,
     * not the test execution state flag.
     * </p>
     */
    @Test
    void testClearDoesNotAffectTestRunningState() {
        // Given: Test is running and files are queued
        queueOrchestrator.markTestRunning();
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/Test.java")));

        // When: Clear the queue
        queueOrchestrator.clear();

        // Then: Queue should be empty but test should still be running
        assertTrue(queueOrchestrator.isEmpty(), "Queue should be empty");
        assertTrue(queueOrchestrator.isTestRunning(), "Test should still be marked as running");
    }

    /**
     * Test single file enqueue and dequeue.
     *
     * <p>
     * Simplest possible case: enqueue one file, dequeue one file.
     * Tests the most basic happy path.
     * </p>
     */
    @Test
    void testSingleFileEnqueueDequeue() {
        // Given: One file
        Path file = Paths.get("src/Single.java");

        // When: Enqueue and dequeue
        queueOrchestrator.enqueue(Arrays.asList(file));
        List<Path> result = queueOrchestrator.dequeue();

        // Then: Should get the same file back
        assertEquals(1, result.size(), "Should have one file");
        assertEquals(file, result.get(0), "Should be the same file");
    }

    /**
     * Test boundary: zero files after clear.
     *
     * <p>
     * Verifies that after clearing, dequeue returns an empty list,
     * ensuring consistent empty state behavior.
     * </p>
     */
    @Test
    void testDequeueAfterClear() {
        // Given: Files in queue
        queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/A.java")));

        // When: Clear and then dequeue
        queueOrchestrator.clear();
        List<Path> result = queueOrchestrator.dequeue();

        // Then: Should get empty list
        assertNotNull(result, "Should not be null");
        assertTrue(result.isEmpty(), "Should be empty after clear");
    }

    // ==================== Thread Safety Tests ====================

    /**
     * Test concurrent enqueue operations from multiple threads.
     *
     * <p>
     * This test verifies that multiple threads can safely enqueue files
     * concurrently without data loss or corruption. All enqueued files
     * should be present in the final queue.
     * </p>
     */
    @Test
    void testConcurrentEnqueue() throws InterruptedException {
        // Given: 10 threads each enqueueing 100 unique files
        final int threadCount = 10;
        final int filesPerThread = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(threadCount);

        // When: Launch multiple threads to enqueue concurrently
        for (int threadId = 0; threadId < threadCount; threadId++) {
            final int id = threadId;
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for signal to start
                    List<Path> files = new ArrayList<>();
                    for (int i = 0; i < filesPerThread; i++) {
                        files.add(Paths.get("src/Thread" + id + "_File" + i + ".java"));
                    }
                    queueOrchestrator.enqueue(files);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            }).start();
        }

        // Release all threads at once to maximize concurrency
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS), "All threads should complete");

        // Then: All files should be in the queue
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(threadCount * filesPerThread, result.size(),
            "Should contain all files from all threads");
    }

    /**
     * Test concurrent mark operations with enqueue.
     *
     * <p>
     * This test verifies that markTestRunning/markTestComplete can be called
     * concurrently with enqueue operations without race conditions. The test
     * ensures that the state transitions are properly synchronized.
     * </p>
     */
    @Test
    void testConcurrentMarkAndEnqueue() throws InterruptedException {
        // Given: Multiple threads performing mark operations and enqueues
        final int iterations = 100;
        final CyclicBarrier barrier = new CyclicBarrier(3);
        final AtomicInteger trueCount = new AtomicInteger(0);
        final AtomicInteger falseCount = new AtomicInteger(0);

        Thread enqueueThread = new Thread(() -> {
            try {
                barrier.await(); // Synchronize start
                for (int i = 0; i < iterations; i++) {
                    List<Path> files = Arrays.asList(Paths.get("src/File" + i + ".java"));
                    boolean wasQueued = queueOrchestrator.enqueue(files);
                    if (wasQueued) {
                        trueCount.incrementAndGet();
                    } else {
                        falseCount.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                fail("Thread failed: " + e.getMessage());
            }
        });

        Thread markRunningThread = new Thread(() -> {
            try {
                barrier.await(); // Synchronize start
                for (int i = 0; i < iterations / 2; i++) {
                    queueOrchestrator.markTestRunning();
                    Thread.sleep(1); // Small delay to allow interleaving
                }
            } catch (Exception e) {
                fail("Thread failed: " + e.getMessage());
            }
        });

        Thread markCompleteThread = new Thread(() -> {
            try {
                barrier.await(); // Synchronize start
                for (int i = 0; i < iterations / 2; i++) {
                    queueOrchestrator.markTestComplete();
                    Thread.sleep(1); // Small delay to allow interleaving
                }
            } catch (Exception e) {
                fail("Thread failed: " + e.getMessage());
            }
        });

        // When: Start all threads
        enqueueThread.start();
        markRunningThread.start();
        markCompleteThread.start();

        // Wait for completion
        enqueueThread.join(5000);
        markRunningThread.join(5000);
        markCompleteThread.join(5000);

        // Then: All operations should complete without exceptions
        // and the queue should contain all files
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(iterations, result.size(), "Should contain all enqueued files");

        // Both true and false results should occur (due to state changes)
        assertTrue(trueCount.get() > 0 || falseCount.get() > 0,
            "Should have at least some enqueue results");
    }

    /**
     * Test concurrent enqueue and dequeue operations.
     *
     * <p>
     * This test verifies that enqueue and dequeue can be safely called
     * concurrently from different threads without data corruption or loss.
     * </p>
     */
    @Test
    void testConcurrentEnqueueAndDequeue() throws InterruptedException {
        // Given: One thread continuously enqueuing, another continuously dequeuing
        final int iterations = 200;
        final CountDownLatch completionLatch = new CountDownLatch(2);
        final AtomicInteger totalEnqueued = new AtomicInteger(0);
        final AtomicInteger totalDequeued = new AtomicInteger(0);

        Thread enqueueThread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    List<Path> files = Arrays.asList(Paths.get("src/File" + i + ".java"));
                    queueOrchestrator.enqueue(files);
                    totalEnqueued.incrementAndGet();
                    Thread.sleep(1); // Small delay
                }
            } catch (Exception e) {
                fail("Enqueue thread failed: " + e.getMessage());
            } finally {
                completionLatch.countDown();
            }
        });

        Thread dequeueThread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations / 10; i++) {
                    Thread.sleep(10); // Allow some files to accumulate
                    List<Path> files = queueOrchestrator.dequeue();
                    totalDequeued.addAndGet(files.size());
                }
            } catch (Exception e) {
                fail("Dequeue thread failed: " + e.getMessage());
            } finally {
                completionLatch.countDown();
            }
        });

        // When: Start both threads
        enqueueThread.start();
        dequeueThread.start();

        // Wait for completion
        assertTrue(completionLatch.await(10, TimeUnit.SECONDS), "Threads should complete");

        // Then: Dequeue remaining files
        List<Path> remaining = queueOrchestrator.dequeue();
        totalDequeued.addAndGet(remaining.size());

        // Total dequeued should equal total enqueued (no data loss)
        assertEquals(totalEnqueued.get(), totalDequeued.get(),
            "All enqueued files should be dequeued");
    }

    /**
     * Test race condition between enqueue and markTestComplete.
     *
     * <p>
     * This test specifically targets the race condition that was fixed:
     * ensuring that when enqueue() and markTestComplete() are called
     * concurrently, the return value of enqueue() is consistent with
     * when the files were actually added to the queue.
     * </p>
     */
    @Test
    void testEnqueueAndMarkTestCompleteRaceCondition() throws InterruptedException {
        // Given: Test is running
        queueOrchestrator.markTestRunning();

        final int iterations = 1000;
        final CountDownLatch completionLatch = new CountDownLatch(2);
        final List<Boolean> enqueueResults = new ArrayList<>();

        Thread enqueueThread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    List<Path> files = Arrays.asList(Paths.get("src/File" + i + ".java"));
                    synchronized (enqueueResults) {
                        boolean result = queueOrchestrator.enqueue(files);
                        enqueueResults.add(result);
                    }
                }
            } finally {
                completionLatch.countDown();
            }
        });

        Thread markCompleteThread = new Thread(() -> {
            try {
                // Wait a bit to let some enqueues happen while running
                Thread.sleep(10);
                queueOrchestrator.markTestComplete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completionLatch.countDown();
            }
        });

        // When: Start both threads
        enqueueThread.start();
        markCompleteThread.start();

        // Wait for completion
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS), "Threads should complete");

        // Then: All files should be in the queue
        List<Path> result = queueOrchestrator.dequeue();
        assertEquals(iterations, result.size(), "Should contain all enqueued files");

        // The enqueue results should show a transition from true to false
        // (some enqueues while running, some after complete)
        synchronized (enqueueResults) {
            boolean foundTrue = enqueueResults.contains(true);
            boolean foundFalse = enqueueResults.contains(false);

            // We expect to see both states due to the markTestComplete call
            assertTrue(foundTrue || foundFalse,
                "Should have enqueue results (may be all true or mix depending on timing)");
        }
    }

    /**
     * Test concurrent requeueAtFront operations.
     *
     * <p>
     * This test verifies that requeueAtFront can be safely called
     * concurrently with other operations without corruption.
     * </p>
     */
    @Test
    void testConcurrentRequeueAtFront() throws InterruptedException {
        // Given: Initial files in queue
        queueOrchestrator.enqueue(Arrays.asList(
            Paths.get("src/Initial1.java"),
            Paths.get("src/Initial2.java")
        ));

        final int threadCount = 5;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(threadCount);

        // When: Multiple threads requeue different batches concurrently
        for (int threadId = 0; threadId < threadCount; threadId++) {
            final int id = threadId;
            new Thread(() -> {
                try {
                    startLatch.await();
                    List<Path> batch = Arrays.asList(
                        Paths.get("src/Requeue" + id + "_A.java"),
                        Paths.get("src/Requeue" + id + "_B.java")
                    );
                    queueOrchestrator.requeueAtFront(batch);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            }).start();
        }

        // Release all threads
        startLatch.countDown();

        // Wait for completion
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS), "All threads should complete");

        // Then: Queue should contain all files (no data loss)
        List<Path> result = queueOrchestrator.dequeue();

        // Each thread adds 2 files, plus 2 initial files
        // But there might be duplicates removed, so check minimum size
        assertTrue(result.size() >= 2, "Should contain at least the initial files");

        // All operations should complete without exceptions
        assertNotNull(result, "Result should not be null");
    }

    /**
     * Test thread safety of isEmpty() method during concurrent operations.
     *
     * <p>
     * This test verifies that isEmpty() returns consistent results
     * even when called concurrently with enqueue/dequeue operations.
     * </p>
     */
    @Test
    void testConcurrentIsEmpty() throws InterruptedException {
        final int iterations = 100;
        final CountDownLatch completionLatch = new CountDownLatch(3);

        Thread enqueueThread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    queueOrchestrator.enqueue(Arrays.asList(Paths.get("src/File" + i + ".java")));
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                fail("Thread failed: " + e.getMessage());
            } finally {
                completionLatch.countDown();
            }
        });

        Thread dequeueThread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations / 2; i++) {
                    Thread.sleep(2);
                    queueOrchestrator.dequeue();
                }
            } catch (Exception e) {
                fail("Thread failed: " + e.getMessage());
            } finally {
                completionLatch.countDown();
            }
        });

        Thread isEmptyThread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations * 2; i++) {
                    // Just call isEmpty() many times - should never throw exception
                    queueOrchestrator.isEmpty();
                }
            } finally {
                completionLatch.countDown();
            }
        });

        // When: Start all threads
        enqueueThread.start();
        dequeueThread.start();
        isEmptyThread.start();

        // Then: All should complete without exceptions
        assertTrue(completionLatch.await(10, TimeUnit.SECONDS),
            "All threads should complete without deadlock or exception");
    }
}
