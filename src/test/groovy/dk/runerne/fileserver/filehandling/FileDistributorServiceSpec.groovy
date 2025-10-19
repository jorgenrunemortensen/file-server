package dk.runerne.fileserver.filehandling

import dk.runerne.fileserver.ConfigurationService
import spock.lang.Specification

import java.nio.file.Files

import static dk.runerne.fileserver.TestConstants.TEST_ROOT_FOLDER_PATH
import static dk.runerne.fileserver.TestConstants.TEST_REQUSTED_FILE_DEPTH
import static dk.runerne.fileserver.TestConstants.TEST_UUID
import static dk.runerne.fileserver.TestConstants.TEST_FILE_CONTENT

class FileDistributorServiceSpec extends Specification {

    private FileDistributorService fileDistributorService = new FileDistributorService()

    void setup() {
        fileDistributorService.configurationService = Mock(ConfigurationService) {
            getDataRootFolderPath() >> TEST_ROOT_FOLDER_PATH
            getRequestedFileDepth() >> TEST_REQUSTED_FILE_DEPTH
        }
    }

    void 'cleanUpOrphans'() {
        given:
        FileDescriptor level1FileDescriptor = createFileAndFileDescriptor(1)
        FileDescriptor level3FileDescriptor = createFileAndFileDescriptor(TEST_REQUSTED_FILE_DEPTH)
        FileDescriptor level5FileDescriptor = createFileAndFileDescriptor(5)
        fileDistributorService.configurationService.getRequestedFileDepth() >> TEST_REQUSTED_FILE_DEPTH

        when:
        fileDistributorService.cleanUpOrphans(level3FileDescriptor)

        then:
        !Files.exists(level1FileDescriptor.getPath())
        Files.exists(level3FileDescriptor.getPath())
        !Files.exists(level5FileDescriptor.getPath())

        cleanup:
        level3FileDescriptor.purge()
    }

    void 'cleanUpOrphans - No file descriptor'() {
        when:
        fileDistributorService.cleanUpOrphans(null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'FileDescriptor or its UUID cannot be null'
    }

    void 'cleanUpOrphans - File descriptor without ID'() {
        when:
        fileDistributorService.cleanUpOrphans(null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'FileDescriptor or its UUID cannot be null'
    }

    void 'getAllFileDescriptorsForId'() {
        given:
        FileDescriptor[] fileDescriptors = [
                createFileAndFileDescriptor(0),
                createFileAndFileDescriptor(3),
                createFileAndFileDescriptor(4),
                createFileAndFileDescriptor(6),
                createFileAndFileDescriptor(8),
        ]

        when:
        Set<FileDescriptor> output = fileDistributorService.getAllFileDescriptorsForId(TEST_UUID)

        then:
        output == (fileDescriptors as Set)

        cleanup:
        fileDescriptors.each { it.purge() }
    }

    void 'getAllFileDescriptorsForId - No ID'() {
        when:
        fileDistributorService.getAllFileDescriptorsForId(null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'ID cannot be null'
    }

    void 'ensureCorrectLevel - At correct level'() {
        given:
        FileDescriptor fileDescriptor = createFileAndFileDescriptor(TEST_REQUSTED_FILE_DEPTH)

        when:
        FileDescriptor output = fileDistributorService.ensureCorrectLevel(fileDescriptor)

        then:
        output == fileDescriptor

        cleanup:
        output.purge()
    }

    void 'ensureCorrectLevel - Not at correct level'() {
        given:
        FileDescriptor fileDescriptor = createFileAndFileDescriptor(TEST_REQUSTED_FILE_DEPTH + 2)

        when:
        FileDescriptor output = fileDistributorService.ensureCorrectLevel(fileDescriptor)

        then:
        output.getDepth() == TEST_REQUSTED_FILE_DEPTH

        cleanup:
        output.purge()
    }

    private static FileDescriptor createFileAndFileDescriptor(int depth) {
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, depth).ensureDirectoriesExist()
        fileDescriptor.write(TEST_FILE_CONTENT)
        return fileDescriptor
    }

}