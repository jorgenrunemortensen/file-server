package dk.runerne.fileserver.filehandling

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import static dk.runerne.fileserver.TestConstants.TEST_FILE_CONTENT
import static dk.runerne.fileserver.TestConstants.TEST_UUID

class FileControllerSpec extends Specification {

    @TempDir
    Path tempDir

    private FileController fileController = new FileController()
    private StoredFile storedFile

    void setup() {
        fileController.fileService = Mock(FileService)
        Path file = tempDir.resolve(TEST_UUID.toString())
        Files.write(file, TEST_FILE_CONTENT)
        storedFile = new StoredFile(
            TEST_UUID,
            file,
            TEST_FILE_CONTENT.length,
            Instant.parse('2026-01-15T10:30:00Z'),
            MediaType.APPLICATION_OCTET_STREAM,
            '"c-6968c198-0"'
        )
    }

    void 'create - OK (backwards-compatible binary upload)'() {
        when:
        ResponseEntity<UUID> output = fileController.create(TEST_FILE_CONTENT, MediaType.APPLICATION_OCTET_STREAM_VALUE, null, null)

        then:
        output.statusCode == HttpStatus.CREATED
        output.body == TEST_UUID

        and:
        1 * fileController.fileService.create(TEST_FILE_CONTENT) >> TEST_UUID
    }

    void 'create - OK with concrete Content-Type header'() {
        when:
        ResponseEntity<UUID> output = fileController.create(TEST_FILE_CONTENT, 'audio/mpeg', null, null)

        then:
        output.statusCode == HttpStatus.CREATED
        output.body == TEST_UUID

        and:
        1 * fileController.fileService.create(TEST_FILE_CONTENT, 'audio/mpeg') >> TEST_UUID
    }

    void 'create - Bad request on invalid contentType query param'() {
        when:
        ResponseEntity<UUID> output = fileController.create(TEST_FILE_CONTENT, null, null, 'not-a-media-type')

        then:
        output.statusCode == HttpStatus.BAD_REQUEST
        output.body == null
        0 * fileController.fileService._
    }

    void 'create - Internal error'() {
        when:
        ResponseEntity<UUID> output = fileController.create(TEST_FILE_CONTENT, null, null, null)

        then:
        output.statusCode.is5xxServerError()
        output.body == null

        and:
        1 * fileController.fileService.create(TEST_FILE_CONTENT) >> { throw new RuntimeException('Internal error') }
    }

    void 'read - OK streams FileSystemResource with full headers'() {
        when:
        ResponseEntity<Resource> output = fileController.read(TEST_UUID, null, new HttpHeaders())

        then:
        output.statusCode == HttpStatus.OK
        output.body instanceof FileSystemResource
        output.headers.getFirst(HttpHeaders.ACCEPT_RANGES) == 'bytes'
        output.headers.getFirst(HttpHeaders.CONTENT_RANGE) == null
        output.headers.getContentLength() == TEST_FILE_CONTENT.length
        output.headers.getContentType() == MediaType.APPLICATION_OCTET_STREAM
        output.headers.getETag() == storedFile.eTag()
        output.headers.getCacheControl() == 'max-age=3600, public'
        output.headers.getLastModified() == storedFile.lastModified().toEpochMilli()

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> storedFile
    }

    void 'read - 206 Partial Content for valid byte range'() {
        given:
        HttpHeaders requestHeaders = new HttpHeaders()
        requestHeaders.set(HttpHeaders.RANGE, 'bytes=0-3')

        when:
        ResponseEntity<Resource> output = fileController.read(TEST_UUID, null, requestHeaders)

        then:
        output.statusCode == HttpStatus.PARTIAL_CONTENT
        output.body instanceof ByteRangeResource
        ByteRangeResource rangeResource = (ByteRangeResource) output.body
        rangeResource.start == 0L
        rangeResource.length == 4L
        output.headers.getFirst(HttpHeaders.CONTENT_RANGE) == "bytes 0-3/${TEST_FILE_CONTENT.length}"
        output.headers.getContentLength() == 4L
        output.headers.getFirst(HttpHeaders.ACCEPT_RANGES) == 'bytes'
        output.headers.getETag() == storedFile.eTag()

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> storedFile
    }

