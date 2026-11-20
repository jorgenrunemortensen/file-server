package dk.runerne.fileserver.filehandling

import org.springframework.http.MediaType
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ContentTypeDetectorSpec extends Specification {

    @TempDir
    Path tempDir

    @Unroll
    void 'probeMagicBytes detects #expectedType from file header bytes'() {
        given:
        Path file = tempDir.resolve(UUID.randomUUID().toString())
        Files.write(file, headerBytes as byte[])

        expect:
        ContentTypeDetector.probeMagicBytes(file) == MediaType.parseMediaType(expectedType)

        where:
        expectedType      | headerBytes
        'image/png'       | [(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A]
        'image/jpeg'      | [(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0]
        'image/gif'       | 'GIF89a......'.getBytes(StandardCharsets.US_ASCII)
        'image/webp'      | 'RIFF1234WEBP'.getBytes(StandardCharsets.US_ASCII)
        'audio/wav'       | 'RIFF1234WAVE'.getBytes(StandardCharsets.US_ASCII)
        'audio/mpeg'      | 'ID3\u0003\u0000\u0000'.getBytes(StandardCharsets.ISO_8859_1)
        'audio/mpeg'      | [(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x00]
        'audio/flac'      | 'fLaC\u0000\u0000'.getBytes(StandardCharsets.ISO_8859_1)
        'audio/ogg'       | 'OggS\u0000\u0002'.getBytes(StandardCharsets.ISO_8859_1)
        'audio/mp4'       | '\u0000\u0000\u0000 ftypM4A '.getBytes(StandardCharsets.ISO_8859_1)
        'video/mp4'       | '\u0000\u0000\u0000 ftypisom'.getBytes(StandardCharsets.ISO_8859_1)
        'video/quicktime' | '\u0000\u0000\u0000\u0014ftypqt  '.getBytes(StandardCharsets.ISO_8859_1)
        'video/webm'      | '\u001A\u0045\u00DF\u00A3\u0042\u0082webm'.getBytes(StandardCharsets.ISO_8859_1)
        'application/pdf' | '%PDF-1.7\n'.getBytes(StandardCharsets.US_ASCII)
        'image/svg+xml'   | '<svg xmlns="http://www.w3.org/2000/svg"></svg>'.getBytes(StandardCharsets.UTF_8)
    }

    void 'extractContentTypeForStorage respects priority and ignores application/octet-stream header'() {
        expect:
        ContentTypeDetector.extractContentTypeForStorage('audio/ogg', 'audio/mpeg', 'video/mp4') == 'audio/ogg'
        ContentTypeDetector.extractContentTypeForStorage(null, 'audio/mpeg', 'video/mp4') == 'audio/mpeg'
        ContentTypeDetector.extractContentTypeForStorage(null, null, 'video/mp4') == 'video/mp4'
        ContentTypeDetector.extractContentTypeForStorage(null, null, 'application/octet-stream') == null
        ContentTypeDetector.extractContentTypeForStorage('application/octet-stream', null, null) == 'application/octet-stream'
    }

    void 'parseConcreteMediaType rejects wildcards and invalid strings'() {
        when:
        ContentTypeDetector.parseConcreteMediaType(badType)

        then:
        thrown(IllegalArgumentException)

        where:
        badType << [null, '', '   ', 'invalid', '*/*', 'audio/*']
    }

}
