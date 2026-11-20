package dk.runerne.fileserver.filehandling

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class FileControllerWebSpec extends Specification {

    private static final Path TEMP_ROOT = Files.createTempDirectory('file-server-web-spec-')
    private static final String BASE_URL = '/api/v1/file'

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add('app.root-folder') { TEMP_ROOT.toString() }
        registry.add('app.default-file-depth') { '2' }
        registry.add('app.cache-control-max-age-seconds') { '3600' }
    }

    @Autowired
    MockMvc mockMvc

    void cleanupSpec() {
        if (Files.exists(TEMP_ROOT)) {
            TEMP_ROOT.toFile().deleteDir()
        }
    }

    void 'GET full download streams file with Accept-Ranges, Content-Length, ETag, Cache-Control, Last-Modified, and Content-Type'() {
        given:
        byte[] payload = '0123456789abcdefghijklmnopqrstuvwxyz'.getBytes(StandardCharsets.UTF_8)
        String fileId = uploadFile(payload, MediaType.APPLICATION_OCTET_STREAM_VALUE)

        when:
        def result = mockMvc.perform(get("${BASE_URL}/${fileId}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, 'bytes'))
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, payload.length))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, 'max-age=3600, public'))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andExpect(content().bytes(payload))
            .andReturn()

        then:
        result.response.getHeader(HttpHeaders.ETAG).startsWith('"')
        result.response.getHeader(HttpHeaders.ETAG).endsWith('"')

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    @Unroll
    void 'GET Range "#rangeHeader" returns 206 Partial Content with expected slice and Content-Range "#expectedContentRange"'() {
        given:
        byte[] payload = '0123456789ABCDEFGHIJ'.getBytes(StandardCharsets.UTF_8) // 20 bytes
        String fileId = uploadFile(payload, 'audio/mpeg')

        when:
        def result = mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.RANGE, rangeHeader)
        )
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, 'bytes'))
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, expectedContentRange))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, expectedBody.length()))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'audio/mpeg'))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, 'max-age=3600, public'))
            .andExpect(content().bytes(expectedBody.getBytes(StandardCharsets.UTF_8)))
            .andReturn()

        then: 'Content-Range header is emitted exactly once (not duplicated by converter)'
        result.response.getHeaders(HttpHeaders.CONTENT_RANGE) == [expectedContentRange]

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))

        where:
        rangeHeader   || expectedContentRange | expectedBody
        'bytes=0-0'   || 'bytes 0-0/20'       | '0'
        'bytes=0-9'   || 'bytes 0-9/20'       | '0123456789'
        'bytes=5-14'  || 'bytes 5-14/20'      | '56789ABCDE'
        'bytes=10-'   || 'bytes 10-19/20'     | 'ABCDEFGHIJ'
        'bytes=19-'   || 'bytes 19-19/20'     | 'J'
        'bytes=-5'    || 'bytes 15-19/20'     | 'FGHIJ'
        'bytes=-1'    || 'bytes 19-19/20'     | 'J'
        'bytes=-100'  || 'bytes 0-19/20'      | '0123456789ABCDEFGHIJ'
        'bytes=15-99' || 'bytes 15-19/20'     | 'FGHIJ'
    }

    @Unroll
    void 'GET invalid or unsatisfiable Range "#rangeHeader" returns 416 Range Not Satisfiable with Content-Range bytes */20'() {
        given:
        byte[] payload = '0123456789ABCDEFGHIJ'.getBytes(StandardCharsets.UTF_8) // 20 bytes
        String fileId = uploadFile(payload, MediaType.APPLICATION_OCTET_STREAM_VALUE)

        expect:
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.RANGE, rangeHeader)
        )
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, 'bytes */20'))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, 'bytes'))
            .andExpect(content().bytes(new byte[0]))

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))

        where:
        rangeHeader << [
            'bytes=20-25',
            'bytes=20-',
            'bytes=100-200',
            'bytes=10-5',
            'bytes=-0',
            'bytes=abc-def',
            'items=0-5',
            'bytes=-'
        ]
    }

    void 'GET Range on empty (0-byte) file returns 416 with bytes */0 while full GET returns 200'() {
        given:
        String fileId = uploadFile(new byte[0], MediaType.APPLICATION_OCTET_STREAM_VALUE)

        expect:
        mockMvc.perform(get("${BASE_URL}/${fileId}"))
            .andExpect(status().isOk())
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 0))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, 'bytes'))
            .andExpect(content().bytes(new byte[0]))

        and:
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.RANGE, 'bytes=0-0')
        )
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, 'bytes */0'))

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    void 'HEAD returns Content-Length, Content-Type, Accept-Ranges, ETag, Cache-Control, Last-Modified without body'() {
        given:
        byte[] payload = 'audio payload bytes for head test'.getBytes(StandardCharsets.UTF_8)
        String fileId = uploadFile(payload, 'audio/mp4')

        when:
        def result = mockMvc.perform(head("${BASE_URL}/${fileId}"))
            .andExpect(status().isOk())
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, payload.length))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'audio/mp4'))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, 'bytes'))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, 'max-age=3600, public'))
            .andReturn()

        then:
        result.response.contentAsByteArray.length == 0

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    void 'ETag and If-None-Match return 304 Not Modified on GET and HEAD (strong, weak, wildcard, multi-etag)'() {
        given:
        byte[] payload = 'cacheable content'.getBytes(StandardCharsets.UTF_8)
        String fileId = uploadFile(payload, 'image/png')
        String etag = mockMvc.perform(head("${BASE_URL}/${fileId}"))
            .andExpect(status().isOk())
            .andReturn()
            .response
            .getHeader(HttpHeaders.ETAG)

        expect: 'exact strong ETag match returns 304 on GET'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_NONE_MATCH, etag)
        )
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ETAG, etag))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, 'max-age=3600, public'))
            .andExpect(header().exists(HttpHeaders.LAST_MODIFIED))
            .andExpect(content().bytes(new byte[0]))

        and: 'weak ETag match returns 304 on GET'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_NONE_MATCH, "W/${etag}")
        )
            .andExpect(status().isNotModified())

        and: 'multi-ETag list match returns 304 on GET'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_NONE_MATCH, "\"other-tag\", ${etag}")
        )
            .andExpect(status().isNotModified())

        and: 'wildcard * returns 304 on HEAD'
        mockMvc.perform(
            head("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_NONE_MATCH, '*')
        )
            .andExpect(status().isNotModified())

        and: 'non-matching ETag returns 200 with full body'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_NONE_MATCH, '"different-etag"')
        )
            .andExpect(status().isOk())
            .andExpect(content().bytes(payload))

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    void 'If-Modified-Since returns 304 when unmodified and 200 when older'() {
        given:
        byte[] payload = 'last-modified test'.getBytes(StandardCharsets.UTF_8)
        String fileId = uploadFile(payload, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        String lastModified = mockMvc.perform(head("${BASE_URL}/${fileId}"))
            .andExpect(status().isOk())
            .andReturn()
            .response
            .getHeader(HttpHeaders.LAST_MODIFIED)

        expect: 'same Last-Modified returns 304'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_MODIFIED_SINCE, lastModified)
        )
            .andExpect(status().isNotModified())

        and: 'older If-Modified-Since returns 200'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.IF_MODIFIED_SINCE, 'Wed, 21 Oct 2015 07:28:00 GMT')
        )
            .andExpect(status().isOk())
            .andExpect(content().bytes(payload))

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    void 'If-Range serves 206 when ETag matches and full 200 when ETag mismatches'() {
        given:
        byte[] payload = '0123456789'.getBytes(StandardCharsets.UTF_8)
        String fileId = uploadFile(payload, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        String etag = mockMvc.perform(head("${BASE_URL}/${fileId}"))
            .andReturn()
            .response
            .getHeader(HttpHeaders.ETAG)

        expect: 'matching strong ETag honors Range with 206'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.RANGE, 'bytes=0-3')
                .header(HttpHeaders.IF_RANGE, etag)
        )
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, 'bytes 0-3/10'))
            .andExpect(content().bytes('0123'.getBytes(StandardCharsets.UTF_8)))

        and: 'stale ETag ignores Range and returns full 200'
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.RANGE, 'bytes=0-3')
                .header(HttpHeaders.IF_RANGE, '"stale-etag"')
        )
            .andExpect(status().isOk())
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 10))
            .andExpect(content().bytes(payload))

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    void 'Content-Type preservation, X-Content-Type, magic-byte probing, and query parameter override work properly'() {
        given: 'a raw binary upload containing PNG magic bytes (no Content-Type stored)'
        byte[] pngHeader = [(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00] as byte[]
        String pngId = uploadFile(pngHeader, MediaType.APPLICATION_OCTET_STREAM_VALUE)

        and: 'a raw binary upload with X-Content-Type header'
        String xHeaderId = mockMvc.perform(
            post(BASE_URL)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header('X-Content-Type', 'audio/flac')
                .content('opaque-bytes'.getBytes(StandardCharsets.UTF_8))
        )
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .contentAsString
            .replace('"', '')

        and: 'a raw binary upload with ?contentType query parameter'
        String queryParamId = mockMvc.perform(
            post("${BASE_URL}?contentType=video/webm")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content('opaque-video'.getBytes(StandardCharsets.UTF_8))
        )
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .contentAsString
            .replace('"', '')

        expect: 'PNG magic bytes are automatically detected'
        mockMvc.perform(get("${BASE_URL}/${pngId}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'image/png'))

        and: 'X-Content-Type was preserved'
        mockMvc.perform(head("${BASE_URL}/${xHeaderId}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'audio/flac'))

        and: 'upload ?contentType query param was preserved'
        mockMvc.perform(get("${BASE_URL}/${queryParamId}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'video/webm'))

        and: 'GET and HEAD ?contentType query parameter overrides stored/probed type'
        mockMvc.perform(get("${BASE_URL}/${pngId}?contentType=application/octet-stream"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'application/octet-stream'))

        mockMvc.perform(head("${BASE_URL}/${xHeaderId}?contentType=audio/mpeg"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'audio/mpeg'))

        and: 'invalid ?contentType override returns 400 Bad Request'
        mockMvc.perform(get("${BASE_URL}/${pngId}?contentType=invalid"))
            .andExpect(status().isBadRequest())

        and: 'PUT updates content, content type, and changes ETag'
        String originalEtag = mockMvc.perform(head("${BASE_URL}/${xHeaderId}"))
            .andReturn().response.getHeader(HttpHeaders.ETAG)

        mockMvc.perform(
            put("${BASE_URL}/${xHeaderId}")
                .contentType(MediaType.parseMediaType('video/mp4'))
                .content('updated larger video payload bytes'.getBytes(StandardCharsets.UTF_8))
        )
            .andExpect(status().isOk())

        String updatedEtag = mockMvc.perform(head("${BASE_URL}/${xHeaderId}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'video/mp4'))
            .andReturn().response.getHeader(HttpHeaders.ETAG)

        updatedEtag != originalEtag

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${pngId}"))
        mockMvc.perform(delete("${BASE_URL}/${xHeaderId}"))
        mockMvc.perform(delete("${BASE_URL}/${queryParamId}"))
    }

    void 'GET streams large multi-chunk byte range accurately from disk'() {
        given: 'a 128 KB payload with deterministic byte values'
        byte[] payload = new byte[128 * 1024]
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251)
        }
        String fileId = uploadFile(payload, 'audio/mpeg')

        when: 'requesting a 32 KB slice starting at offset 65536'
        int start = 65536
        int end = 98303
        byte[] expectedSlice = Arrays.copyOfRange(payload, start, end + 1)

        then:
        mockMvc.perform(
            get("${BASE_URL}/${fileId}")
                .header(HttpHeaders.RANGE, "bytes=${start}-${end}")
        )
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes ${start}-${end}/${payload.length}"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, expectedSlice.length))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, 'audio/mpeg'))
            .andExpect(content().bytes(expectedSlice))

        cleanup:
        mockMvc.perform(delete("${BASE_URL}/${fileId}"))
    }

    void 'GET and HEAD return 404 for nonexistent UUID'() {
        given:
        UUID missing = UUID.randomUUID()

        expect:
        mockMvc.perform(get("${BASE_URL}/${missing}"))
            .andExpect(status().isNotFound())

        mockMvc.perform(head("${BASE_URL}/${missing}"))
            .andExpect(status().isNotFound())
    }

    private String uploadFile(byte[] content, String contentType) {
        return mockMvc.perform(
            post(BASE_URL)
                .contentType(MediaType.parseMediaType(contentType))
                .content(content)
        )
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .contentAsString
            .replace('"', '')
    }

}
