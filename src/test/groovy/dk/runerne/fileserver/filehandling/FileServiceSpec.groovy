package dk.runerne.fileserver.filehandling

import spock.lang.Specification

import java.nio.file.Files
import dk.runerne.fileserver.ConfigurationService

import static dk.runerne.fileserver.TestConstants.TEST_FILE_CONTENT
import static dk.runerne.fileserver.TestConstants.TEST_REQUSTED_FILE_DEPTH
import static dk.runerne.fileserver.TestConstants.TEST_ROOT_FOLDER_PATH

class FileServiceSpec extends Specification {

    private FileService fileService = new FileService()

    void setup() {
        fileService.configurationService = Mock(ConfigurationService) {
            getDataRootFolderPath() >> TEST_ROOT_FOLDER_PATH
            getRequestedFileDepth() >> TEST_REQUSTED_FILE_DEPTH
        }
        fileService.fileDistributorService = Mock(FileDistributorService)
    }

    void 'create'() {
        when:
        UUID uuid = fileService.create(TEST_FILE_CONTENT)

        then:
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, uuid, TEST_REQUSTED_FILE_DEPTH)
        Files.exists(fileDescriptor.path)

        cleanup:
        fileDescriptor.purge()
    }

    void 'update'() {
        given:
        UUID uuid = fileService.create(TEST_FILE_CONTENT)
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, uuid, TEST_REQUSTED_FILE_DEPTH)

        when:
        fileService.update(uuid, "New content".getBytes())

        then:
        Files.readAllBytes(fileDescriptor.path) == "New content".getBytes()

        and:
        1 * fileService.fileDistributorService.cleanUpOrphans(fileDescriptor)

        cleanup:
        fileDescriptor.purge()
    }

    void 'read'() {
        given:
        UUID uuid = fileService.create(TEST_FILE_CONTENT)
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, uuid, TEST_REQUSTED_FILE_DEPTH)
        fileService.fileDistributorService.getAllFileDescriptorsForId(uuid) >> [fileDescriptor]
        fileService.fileDistributorService.ensureCorrectLevel(fileDescriptor) >> fileDescriptor

        when:
        byte[] output = fileService.read(uuid)

        then:
        output == TEST_FILE_CONTENT

        cleanup:
        fileDescriptor.purge()
    }

    void 'delete - OK'() {
        given:
        UUID uuid = fileService.create(TEST_FILE_CONTENT)
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, uuid, TEST_REQUSTED_FILE_DEPTH)
        fileService.fileDistributorService.getAllFileDescriptorsForId(uuid) >> [fileDescriptor]

        when:
        fileService.delete(uuid)

        then:
        !Files.exists(fileDescriptor.path)
    }

    void 'delete - File does not exist'() {
        given:
        UUID uuid = UUID.randomUUID()
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, uuid, TEST_REQUSTED_FILE_DEPTH)
        fileService.fileDistributorService.getAllFileDescriptorsForId(uuid) >> [fileDescriptor]

        when:
        fileService.delete(uuid)

        then:
        !Files.exists(fileDescriptor.path)
    }

}