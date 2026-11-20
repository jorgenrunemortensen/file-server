package dk.runerne.fileserver.filehandling;

import dk.runerne.fileserver.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileNotFoundException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>This controller handles HTTP requests for file operations such as create, read, head, update, and delete.</p>
 * <p>The base path for all endpoints in this controller is configured via the 'api.base-path' property.</p>
 * <p>Read operations stream directly from disk and support HTTP byte range requests ({@code 206 Partial Content} /
 * {@code 416 Range Not Satisfiable}), conditional requests ({@code ETag} / {@code If-None-Match} /
 * {@code If-Modified-Since} -> {@code 304 Not Modified}), {@code HEAD} metadata retrieval, and Content-Type
 * preservation/detection/override.</p>
 */
@RestController
@RequestMapping("${api.base-path}/file")
@Slf4j
public class FileController {

    static final String X_CONTENT_TYPE_HEADER = "X-Content-Type";
    private static final String ACCEPT_RANGES_BYTES = "bytes";
    private static final long DEFAULT_CACHE_CONTROL_MAX_AGE_SECONDS = 3600L;

    /**
     * The file service used to perform file operations.
     */
    @Autowired FileService fileService;

    @Autowired(required = false) ConfigurationService configurationService;

    /**
     * Creates a new file with the provided binary data.
     *
     * <p>Backwards-compatible with raw {@code application/octet-stream} uploads. If a concrete {@code Content-Type}
     * header (other than {@code application/octet-stream}), an {@code X-Content-Type} header, or a {@code contentType}
     * query parameter is supplied, the media type is stored alongside the file for subsequent {@code GET}/{@code HEAD}
     * responses.</p>
     *
     * @param data               the raw binary data to store (empty array if omitted).
     * @param contentTypeHeader  optional standard HTTP {@code Content-Type} header.
     * @param xContentTypeHeader optional {@code X-Content-Type} header overriding the upload content type.
     * @param contentTypeParam   optional {@code contentType} query parameter overriding the upload content type.
     * @return a ResponseEntity containing the UUID of the created file and HTTP status {@code 201 Created},
     * {@code 400 Bad Request} if an invalid content type was supplied, or {@code 500 Internal Server Error}.
     */
    @PostMapping()
    public ResponseEntity<UUID> create(
        @RequestBody(required = false) byte[] data,
        @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentTypeHeader,
        @RequestHeader(value = X_CONTENT_TYPE_HEADER, required = false) String xContentTypeHeader,
        @RequestParam(value = "contentType", required = false) String contentTypeParam
    ) {
        var start = System.currentTimeMillis();
        try {
            String storedContentType = ContentTypeDetector.extractContentTypeForStorage(
                contentTypeParam,
                xContentTypeHeader,
                contentTypeHeader
            );
            byte[] payload = data != null ? data : new byte[0];
            UUID id = storedContentType != null
                ? fileService.create(payload, storedContentType)
                : fileService.create(payload);
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(id);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid Content-Type on create: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            var duration = System.currentTimeMillis() - start;
            log.debug("File creation took {} ms", duration);
        }
    }

