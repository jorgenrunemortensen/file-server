package dk.runerne.fileserver.maintenance

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dk.runerne.fileserver.ConfigurationService
import dk.runerne.fileserver.filehandling.FileDescriptor
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import static dk.runerne.fileserver.TestConstants.TEST_FILE_PATH
import static dk.runerne.fileserver.TestConstants.TEST_ROOT_FOLDER_PATH
import static dk.runerne.fileserver.TestConstants.TEST_UUID
import static dk.runerne.fileserver.TestConstants.getTEST_FILE_CONTENT

class FolderMaintenanceServiceSpec extends Specification {

    private FolderMaintenanceService folderMaintenanceService = new FolderMaintenanceService()

    void setup() {
        folderMaintenanceService.configurationService = Mock(ConfigurationService)
    }

    void 'maintainFile - Depth as requested'() {
        when:
        folderMaintenanceService.maintainFile(Path.of('/some/file/path.txt'), 2)

        then:
        folderMaintenanceService.configurationService.getMaintenanceDepth() >> 2
    }

    void 'maintainFile - Invalid UUID'() {
        given:
        Logger logger = LoggerFactory.getLogger(FolderMaintenanceService.class)
        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)
        folderMaintenanceService.configurationService.getMaintenanceDepth() >> 3

        when:
        folderMaintenanceService.maintainFile(Path.of('/some/file/path.txt'), 2)

        then:
        listAppender.list.any { event ->
            event.level.toString() == 'WARN' && event.getFormattedMessage() == 'Skipping misplaced file with invalid UUID name: \\some\\file\\path.txt'
        }
    }

    void 'maintainFile - OK'() {
        given:
        folderMaintenanceService.configurationService.getRequestedFileDepth() >> 1
        folderMaintenanceService.configurationService.getDataRootFolderPath() >> TEST_ROOT_FOLDER_PATH
        Files.createDirectories(TEST_FILE_PATH.parent)
        Files.write(TEST_FILE_PATH, TEST_FILE_CONTENT)
        FileDescriptor source = FileDescriptor.fromPath(TEST_ROOT_FOLDER_PATH, TEST_FILE_PATH)
        FileDescriptor target = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, 1)

        when:
        folderMaintenanceService.maintainFile(TEST_FILE_PATH, 3)

        then:
        Files.exists(target.path)
        !Files.exists(source.path)

        cleanup:
        deleteFolderRecursively(Path.of("${TEST_ROOT_FOLDER_PATH}/P"))
    }

    static void deleteFolderRecursively(Path folderPath) {
        if (!Files.exists(folderPath)) return

        Files.walkFileTree(folderPath, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }


}