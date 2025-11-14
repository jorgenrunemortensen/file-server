package dk.runerne.fileserver

import java.nio.file.Path

final class TestConstants {

    static final Path TEST_ROOT_FOLDER_PATH = Path.of('build/temp/data')
    static final UUID TEST_UUID = UUID.fromString('2968fbe4-77c1-4e4b-ae50-855177335e1b')
    static final String TEST_FILENAME = "${TEST_UUID}"
    static final Path TEST_FILE_PATH = Path.of("${TEST_ROOT_FOLDER_PATH}/P/F/J/${TEST_FILENAME}")
    static final byte[] TEST_FILE_CONTENT = "Test content".getBytes()
    static final int TEST_REQUSTED_FILE_DEPTH = 3

    private TestConstants() {
    }

}