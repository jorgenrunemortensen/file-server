package dk.runerne.fileserver.maintenance

import dk.runerne.fileserver.ConfigurationService
import spock.lang.Specification

import java.nio.file.Files

import static dk.runerne.fileserver.TestConstants.TEST_ROOT_FOLDER_PATH
import static dk.runerne.fileserver.TestConstants.TEST_FILE_CONTENT
import static dk.runerne.fileserver.TestConstants.TEST_FILE_PATH

class MaintenanceJobServiceSpec extends Specification {

    private MaintenanceJobService maintenanceJobService = new MaintenanceJobService()

    void setup() {
        maintenanceJobService.configurationService = Mock(ConfigurationService)
        maintenanceJobService.folderMaintenanceService = Mock(FolderMaintenanceService)

        Files.createDirectories(TEST_FILE_PATH.parent)
        Files.write(TEST_FILE_PATH, TEST_FILE_CONTENT)
    }

    void cleanup() {
        Files.walk(TEST_ROOT_FOLDER_PATH)
                .sorted(Comparator.reverseOrder())
                .forEach { path -> Files.deleteIfExists(path) }
    }

    void 'start - success'() {
        given:
        maintenanceJobService.jobIsRunning.set(false)
        maintenanceJobService.configurationService.dataRootFolderPath >> TEST_ROOT_FOLDER_PATH
        maintenanceJobService.configurationService.maxMaintenanceConcurrentThreads >> 3

        when:
        boolean output = maintenanceJobService.startJob()

        then:
        output

        and:
        maintenanceJobService.folderMaintenanceService.maintainFile(TEST_FILE_PATH, 3)
    }

    void 'start - Job is already running'() {
        given:
        maintenanceJobService.jobIsRunning.set(true)

        when:
        boolean output = maintenanceJobService.startJob()

        then:
        !output
    }

    void 'terminateJob - success'() {
        given:
        maintenanceJobService.jobIsRunning.set(true)
        maintenanceJobService.multiThreadFolderTraverser = Mock(MultiThreadFolderTraverser)

        when:
        boolean output = maintenanceJobService.terminateJob()

        then:
        output
    }

    void 'terminateJob - No job is running'() {
        given:
        maintenanceJobService.jobIsRunning.set(false)

        when:
        boolean output = maintenanceJobService.terminateJob()

        then:
        !output
    }

    void 'getJobStatus - running'() {
        given:
        maintenanceJobService.jobIsRunning.set(true)
        maintenanceJobService.multiThreadFolderTraverser = Mock(MultiThreadFolderTraverser)  {
            maintenanceStatus >> MaintenanceJobStatus.maintaining()
        }

        when:
        MaintenanceJobStatus output = maintenanceJobService.getJobStatus()

        then:
        output == MaintenanceJobStatus.maintaining()
    }

    void 'getJobStatus - traverser says not running'() {
        given:
        maintenanceJobService.jobIsRunning.set(false)
        maintenanceJobService.multiThreadFolderTraverser = null

        when:
        MaintenanceJobStatus output = maintenanceJobService.getJobStatus()

        then:
        output == MaintenanceJobStatus.idle()
    }

    void 'getJobStatus - no traverser - but jos is running'() {
        given:
        maintenanceJobService.jobIsRunning.set(true)
        maintenanceJobService.multiThreadFolderTraverser = null

        when:
        MaintenanceJobStatus output = maintenanceJobService.getJobStatus()

        then:
        output == MaintenanceJobStatus.maintaining()
    }

}