package dk.runerne.fileserver.maintenance;

import lombok.Data;

import java.time.Duration;
import java.time.Instant;

/**
 * Metrics for tracking the progress of maintenance tasks.
 */
@Data
public class MaintenanceProgressMetrics {

    /**
     * Width of the progress bar in characters.
     */
    private static final int BAR_WIDTH = 100;

    /** <p>Characters used for rendering the progress bar.</p>
     * <p>Each character represents an eighth of a block.</p>
     */
    private static final char[] BLOCKS = {
        ' ',  // 0/8
        '▏',  // 1/8
        '▎',  // 2/8
        '▍',  // 3/8
        '▌',  // 4/8
        '▋',  // 5/8
        '▊',  // 6/8
        '▉',  // 7/8
        '█'   // 8/8 (full block)
    };

    /** The start time of the maintenance task. */
    private final Instant startTime;

    /** The time of the latest status update. */
    private final Instant statusTime;

    /** The total number of files and folders to process. */
    private final int numberToProcess;

    /** The number of files and folders processed so far. */
    private final int numberProcessed;

    /** Gets the elapsed time since the start of the maintenance task.
     * @return The elapsed time as a Duration, or <i>null</i> if startTime or statusTime is <i>null</i>.
     */
    public Duration getElapsedTime() {
        if (startTime == null || statusTime == null) {
            return null;
        }
        return Duration.between(startTime, statusTime);
    }

    /**
     * Gets the estimated completion time based on current progress.
     * @return The estimated completion time as an Instant, or <i>null</i> if startTime, statusTime is <i>null</i> or progress is zero.
     */
    public Instant getEstimatedCompletionTime() {
        if (startTime == null || statusTime == null || getProgress() <= 0.0) {
            return null;
        }
        Duration elapsed = getElapsedTime();
        long estimatedTotalMillis = (long) (elapsed.toMillis() / getProgress());
        return startTime.plusMillis(estimatedTotalMillis);
    }

    /**
     * Gets the remaining time until estimated completion.
     * @return The remaining time as a Duration, or <i>null</i> if estimated completion time or statusTime is <i>null</i>.
     */
    public Duration getRemainingTime() {
        Instant estimatedCompletionTime = getEstimatedCompletionTime();
        if (estimatedCompletionTime == null || statusTime == null) {
            return null;
        }
        return Duration.between(statusTime, estimatedCompletionTime);
    }

    /** Gets the progress rate in terms of progress per second.
     * @return The progress rate as a double, or 0.0 if startTime or statusTime is <i>null</i> or elapsed time is zero.
     */
    public double getProgressRate() {
        if (startTime == null || statusTime == null) {
            return 0.0;
        }
        Duration elapsed = getElapsedTime();
        if (elapsed.isZero()) {
            return 0.0;
        }
        return getProgress() / elapsed.toMillis() * 1000.0; // progress per second
    }

    /** Gets the current progress as a fraction between 0.0 and 1.0.
     * @return The progress as a double.
     */
    public double getProgress() {
        if (numberToProcess == 0) {
            return 0.0;
        }
        return (double) numberProcessed / (double) numberToProcess;
    }

    /** Gets a string representation of the progress bar.
     * @return The progress bar string.
     */
    public String getProgressBar() {
        return toProgressBarString(numberProcessed, numberToProcess, BAR_WIDTH);
    }

    private static String toProgressBarString(double progress, double total, int width) {
        var fraction = progress / total;
        var totalBlocks = fraction * width;

        var fullBlocks = (int) totalBlocks;
        var partialBlockIndex = (int) ((totalBlocks - fullBlocks) * 8);

        StringBuilder bar = new StringBuilder(width);
        bar.append("█".repeat(fullBlocks));

        // Partial blocks (only if space left)
        if (fullBlocks < width && partialBlockIndex > 0) {
            bar.append(BLOCKS[partialBlockIndex]);
            fullBlocks++; // tæller som "optaget"
        }

        // Empty blocks
        for (int i = fullBlocks; i < width; i++) {
            bar.append(' ');
        }

        return bar.toString();
    }

}