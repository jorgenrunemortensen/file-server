package dk.runerne.fileserver.filehandling

import org.springframework.http.ResponseEntity
import spock.lang.Specification

class FileControllerSpec extends Specification {

    private static final byte[] TEST_FILE_CONTENT = [1, 2, 3]
    private static final UUID TEST_UUID = java.util.UUID.randomUUID()

    private FileController fileController = new FileController()

    void setup() {
        fileController.fileService = Mock(FileService)
    }

    void 'create - OK'() {
        when:
        ResponseEntity<UUID> output = fileController.create(TEST_FILE_CONTENT)

        then:
        output.statusCode.is2xxSuccessful()
        output.body == TEST_UUID

        and:
        1 * fileController.fileService.create(TEST_FILE_CONTENT) >> TEST_UUID
    }

    void 'create - Internal error'() {
        when:
        ResponseEntity<UUID> output = fileController.create(TEST_FILE_CONTENT)

        then:
        output.statusCode.is5xxServerError()
        output.body == null

        and:
        1 * fileController.fileService.create(TEST_FILE_CONTENT) >> { throw new RuntimeException('Internal error') }
    }

    void 'read - OK'() {
        when:
        ResponseEntity<byte[]> output = fileController.read(TEST_UUID)

        then:
        output == ResponseEntity.ok(TEST_FILE_CONTENT)

        and:
        1 * fileController.fileService.read(TEST_UUID) >> TEST_FILE_CONTENT
    }

    void 'read - File not found'() {
        when:
        ResponseEntity<byte[]> output = fileController.read(TEST_UUID)

        then:
        output == ResponseEntity.notFound().build()

        and:
        1 * fileController.fileService.read(TEST_UUID) >> { throw new FileNotFoundException('The file was not found') }
    }

    void 'read - Internal error'() {
        when:
        ResponseEntity<byte[]> output = fileController.read(TEST_UUID)

        then:
        output.statusCode.is5xxServerError()
        output.body == null

        and:
        1 * fileController.fileService.read(TEST_UUID) >> { throw new RuntimeException('Internal error') }
    }

    void 'update - OK'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID)

        then:
        output.statusCode.is2xxSuccessful()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT)
    }

    void 'update - File not found'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID)

        then:
        output == ResponseEntity.notFound().build()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT) >> { throw new FileNotFoundException('The file was not found') }
    }

    void 'update - Internal error'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID)

        then:
        output.statusCode.is5xxServerError()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT) >> { throw new RuntimeException('Internal error') }
    }

    void 'delete - OK'() {
        when:
        ResponseEntity<Void> output = fileController.delete(TEST_UUID)

        then:
        output.statusCode.is2xxSuccessful()

        and:
        1 * fileController.fileService.delete(TEST_UUID)
    }

    void 'delete - File not found'() {
        when:
        ResponseEntity<Void> output = fileController.delete(TEST_UUID)

        then:
        output == ResponseEntity.notFound().build()

        and:
        1 * fileController.fileService.delete(TEST_UUID) >> { throw new FileNotFoundException('The file was not found') }
    }

    void 'delete - Internal error'() {
        when:
        ResponseEntity<Void> output = fileController.delete(TEST_UUID)

        then:
        output.statusCode.is5xxServerError()

        and:
        1 * fileController.fileService.delete(TEST_UUID) >> { throw new RuntimeException('Internal error') }
    }

}