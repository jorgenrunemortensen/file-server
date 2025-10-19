package dk.runerne.fileserver.maintenance;

import lombok.Data;

/**
 * Class representing the status of a maintenance job, including its state and progress metrics.
 */
@Data
public class MaintenanceJobStatus {

    private final MaintenanceJobState state;
    private final MaintenanceProgressMetrics progressMetrics;

    /**
     * Creates a MaintenanceJobStatus instance representing an idle state.
     * @return a MaintenanceJobStatus instance with IDLE state and default progress metrics.
     */
    public static MaintenanceJobStatus idle() {
        return new MaintenanceJobStatus(MaintenanceJobState.IDLE, new MaintenanceProgressMetrics(null, null, 0, 0));
    }

    /**
     * Creates a MaintenanceJobStatus instance representing a maintaining state.
     * @return a MaintenanceJobStatus instance with MAINTAINING state and default progress metrics.
     */
    public static MaintenanceJobStatus maintaining() {
        return new MaintenanceJobStatus(MaintenanceJobState.MAINTAINING, new MaintenanceProgressMetrics(null, null,  0, 0));
    }

    /**
     * Creates a MaintenanceJobStatus instance with the specified state and progress metrics.
     * @param jobState the state of the maintenance job.
     * @param progressMetrics the progress metrics of the maintenance job.
     * @return a MaintenanceJobStatus instance with the specified state and progress metrics.
     */
    public static MaintenanceJobStatus maintainingWithMetrics(MaintenanceJobState jobState, MaintenanceProgressMetrics progressMetrics) {
        return new MaintenanceJobStatus(jobState, progressMetrics);
    }

    private MaintenanceJobStatus(MaintenanceJobState state, MaintenanceProgressMetrics progressMetrics) {
        this.state = state;
        this.progressMetrics = progressMetrics;
    }

}