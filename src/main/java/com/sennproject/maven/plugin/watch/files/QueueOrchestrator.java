package com.sennproject.maven.plugin.watch.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates queuing of file changes during test execution.
 *
 * <p>
 * This class provides a thread-safe queue mechanism for accumulating file changes
 * that are detected while tests are running. When tests complete, the accumulated
 * changes can be dequeued and processed as a batch, ensuring that no file changes
 * are lost during test execution.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Thread-safe:</b> All operations are synchronized to support concurrent access</li>
 *   <li><b>Duplicate removal:</b> Uses LinkedHashSet to automatically eliminate duplicate paths</li>
 *   <li><b>Order preservation:</b> Maintains insertion order of file changes</li>
 *   <li><b>State tracking:</b> Tracks whether tests are currently running</li>
 * </ul>
 *
 * <h2>Typical Usage Pattern</h2>
 * <pre>
 * QueueOrchestrator queue = new QueueOrchestrator();
 *
 * // When test starts
 * queue.markTestRunning();
 *
 * // If file changes are detected during test execution
 * queue.enqueue(changedFiles);  // Returns true (queued)
 *
 * // When test completes
 * queue.markTestComplete();
 *
 * // Process accumulated changes
 * if (!queue.isEmpty()) {
 *     List&lt;Path&gt; files = queue.dequeue();
 *     // Process files...
 * }
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All mutable state is protected by synchronization:
 * </p>
 * <ul>
 *   <li>{@code queuedChanges} modifications are guarded by {@code lock}</li>
 *   <li>{@code isTestRunning} is protected by {@code lock} for consistent state</li>
 * </ul>
 *
 * @see WatcherMojo
 * @since 0.1.0
 */
public class QueueOrchestrator {
    /**
     * Flag indicating whether a test is currently running.
     * Protected by {@code lock} for thread-safe access and consistency with queue state.
     */
    private boolean isTestRunning = false;

    /**
     * Set of file paths that have been queued during test execution.
     * LinkedHashSet provides:
     * - O(1) add/remove/contains operations
     * - Automatic duplicate removal
     * - Insertion order preservation
     */
    private final Set<Path> queuedChanges = new LinkedHashSet<>();

    /**
     * Lock object for synchronizing access to both queuedChanges and isTestRunning.
     * Ensures thread-safe modifications and consistent state across both fields.
     */
    private final Object lock = new Object();

    /**
     * Enqueue file changes for later processing.
     *
     * <p>
     * Adds the specified files to the queue. The files are stored in a LinkedHashSet,
     * which automatically removes duplicates while preserving insertion order.
     * </p>
     *
     * <p>
     * The return value indicates whether the files were added to the queue because
     * a test is currently running:
     * </p>
     * <ul>
     *   <li><b>true:</b> Files were queued because test is running (deferred processing)</li>
     *   <li><b>false:</b> Files were added but can be processed immediately (test not running)</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe. Multiple threads can call
     * enqueue concurrently.</p>
     *
     * @param changedFiles list of changed files to enqueue (null or empty list will be ignored)
     * @return true if changes were queued while test is running, false otherwise
     */
    public boolean enqueue(List<Path> changedFiles) {
        // Early return for null or empty input
        if (changedFiles == null || changedFiles.isEmpty()) {
            return false;
        }

        synchronized (lock) {
            // Add all files to queue (duplicates are automatically removed by LinkedHashSet)
            queuedChanges.addAll(changedFiles);
            // Return current test execution state (both read and queue modification under same lock)
            return isTestRunning;
        }
    }

    /**
     * Dequeue all accumulated files from the queue.
     *
     * <p>
     * This method retrieves all files that have been queued and clears the queue
     * in a single atomic operation. The returned list preserves the insertion order
     * of the files (first queued, first in the list).
     * </p>
     *
     * <p>
     * If the queue is empty, this method returns an empty list (never null).
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and atomically removes
     * all queued files.</p>
     *
     * @return list of all accumulated files (queue will be cleared); never null
     */
    public List<Path> dequeue() {
        synchronized (lock) {
            // Create a copy of the current queue contents
            List<Path> result = new ArrayList<>(queuedChanges);
            // Clear the queue
            queuedChanges.clear();
            return result;
        }
    }

    /**
     * Requeue the provided files at the front of the queue.
     *
     * <p>
     * This method restores a previously dequeued batch (typically when test execution
     * fails) so that it will be processed before any files that arrived later.
     * Existing queued files are preserved and appended after the requeued batch.
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe.</p>
     *
     * @param files list of files to return to the queue (null or empty list will be ignored)
     */
    public void requeueAtFront(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        synchronized (lock) {
            LinkedHashSet<Path> reordered = new LinkedHashSet<>(files.size() + queuedChanges.size());

            for (Path file : files) {
                if (file != null) {
                    reordered.add(file);
                }
            }

            reordered.addAll(queuedChanges);

            queuedChanges.clear();
            queuedChanges.addAll(reordered);
        }
    }

    /**
     * Mark test execution as running.
     *
     * <p>
     * Sets the internal flag to indicate that a test is currently executing.
     * When this flag is set, subsequent calls to {@link #enqueue(List)} will
     * return true, indicating that files are being queued for deferred processing.
     * </p>
     *
     * <p>
     * This method should be called immediately before starting test execution.
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and synchronized with queue operations.</p>
     *
     * @see #markTestComplete()
     * @see #isTestRunning()
     */
    public void markTestRunning() {
        synchronized (lock) {
            isTestRunning = true;
        }
    }

    /**
     * Mark test execution as complete.
     *
     * <p>
     * Clears the internal flag to indicate that test execution has finished.
     * When this flag is cleared, subsequent calls to {@link #enqueue(List)} will
     * return false, indicating that files can be processed immediately.
     * </p>
     *
     * <p>
     * This method should be called immediately after test execution completes,
     * regardless of whether the test passed or failed. It is typically called
     * in a finally block to ensure the flag is always cleared.
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and synchronized with queue operations.</p>
     *
     * @see #markTestRunning()
     * @see #isTestRunning()
     */
    public void markTestComplete() {
        synchronized (lock) {
            isTestRunning = false;
        }
    }

    /**
     * Check if the queue is empty.
     *
     * <p>
     * Returns true if there are no files currently in the queue, false otherwise.
     * This method can be used to determine whether there are any pending file
     * changes that need to be processed.
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe.</p>
     *
     * @return true if the queue contains no files, false otherwise
     */
    public boolean isEmpty() {
        synchronized (lock) {
            return queuedChanges.isEmpty();
        }
    }

    /**
     * Clear all files from the queue.
     *
     * <p>
     * Removes all queued files without returning them. This method is useful
     * when you want to discard pending changes, for example in error recovery
     * scenarios.
     * </p>
     *
     * <p>
     * Unlike {@link #dequeue()}, this method does not return the cleared files.
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe.</p>
     *
     * @see #dequeue()
     */
    public void clear() {
        synchronized (lock) {
            queuedChanges.clear();
        }
    }

    /**
     * Check if a test is currently running.
     *
     * <p>
     * Returns true if {@link #markTestRunning()} has been called and
     * {@link #markTestComplete()} has not yet been called, false otherwise.
     * </p>
     *
     * <p>
     * This method can be used to check the test execution state before deciding
     * whether to queue file changes or process them immediately.
     * </p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and synchronized with
     * queue and state operations.</p>
     *
     * @return true if test is currently running, false otherwise
     * @see #markTestRunning()
     * @see #markTestComplete()
     */
    public boolean isTestRunning() {
        synchronized (lock) {
            return isTestRunning;
        }
    }
}