    void 'read - 416 Range Not Satisfiable for out-of-bounds range'() {
        given:
        HttpHeaders requestHeaders = new HttpHeaders()
        requestHeaders.set(HttpHeaders.RANGE, 'bytes=999-1000')

        when:
        ResponseEntity<Resource> output = fileController.read(TEST_UUID, null, requestHeaders)

        then:
        output.statusCode == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE
        output.body == null
        output.headers.getFirst(HttpHeaders.CONTENT_RANGE) == "bytes */${TEST_FILE_CONTENT.length}"
        output.headers.getFirst(HttpHeaders.ACCEPT_RANGES) == 'bytes'

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> storedFile
    }

    void 'read - 304 Not Modified when If-None-Match matches ETag'() {
        given:
        HttpHeaders requestHeaders = new HttpHeaders()
        requestHeaders.setIfNoneMatch(storedFile.eTag())

        when:
        ResponseEntity<Resource> output = fileController.read(TEST_UUID, null, requestHeaders)

        then:
        output.statusCode == HttpStatus.NOT_MODIFIED
        output.body == null
        output.headers.getETag() == storedFile.eTag()
        output.headers.getCacheControl() == 'max-age=3600, public'

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> storedFile
    }

    void 'read - File not found'() {
        when:
        ResponseEntity<Resource> output = fileController.read(TEST_UUID, null, new HttpHeaders())

        then:
        output == ResponseEntity.notFound().build()

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> { throw new FileNotFoundException('The file was not found') }
    }

    void 'read - Internal error'() {
        when:
        ResponseEntity<Resource> output = fileController.read(TEST_UUID, null, new HttpHeaders())

        then:
        output.statusCode.is5xxServerError()
        output.body == null

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> { throw new RuntimeException('Internal error') }
    }

    void 'head - OK returns headers without body'() {
        when:
        ResponseEntity<Void> output = fileController.head(TEST_UUID, null, new HttpHeaders())

        then:
        output.statusCode == HttpStatus.OK
        output.body == null
        output.headers.getContentLength() == TEST_FILE_CONTENT.length
        output.headers.getContentType() == MediaType.APPLICATION_OCTET_STREAM
        output.headers.getFirst(HttpHeaders.ACCEPT_RANGES) == 'bytes'
        output.headers.getETag() == storedFile.eTag()
        output.headers.getCacheControl() == 'max-age=3600, public'
        output.headers.getLastModified() == storedFile.lastModified().toEpochMilli()

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> storedFile
    }

    void 'head - 304 Not Modified when If-None-Match matches'() {
        given:
        HttpHeaders requestHeaders = new HttpHeaders()
        requestHeaders.setIfNoneMatch(storedFile.eTag())

        when:
        ResponseEntity<Void> output = fileController.head(TEST_UUID, null, requestHeaders)

        then:
        output.statusCode == HttpStatus.NOT_MODIFIED
        output.body == null
        output.headers.getETag() == storedFile.eTag()

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> storedFile
    }

    void 'head - File not found'() {
        when:
        ResponseEntity<Void> output = fileController.head(TEST_UUID, null, new HttpHeaders())

        then:
        output == ResponseEntity.notFound().build()

        and:
        1 * fileController.fileService.resolve(TEST_UUID, null) >> { throw new FileNotFoundException('The file was not found') }
    }

    void 'update - OK'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID, MediaType.APPLICATION_OCTET_STREAM_VALUE, null, null)

        then:
        output.statusCode.is2xxSuccessful()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT)
    }

    void 'update - OK with explicit content type'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID, 'video/mp4', null, null)

        then:
        output.statusCode.is2xxSuccessful()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT, 'video/mp4')
    }

    void 'update - File not found'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID, null, null, null)

        then:
        output == ResponseEntity.notFound().build()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT) >> { throw new FileNotFoundException('The file was not found') }
    }

    void 'update - Internal error'() {
        when:
        ResponseEntity<Void> output = fileController.update(TEST_FILE_CONTENT, TEST_UUID, null, null, null)

        then:
        output.statusCode.is5xxServerError()

        and:
        1 * fileController.fileService.update(TEST_UUID, TEST_FILE_CONTENT) >> { throw new RuntimeException('Internal error') }
    }

    void 'delete - OK'() {
        when:
        ResponseEntity<Void> output = fileController.delete(TEST_UUID)

        then:
        output.statusCode == HttpStatus.NO_CONTENT

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
