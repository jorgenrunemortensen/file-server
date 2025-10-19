package dk.runerne.fileserver.maintenance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>This controller handles HTTP requests for managing maintenance jobs on the file server.</p>
 * <p>The base path for all endpoints in this controller is configured via the 'api.base-path' property.</p>
 * <p>The class implements the controller level and forwards all valid requests to the appropriate methods in {@link MaintenanceJobService}</p>.
 */
@RestController
@RequestMapping("${api.base-path}/maintenance-job")
public class MaintenanceJobController {

    @Autowired private MaintenanceJobService maintenanceJobService;

    /**
     * Starts a maintenance job.
     * @return a ResponseEntity indicating the result of the operation.
     */
    @PostMapping("/start")
    public ResponseEntity<String> start() {
        return maintenanceJobService.startJob()
                   ? ResponseEntity.ok("Maintenance job started successfully.")
                   : ResponseEntity.status(HttpStatus.CONFLICT).body("Maintenance job is already running. Please wait until it completes.");
    }

    /**
     * Stops the currently running maintenance job.
     * @return a ResponseEntity indicating the result of the operation.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stop() {
        if (maintenanceJobService.terminateJob()) {
            return ResponseEntity.ok("Maintenance job terminated successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("No Maintenance job is currently running.");
        }
    }

    /**
     * Gets the status of the current maintenance job.
     * @return the status of the maintenance job.
     */
    @GetMapping("/status")
    public MaintenanceJobStatus getStatus() {
        return maintenanceJobService.getJobStatus();
    }

}