    /**
     * Streams the file with the specified UUID directly from disk, supporting HTTP byte range requests,
     * conditional caching ({@code ETag} / {@code If-None-Match} / {@code If-Modified-Since}), and
     * Content-Type resolution or override.
     *
     * @param id             the UUID of the file to read.
     * @param contentType    optional {@code contentType} query parameter to override the response {@code Content-Type}.
     * @param requestHeaders HTTP request headers (used for {@code Range}, {@code If-Range}, {@code If-None-Match},
     *                       and {@code If-Modified-Since}).
     * @return a streaming ResponseEntity with {@code 200 OK}, {@code 206 Partial Content}, {@code 304 Not Modified},
     * {@code 400 Bad Request}, {@code 404 Not Found}, {@code 416 Range Not Satisfiable}, or {@code 500 Internal Server Error}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> read(
        @PathVariable("id") UUID id,
        @RequestParam(value = "contentType", required = false) String contentType,
        @RequestHeader(required = false) HttpHeaders requestHeaders
    ) {
        try {
            StoredFile storedFile = fileService.resolve(id, contentType);
            HttpHeaders effectiveRequestHeaders = requestHeaders != null ? requestHeaders : HttpHeaders.EMPTY;

            if (isNotModified(effectiveRequestHeaders, storedFile)) {
                return ResponseEntity
                    .status(HttpStatus.NOT_MODIFIED)
                    .headers(buildConditionalCacheHeaders(storedFile))
                    .build();
            }

            String rangeHeader = effectiveRequestHeaders.getFirst(HttpHeaders.RANGE);
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                if (matchesIfRange(effectiveRequestHeaders, storedFile)) {
                    return buildPartialContentResponse(storedFile, rangeHeader);
                }
                // If-Range did not match: serve full 200 OK using lazy InputStreamResource so Spring MVC's
                // AbstractMessageConverterMethodProcessor does not re-apply the Range header automatically.
                HttpHeaders headers = buildRepresentationHeaders(storedFile);
                headers.setContentLength(storedFile.size());
                return ResponseEntity
                    .ok()
                    .headers(headers)
                    .body(new InputStreamResource(storedFile.toResource()));
            }

            HttpHeaders headers = buildRepresentationHeaders(storedFile);
            headers.setContentLength(storedFile.size());
            return ResponseEntity
                .ok()
                .headers(headers)
                .body(storedFile.toResource());
        } catch (IllegalArgumentException e) {
            log.debug("Bad request while reading file {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Returns metadata headers for the file with the specified UUID without transferring the body.
     *
     * @param id             the UUID of the file.
     * @param contentType    optional {@code contentType} query parameter to override the response {@code Content-Type}.
     * @param requestHeaders HTTP request headers (used for {@code If-None-Match} and {@code If-Modified-Since}).
     * @return a ResponseEntity without body containing {@code Content-Length}, {@code Content-Type},
     * {@code Accept-Ranges}, {@code ETag}, {@code Cache-Control}, and {@code Last-Modified} headers.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(
        @PathVariable("id") UUID id,
        @RequestParam(value = "contentType", required = false) String contentType,
        @RequestHeader(required = false) HttpHeaders requestHeaders
    ) {
        try {
            StoredFile storedFile = fileService.resolve(id, contentType);
            HttpHeaders effectiveRequestHeaders = requestHeaders != null ? requestHeaders : HttpHeaders.EMPTY;

            if (isNotModified(effectiveRequestHeaders, storedFile)) {
                return ResponseEntity
                    .status(HttpStatus.NOT_MODIFIED)
                    .headers(buildConditionalCacheHeaders(storedFile))
                    .build();
            }

            HttpHeaders headers = buildRepresentationHeaders(storedFile);
            headers.setContentLength(storedFile.size());
            return ResponseEntity
                .ok()
                .headers(headers)
                .build();
        } catch (IllegalArgumentException e) {
            log.debug("Bad request while executing HEAD for file {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates the file with the specified UUID using the provided binary data.
     *
     * @param data               the new data for the file.
     * @param id                 the UUID of the file to be updated.
     * @param contentTypeHeader  optional standard HTTP {@code Content-Type} header.
     * @param xContentTypeHeader optional {@code X-Content-Type} header overriding the stored content type.
     * @param contentTypeParam   optional {@code contentType} query parameter overriding the stored content type.
     * @return a ResponseEntity with HTTP status.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
        @RequestBody(required = false) byte[] data,
        @PathVariable("id") UUID id,
        @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentTypeHeader,
        @RequestHeader(value = X_CONTENT_TYPE_HEADER, required = false) String xContentTypeHeader,
        @RequestParam(value = "contentType", required = false) String contentTypeParam
    ) {
        try {
            String storedContentType = ContentTypeDetector.extractContentTypeForStorage(
                contentTypeParam,
                xContentTypeHeader,
                contentTypeHeader
            );
            byte[] payload = data != null ? data : new byte[0];
            if (storedContentType != null) {
                fileService.update(id, payload, storedContentType);
            } else {
                fileService.update(id, payload);
            }
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.debug("Invalid Content-Type on update for {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes the file with the specified UUID.
     *
     * @param id the UUID of the file to be deleted.
     * @return a ResponseEntity with HTTP status.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        try {
            fileService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<Resource> buildPartialContentResponse(StoredFile storedFile, String rangeHeader) {
        long fileSize = storedFile.size();
        if (fileSize <= 0) {
            return rangeNotSatisfiable(fileSize);
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            log.debug("Malformed Range header '{}': {}", rangeHeader, e.getMessage());
            return rangeNotSatisfiable(fileSize);
        }

        if (ranges.isEmpty()) {
            return rangeNotSatisfiable(fileSize);
        }

        for (HttpRange range : ranges) {
            long start = range.getRangeStart(fileSize);
            long end = range.getRangeEnd(fileSize);
            if (start >= 0 && start < fileSize && end >= start) {
                long clampedEnd = Math.min(end, fileSize - 1);
                long regionLength = clampedEnd - start + 1;
                ByteRangeResource rangeResource = new ByteRangeResource(storedFile.path(), start, regionLength);

                HttpHeaders headers = buildRepresentationHeaders(storedFile);
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + clampedEnd + "/" + fileSize);
                headers.setContentLength(regionLength);

                return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .body(rangeResource);
            }
        }

        return rangeNotSatisfiable(fileSize);
    }

    private ResponseEntity<Resource> rangeNotSatisfiable(long fileSize) {
        return ResponseEntity
            .status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
            .header(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_BYTES)
            .build();
    }

    private HttpHeaders buildRepresentationHeaders(StoredFile storedFile) {
        HttpHeaders headers = buildConditionalCacheHeaders(storedFile);
        headers.setContentType(storedFile.contentType());
        headers.set(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_BYTES);
        return headers;
    }

    private HttpHeaders buildConditionalCacheHeaders(StoredFile storedFile) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(storedFile.eTag());
        headers.setLastModified(storedFile.lastModified());
        headers.setCacheControl(buildCacheControl());
        return headers;
    }

    private CacheControl buildCacheControl() {
        long maxAgeSeconds = configurationService != null
            ? configurationService.getCacheControlMaxAgeSeconds()
            : DEFAULT_CACHE_CONTROL_MAX_AGE_SECONDS;
        return CacheControl.maxAge(Math.max(0L, maxAgeSeconds), TimeUnit.SECONDS).cachePublic();
    }

    private static boolean isNotModified(HttpHeaders requestHeaders, StoredFile storedFile) {
        List<String> ifNoneMatch = requestHeaders.getIfNoneMatch();
        if (!ifNoneMatch.isEmpty()) {
            return matchesIfNoneMatch(ifNoneMatch, storedFile.eTag());
        }

        long ifModifiedSinceMillis = requestHeaders.getIfModifiedSince();
        if (ifModifiedSinceMillis >= 0) {
            long resourceLastModifiedMillis = storedFile.lastModified()
                .truncatedTo(ChronoUnit.SECONDS)
                .toEpochMilli();
            return resourceLastModifiedMillis <= ifModifiedSinceMillis;
        }

        return false;
    }

    private static boolean matchesIfNoneMatch(List<String> ifNoneMatchValues, String currentETag) {
        String normalizedCurrent = stripWeakPrefix(currentETag);
        for (String rawValue : ifNoneMatchValues) {
            if (rawValue == null) {
                continue;
            }
            for (String candidate : rawValue.split(",")) {
                String trimmed = candidate.trim();
                if ("*".equals(trimmed) || stripWeakPrefix(trimmed).equals(normalizedCurrent)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stripWeakPrefix(String eTag) {
        String trimmed = eTag.trim();
        if (trimmed.startsWith("W/") || trimmed.startsWith("w/")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private static boolean matchesIfRange(HttpHeaders requestHeaders, StoredFile storedFile) {
        String ifRange = requestHeaders.getFirst(HttpHeaders.IF_RANGE);
        if (ifRange == null || ifRange.isBlank()) {
            return true;
        }
        String trimmed = ifRange.trim();
        if (trimmed.startsWith("W/") || trimmed.startsWith("w/")) {
            return false;
        }
        if (trimmed.startsWith("\"")) {
            return trimmed.equals(storedFile.eTag());
        }
        try {
            Instant ifRangeInstant = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return storedFile.lastModified()
                .truncatedTo(ChronoUnit.SECONDS)
                .equals(ifRangeInstant.truncatedTo(ChronoUnit.SECONDS));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

}
