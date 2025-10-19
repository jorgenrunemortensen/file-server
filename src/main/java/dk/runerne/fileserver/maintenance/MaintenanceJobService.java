package dk.runerne.fileserver.maintenance;

import dk.runerne.fileserver.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing maintenance jobs on the file server.
 * It allows starting, terminating, and checking the status of maintenance jobs.
 */
@Service
@Slf4j
public class MaintenanceJobService {

    @Autowired private ConfigurationService configurationService;
    @Autowired private FolderMaintenanceService folderMaintenanceService;

    private final AtomicBoolean JobIsRunning = new AtomicBoolean(false);
    private MultiThreadFolderTraverser multiThreadFolderTraverser;

    /**
     * Starts a maintenance job if one is not already running.
     * @return true if the job was started successfully, false if a job is already running.
     */
    public boolean startJob() {
        if (!JobIsRunning.compareAndSet(false, true)) {
            log.info("Maintenance job is already running. Please wait until it completes.");
            return false;
        }
        multiThreadFolderTraverser = new MultiThreadFolderTraverser(
            configurationService.getDataRootFolderPath(),
            configurationService.getMaxMaintenanceConcurrentThreads(),
            (folder, depth) -> folderMaintenanceService.maintainFile(folder, depth),
            () -> {
                JobIsRunning.set(false);
            }
        ).start();

        log.info("Maintenance job started.");
        return true;
    }

    /**
     * Terminates the currently running maintenance job.
     * @return true if the job was terminated successfully, false if no job was running.
     */
    public boolean terminateJob() {
        if (!JobIsRunning.get() || multiThreadFolderTraverser == null) {
            log.info("No maintenance job is currently running.");
            return false;
        }

        log.info("Terminating maintenance job process.");
        multiThreadFolderTraverser.terminate();

        return true;
    }

    /**
     * Gets the status of the current maintenance job.
     * @return the status of the maintenance job.
     */
    public MaintenanceJobStatus getJobStatus() {
        if (multiThreadFolderTraverser != null) {
            return multiThreadFolderTraverser.getMaintenanceStatus();
        }

        return JobIsRunning.get() ? MaintenanceJobStatus.maintaining() :  MaintenanceJobStatus.idle();
    }

}