package dk.runerne.fileserver.filehandling

import dk.runerne.common.UUIDUtil
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

class FileDescriptorSpec extends Specification {

    private static final Path TEST_ROOT_FOLDER_PATH = Path.of('src/test/resources/data')
    private static final UUID TEST_UUID = UUID.fromString('2968fbe4-77c1-4e4b-ae50-855177335e1b')
    private static final String TEST_FILENAME = "${TEST_UUID}"
    private static final Path TEST_FILE_PATH = Path.of("${TEST_ROOT_FOLDER_PATH}/x/y/z/${TEST_FILENAME}")
    private static final byte[] TEST_FILE_BYTES = "Test content".getBytes()

    void 'fromPath - OK'() {
        when:
        FileDescriptor output = FileDescriptor.fromPath(TEST_ROOT_FOLDER_PATH, TEST_FILE_PATH)

        then:
        output.id == TEST_UUID
        output.depth == 3
    }

    void 'fromPath - No root folder'() {
        when:
        FileDescriptor.fromPath(null, TEST_FILE_PATH)

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message == 'Root folder path cannot be null'
    }

    void 'fromUUIDAndDepth - OK'() {
        when:
        FileDescriptor output = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, 3)

        then:
        output.id == TEST_UUID
        output.depth == 3
    }

    void 'fromUUIDAndDepth - No root folder'() {
        when:
        FileDescriptor.fromUUIDAndDepth(null, TEST_UUID, 3)

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message == 'Root folder path cannot be null'
    }

    void 'fromUUIDAndDepth - No ID'() {
        when:
        FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, null, 3)

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message == "'id' cannot be null"
    }

    void 'fromDepth'() {
        when:
        FileDescriptor output = FileDescriptor.fromDepth(TEST_ROOT_FOLDER_PATH, 3)

        then:
        UUIDUtil.isValidUUID(output.id.toString())
        output.id
        output.depth == 3
    }

    void 'toDepth - OK'() {
        given:
        FileDescriptor input = FileDescriptor.fromDepth(TEST_ROOT_FOLDER_PATH, 3)

        when:
        FileDescriptor output = input.toDepth(4)

        then:
        output.rootFolderPath == TEST_ROOT_FOLDER_PATH
        output.id == input.id
        output.depth == 4
    }

    void 'toDepth - Negative depth'() {
        given:
        FileDescriptor input = FileDescriptor.fromDepth(TEST_ROOT_FOLDER_PATH, 3)

        when:
        input.toDepth(-7)

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message == "Depth cannot be negative"
    }

    void 'readAllBytes'() {
        given:
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, 3)

        when:
        byte[] output = fileDescriptor.readAllBytes()

        then:
        output == TEST_FILE_BYTES
    }

    void 'fileExists - #scenarie'() {
        given:
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, depth)

        when:
        boolean output = fileDescriptor.fileExists()

        then:
        output == expectedOutput

        where:
        depth || expectedOutput | scenarie
        4     || false          | 'File does not exist at depth 4'
        3     || true           | 'File exists at depth 3'
        2     || false          | 'File does not exist at depth 2'
    }

    void 'getLastModifiedTime'() {
        given:
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, 3)

        when:
        FileTime output = fileDescriptor.getLastModifiedTime()

        then:
        output == FileTime.from(Instant.parse("2025-10-25T17:11:31.3415764Z"))
    }

    void 'moveTo - OK'() {
        given:
        UUID soruceUUID = UUID.randomUUID()

        // Make original file
        FileDescriptor source = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, soruceUUID, 3)
        Files.createDirectories(source.getFolderPath())
        Files.write(source.getPath(), "Some content".getBytes())

        // Make destination descriptor
        UUID destinationUUID = UUID.randomUUID()
        FileDescriptor destination = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, destinationUUID, 4)

        when:
        FileDescriptor output = source.moveTo(destination)

        then:
        output == destination
        !source.fileExists()
        destination.fileExists()

        cleanup:
        Files.deleteIfExists(destination.getPath())
        deleteEmptyParentFolders(destination.getFolderPath(), TEST_ROOT_FOLDER_PATH)
        deleteEmptyParentFolders(source.getFolderPath(), TEST_ROOT_FOLDER_PATH)
    }

    void 'moveTo - no new file descriptor'() {
        given:
        FileDescriptor source = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, TEST_UUID, 3)

        when:
        source.moveTo(null)

        then:
        IllegalArgumentException e = thrown(IllegalArgumentException)
        e.message == 'New FileDescriptor cannot be null'
    }

    void 'purge - OK'() {
        given:
        UUID purgedUUID = UUID.randomUUID()

        // Make original file
        FileDescriptor toPurge = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, purgedUUID, 3)
        Files.createDirectories(toPurge.getFolderPath())
        Files.write(toPurge.getPath(), "Some content".getBytes())

        when:
        FileDescriptor output = toPurge.purge()

        then:
        output == toPurge
        !toPurge.fileExists()

        cleanup:
        deleteEmptyParentFolders(toPurge.getFolderPath(), TEST_ROOT_FOLDER_PATH)
    }

    void 'ensureDirectoriesExist'() {
        given:
        UUID testUUID = UUID.randomUUID()
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, testUUID, 5)

        when:
        FileDescriptor output = fileDescriptor.ensureDirectoriesExist()

        then:
        output == fileDescriptor
        Files.isDirectory(fileDescriptor.getFolderPath())

        cleanup:
        deleteEmptyParentFolders(fileDescriptor.getFolderPath(), TEST_ROOT_FOLDER_PATH)
    }

    void 'write'() {
        given:
        UUID testUUID = UUID.randomUUID()
        FileDescriptor fileDescriptor = FileDescriptor.fromUUIDAndDepth(TEST_ROOT_FOLDER_PATH, testUUID, 4)
        byte[] dataToWrite = "Some other content".getBytes()
        Files.createDirectories(fileDescriptor.getFolderPath())

        when:
        FileDescriptor output = fileDescriptor.write(dataToWrite)

        then:
        output == fileDescriptor
        Files.readAllBytes(fileDescriptor.getPath()) == dataToWrite

        cleanup:
        Files.deleteIfExists(fileDescriptor.getPath())
        deleteEmptyParentFolders(fileDescriptor.getFolderPath(), TEST_ROOT_FOLDER_PATH)
    }

    private void deleteEmptyParentFolders(Path folderPath, Path stopAtPath) {
        Path currentPath = folderPath
        while (currentPath != null && !currentPath.equals(stopAtPath)) {
            try {
                Files.delete(currentPath)
            } catch (IOException ignored) {
                break
            }
            currentPath = currentPath.getParent()
        }
    }

}