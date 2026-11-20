package dk.runerne.fileserver.filehandling;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves and validates HTTP {@link MediaType} values from explicit request parameters/headers,
 * stored metadata, or lightweight magic-byte probing of the file header.
 */
public final class ContentTypeDetector {

    private static final int PROBE_HEADER_SIZE = 64;

    private ContentTypeDetector() {
    }

    /**
     * Parses and validates a raw media type string.
     *
     * @param rawContentType the raw media type string.
     * @return the parsed {@link MediaType}.
     * @throws IllegalArgumentException if the string is blank, invalid, or contains a wildcard.
     */
    public static MediaType parseConcreteMediaType(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            throw new IllegalArgumentException("Content-Type must not be blank");
        }
        MediaType mediaType = MediaType.parseMediaType(rawContentType.trim());
        if (mediaType.isWildcardType() || mediaType.isWildcardSubtype()) {
            throw new IllegalArgumentException("Content-Type must not contain wildcards: " + rawContentType);
        }
        return normalizeMediaType(mediaType);
    }

    private static MediaType normalizeMediaType(MediaType mediaType) {
        String type = mediaType.getType().toLowerCase(Locale.ROOT);
        if (("audio".equals(type) || "video".equals(type) || "image".equals(type)
                || MediaType.APPLICATION_OCTET_STREAM.equalsTypeAndSubtype(mediaType)
                || MediaType.APPLICATION_PDF.equalsTypeAndSubtype(mediaType))
                && mediaType.getCharset() != null) {
            return new MediaType(mediaType.getType(), mediaType.getSubtype());
        }
        return mediaType;
    }

    /**
     * Determines the content type to persist for an upload/update request.
     *
     * <p>Priority order:</p>
     * <ol>
     *     <li>{@code contentTypeParam} query parameter (validated strictly).</li>
     *     <li>{@code xContentTypeHeader} custom header (validated strictly).</li>
     *     <li>{@code contentTypeHeader} standard HTTP Content-Type header (persisted only if concrete and not {@code application/octet-stream}).</li>
     * </ol>
     *
     * @param contentTypeParam   optional {@code contentType} query parameter.
     * @param xContentTypeHeader optional {@code X-Content-Type} header.
     * @param contentTypeHeader  optional {@code Content-Type} header.
     * @return normalized content type string to persist, or {@code null} if none should be stored.
     * @throws IllegalArgumentException if an explicitly supplied content type is malformed or wildcard.
     */
    public static String extractContentTypeForStorage(
        String contentTypeParam,
        String xContentTypeHeader,
        String contentTypeHeader
    ) {
        if (contentTypeParam != null && !contentTypeParam.isBlank()) {
            return parseConcreteMediaType(contentTypeParam).toString();
        }
        if (xContentTypeHeader != null && !xContentTypeHeader.isBlank()) {
            return parseConcreteMediaType(xContentTypeHeader).toString();
        }
        if (contentTypeHeader != null && !contentTypeHeader.isBlank()) {
            MediaType parsed = MediaType.parseMediaType(contentTypeHeader.trim());
            if (!parsed.isWildcardType()
                    && !parsed.isWildcardSubtype()
                    && !MediaType.APPLICATION_OCTET_STREAM.equalsTypeAndSubtype(parsed)) {
                return normalizeMediaType(parsed).toString();
            }
        }
        return null;
    }

    /**
     * Resolves the response {@link MediaType} for a stored file.
     *
     * <p>Priority order:</p>
     * <ol>
     *     <li>Explicit {@code overrideContentType} query parameter (validated strictly).</li>
     *     <li>Stored metadata {@code storedContentType}.</li>
     *     <li>Magic-byte probing of the first {@value #PROBE_HEADER_SIZE} bytes on disk.</li>
     *     <li>Default {@link MediaType#APPLICATION_OCTET_STREAM}.</li>
     * </ol>
     *
     * @param filePath            path to the file on disk.
     * @param storedContentType   optional stored content type from sidecar metadata.
     * @param overrideContentType optional override content type from the request.
     * @return the resolved concrete {@link MediaType}.
     * @throws IllegalArgumentException if {@code overrideContentType} is provided but invalid.
     */
    public static MediaType resolve(Path filePath, String storedContentType, String overrideContentType) {
        if (overrideContentType != null && !overrideContentType.isBlank()) {
            return parseConcreteMediaType(overrideContentType);
        }

        if (storedContentType != null && !storedContentType.isBlank()) {
            try {
                return parseConcreteMediaType(storedContentType);
            } catch (IllegalArgumentException ignored) {
                // Fall through to probing if stored metadata is somehow malformed
            }
        }

        if (filePath != null) {
            MediaType probed = probeMagicBytes(filePath);
            if (probed != null) {
                return probed;
            }
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Probes the first bytes of a file to identify common media formats without reading the whole file.
     *
     * @param filePath path to the file on disk.
     * @return detected {@link MediaType}, or {@code null} if unknown.
     */
    public static MediaType probeMagicBytes(Path filePath) {
        byte[] buffer = new byte[PROBE_HEADER_SIZE];
        int bytesRead;
        try (InputStream is = Files.newInputStream(filePath)) {
            bytesRead = is.read(buffer);
        } catch (IOException e) {
            return null;
        }
        if (bytesRead <= 0) {
            return null;
        }
        return detectFromHeaderBytes(buffer, bytesRead);
    }

    static MediaType detectFromHeaderBytes(byte[] header, int len) {
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return MediaType.IMAGE_PNG;
        }

        // JPEG: FF D8 FF
        if (len >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG;
        }

        // GIF: GIF87a or GIF89a
        if (len >= 6 && matchesAscii(header, 0, "GIF87a") || (len >= 6 && matchesAscii(header, 0, "GIF89a"))) {
            return MediaType.IMAGE_GIF;
        }

        // RIFF containers: WEBP, WAVE
        if (len >= 12 && matchesAscii(header, 0, "RIFF")) {
            if (matchesAscii(header, 8, "WEBP")) {
                return MediaType.parseMediaType("image/webp");
            }
            if (matchesAscii(header, 8, "WAVE")) {
                return MediaType.parseMediaType("audio/wav");
            }
        }

        // MP3 with ID3v2 tag
        if (len >= 3 && matchesAscii(header, 0, "ID3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }

        // MP3 raw MPEG audio frame sync (MPEG1/2 Layer 3)
        if (len >= 2
                && (header[0] & 0xFF) == 0xFF
                && ((header[1] & 0xFF) == 0xFB
                        || (header[1] & 0xFF) == 0xF3
                        || (header[1] & 0xFF) == 0xF2
                        || (header[1] & 0xFF) == 0xFA)) {
            return MediaType.parseMediaType("audio/mpeg");
        }

        // FLAC: fLaC
        if (len >= 4 && matchesAscii(header, 0, "fLaC")) {
            return MediaType.parseMediaType("audio/flac");
        }

        // OGG: OggS
        if (len >= 4 && matchesAscii(header, 0, "OggS")) {
            return MediaType.parseMediaType("audio/ogg");
        }

        // ISO Base Media File Format: ....ftyp<brand>
        if (len >= 12 && matchesAscii(header, 4, "ftyp")) {
            String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            return switch (brand) {
                case "M4A ", "M4B ", "M4P " -> MediaType.parseMediaType("audio/mp4");
                case "qt  " -> MediaType.parseMediaType("video/quicktime");
                case "avif", "avis" -> MediaType.parseMediaType("image/avif");
                case "heic", "heix", "mif1" -> MediaType.parseMediaType("image/heic");
                default -> MediaType.parseMediaType("video/mp4");
            };
        }

        // EBML (WebM / Matroska): 1A 45 DF A3
        if (len >= 4
                && (header[0] & 0xFF) == 0x1A
                && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF
                && (header[3] & 0xFF) == 0xA3) {
            String snippet = new String(header, 0, len, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            if (snippet.contains("webm")) {
                return MediaType.parseMediaType("video/webm");
            }
            return MediaType.parseMediaType("video/x-matroska");
        }

        // PDF: %PDF-
        if (len >= 5 && matchesAscii(header, 0, "%PDF-")) {
            return MediaType.APPLICATION_PDF;
        }

        // SVG
        String prefix = new String(header, 0, len, StandardCharsets.UTF_8).stripLeading();
        if (prefix.startsWith("\uFEFF")) {
            prefix = prefix.substring(1).stripLeading();
        }
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        if (lowerPrefix.startsWith("<svg") || (lowerPrefix.startsWith("<?xml") && lowerPrefix.contains("<svg"))) {
            return MediaType.parseMediaType("image/svg+xml");
        }

        return null;
    }

    private static boolean matchesAscii(byte[] buffer, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if (buffer[offset + i] != (byte) expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

}
