package dk.runerne.fileserver.maintenance;

/**
 * Enum representing the state of a maintenance job.
 */
public enum MaintenanceJobState {
    /**
     * The maintenance job is idle and not currently running.
     */
    IDLE,

    /**
     * The maintenance job is currently running.
     */
    MAINTAINING,

}