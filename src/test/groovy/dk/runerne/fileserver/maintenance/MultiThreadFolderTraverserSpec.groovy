package dk.runerne.fileserver.maintenance

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import static dk.runerne.fileserver.TestConstants.TEST_ROOT_FOLDER_PATH

class MultiThreadFolderTraverserSpec extends Specification {

    void "Multi-threaded folder traversal - start and run to end"() {
        given:
        Path[] files = createFiles()
        boolean finished = false
        MultiThreadFolderTraverser traverser = new MultiThreadFolderTraverser(
                TEST_ROOT_FOLDER_PATH,
                3,
                { filePath, i ->
                    Files.write(filePath, "Inserted ${i}".bytes)
                    sleep(10)
                },
                { finished = true }
        )

        when:
        traverser.start()
        while (!finished) {
            Thread.sleep(10)
        }

        then:
        files.every { filePath -> verifyContent(filePath, 'Inserted 3') }

        cleanup:
        deleteFolderRecursively(TEST_ROOT_FOLDER_PATH)
    }

    void "Multi-threaded folder traversal - start and interrupt"() {
        given:
        Path[] files = createFiles()
        boolean finished = false
        MultiThreadFolderTraverser traverser = new MultiThreadFolderTraverser(
                TEST_ROOT_FOLDER_PATH,
                3,
                { filePath, i ->
                    Files.write(filePath, "Inserted ${i}".bytes)
                    sleep(10)
                },
                { finished = true }
        )

        when:
        traverser.start()
        sleep(22)
        traverser.terminate()
        int nCreated = files.count { filePath -> readContent(filePath).startsWith('Created') }
        int nInserted = files.count { filePath -> readContent(filePath) == 'Inserted 3' }

        then:
        nCreated + nInserted == 10
        nInserted > 0
        nCreated > 0

        cleanup:
        deleteFolderRecursively(TEST_ROOT_FOLDER_PATH)
    }

    void "Multi-threaded folder traversal - start and run - get status"() {
        given:
        Path[] files = createFiles()
        boolean finished = false
        MultiThreadFolderTraverser traverser = new MultiThreadFolderTraverser(
                TEST_ROOT_FOLDER_PATH,
                3,
                { filePath, i ->
                    Files.write(filePath, "Inserted ${i}".bytes)
                    sleep(10)
                },
                { finished = true }
        )

        when:
        MaintenanceJobStatus status0 = traverser.maintenanceStatus
        traverser.start()
        MaintenanceJobStatus status1 = traverser.maintenanceStatus
        sleep(22)
        MaintenanceJobStatus status2 = traverser.maintenanceStatus
        sleep(100)
        MaintenanceJobStatus status3 = traverser.maintenanceStatus

        then:
        status0.state == MaintenanceJobState.IDLE
        status1.state == MaintenanceJobState.MAINTAINING
        status2.state == MaintenanceJobState.MAINTAINING
        status3.state == MaintenanceJobState.IDLE

        cleanup:
        deleteFolderRecursively(TEST_ROOT_FOLDER_PATH)
    }

    private static Path[] createFiles() {
        return [
                createFileInFolder('a/b/c/file1.txt', 'Created 1'),
                createFileInFolder('a/b/d/file1.txt', 'Created 2'),
                createFileInFolder('a/b/e/file1.txt', 'Created 3'),
                createFileInFolder('a/b/f/file1.txt', 'Created 4'),
                createFileInFolder('a/b/g/file1.txt', 'Created 5'),
                createFileInFolder('a/c/a/file1.txt', 'Created 6'),
                createFileInFolder('a/c/b/file1.txt', 'Created 7'),
                createFileInFolder('a/c/c/file1.txt', 'Created 8'),
                createFileInFolder('a/c/d/file1.txt', 'Created 9'),
                createFileInFolder('a/c/e/file1.txt', 'Created 10'),
        ]
    }

    private static Path createFileInFolder(String relativeFilePath, String content) {
        Path filePath = TEST_ROOT_FOLDER_PATH.resolve(relativeFilePath)
        Files.createDirectories(filePath.parent)
        Files.write(filePath, content.getBytes())
        return filePath
    }

    private static String readContent(Path filePath) {
        return new String(Files.readAllBytes(filePath))
    }

    private static boolean verifyContent(Path filePath, String expectedContent) {
        return readContent(filePath) == expectedContent
    }

    private static void deleteFolderRecursively(Path folder) {
        if (!Files.exists(folder)) return

        Files.walk(folder)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.delete(it) }
    }

}