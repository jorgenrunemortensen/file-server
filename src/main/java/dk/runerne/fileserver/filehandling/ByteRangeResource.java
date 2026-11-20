package dk.runerne.fileserver.filehandling;

import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A streamable Spring {@link org.springframework.core.io.Resource} representing a contiguous byte range
 * of a file on disk, using {@link FileChannel#position(long)} for O(1) seeking without buffering the file in memory.
 */
public class ByteRangeResource extends AbstractResource {

    private final Path path;
    private final long start;
    private final long length;

    /**
     * Creates a resource covering {@code length} bytes starting at {@code start} in {@code path}.
     *
     * @param path   the path to the file on disk.
     * @param start  the zero-based byte offset to start reading from.
     * @param length the number of bytes to stream.
     */
    public ByteRangeResource(Path path, long start, long length) {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        if (start < 0 || length < 0) {
            throw new IllegalArgumentException("Start and length must be non-negative");
        }
        this.path = path;
        this.start = start;
        this.length = length;
    }

    /**
     * Returns the zero-based byte offset of this range.
     *
     * @return the start offset in bytes.
     */
    public long getStart() {
        return start;
    }

    /**
     * Returns the length of this byte range.
     *
     * @return the number of bytes in this range.
     */
    public long getLength() {
        return length;
    }

    @Override
    public String getDescription() {
        return "Byte range [" + start + ", length=" + length + "] of file [" + path + "]";
    }

    @Override
    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public long contentLength() {
        return length;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            channel.position(start);
            return new BoundedFileChannelInputStream(channel, length);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private static final class BoundedFileChannelInputStream extends InputStream {

        private final FileChannel channel;
        private long remaining;

        private BoundedFileChannelInputStream(FileChannel channel, long length) {
            this.channel = channel;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            byte[] single = new byte[1];
            int bytesRead = read(single, 0, 1);
            return bytesRead == -1 ? -1 : (single[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            ByteBuffer buffer = ByteBuffer.wrap(b, off, toRead);
            int bytesRead = channel.read(buffer);
            if (bytesRead == -1) {
                remaining = 0;
                return -1;
            }
            remaining -= bytesRead;
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

}
