package dk.runerne.fileserver.filehandling;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable metadata snapshot for a resolved file on disk, enabling streaming without loading file contents into memory.
 *
 * @param id           the file UUID.
 * @param path         the resolved path on disk.
 * @param size         the file size in bytes.
 * @param lastModified the file's last-modified timestamp.
 * @param contentType  the resolved HTTP {@link MediaType}.
 * @param eTag         the strong HTTP ETag value (including surrounding quotes).
 */
public record StoredFile(
    UUID id,
    Path path,
    long size,
    Instant lastModified,
    MediaType contentType,
    String eTag
) {

    /**
     * Creates a streamable {@link Resource} backed directly by the file on disk.
     *
     * @return a {@link FileSystemResource} for this stored file.
     */
    public Resource toResource() {
        return new FileSystemResource(path);
    }

    /**
     * Computes a strong HTTP ETag from file size and last-modified timestamp in O(1) time.
     *
     * @param size         the file size in bytes.
     * @param lastModified the file's last-modified time.
     * @return a quoted strong ETag string, e.g. {@code "400-673e9a1b-123456"}.
     */
    public static String computeETag(long size, FileTime lastModified) {
        Instant instant = lastModified.toInstant();
        return String.format("\"%x-%x-%x\"", size, instant.getEpochSecond(), instant.getNano());
    }

}
