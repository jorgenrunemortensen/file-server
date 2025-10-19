package dk.runerne.fileserver.maintenance

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification

class MaintenanceJobControllerSpec extends Specification {

    private MaintenanceJobController maintenanceJobController = new MaintenanceJobController()

    void setup() {
        maintenanceJobController.maintenanceJobService = Mock(MaintenanceJobService)
    }

    void 'start - success'() {
        given:
        maintenanceJobController.maintenanceJobService.startJob() >> true

        when:
        ResponseEntity<String> output = maintenanceJobController.start()

        then:
        output.statusCode == HttpStatus.OK
        output.body == 'Maintenance job started successfully.'
    }

    void 'start - failure'() {
        given:
        maintenanceJobController.maintenanceJobService.startJob() >> false

        when:
        ResponseEntity<String> output = maintenanceJobController.start()

        then:
        output.statusCode == HttpStatus.CONFLICT
        output.body == 'Maintenance job is already running. Please wait until it completes.'
    }

    void 'stop - success'() {
        given:
        maintenanceJobController.maintenanceJobService.terminateJob() >> true

        when:
        ResponseEntity<String> output = maintenanceJobController.stop()

        then:
        output.statusCode == HttpStatus.OK
        output.body == 'Maintenance job terminated successfully.'
    }

    void 'stop - failure'() {
        given:
        maintenanceJobController.maintenanceJobService.terminateJob() >> false

        when:
        ResponseEntity<String> output = maintenanceJobController.stop()

        then:
        output.statusCode == HttpStatus.CONFLICT
        output.body == 'No maintenance job is currently running.'
    }

    void 'status'() {
        given:
        maintenanceJobController.maintenanceJobService.getJobStatus() >> MaintenanceJobStatus.idle()

        when:
        MaintenanceJobStatus output = maintenanceJobController.getStatus()

        then:
        output.state == MaintenanceJobState.IDLE
        output.progressMetrics == new MaintenanceProgressMetrics(null, null, 0, 0)
    }

}