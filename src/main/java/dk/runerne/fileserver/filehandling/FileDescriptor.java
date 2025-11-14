package dk.runerne.fileserver.filehandling;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>Represents a file descriptor that manages file storage and retrieval based on a UUID and a specified directory depth.</p>
 * <p>The file descriptor describes a file that is stored in the file-server storage, i.e. it keeps track of its location, its id (UUID) and the depth of the file.</p>
 * <p>An instance of this class can be created either by {@link #fromPath(Path, Path) fromPath}-, the {@link #fromUUIDAndDepth(Path, UUID, int) fromUUIDAndDepth}- or the
 * {@link #fromDepth(Path, int)}-method.</p>
 */
@Slf4j
@EqualsAndHashCode
public class FileDescriptor {

    private static final String ALGORITHM = "SHA-256";

    /**
     * The unique identifier for the file.
     */
    @Getter private final UUID id;

    /**
     * The depth of the directory structure in which the file is located.
     */
    @Getter private final int depth;
    private List<String> hashStrings = null;
    private final Path rootFolderPath;

    /**
     * Creates a FileDescriptor from the given root folder path and file path.
     * @param rootFolderPath The root folder path.
     * @param filePath The file path.
     * @return A new FileDescriptor instance.
     */
    public static FileDescriptor fromPath(Path rootFolderPath, Path filePath) {
        if (rootFolderPath == null) {
            throw new IllegalArgumentException("Root folder path cannot be null");
        }

        var relativePath = rootFolderPath.relativize(filePath);
        return new FileDescriptor(rootFolderPath, UUID.fromString(filePath.getFileName().toString()), relativePath.getNameCount() - 1);
    }

    /**
     * Creates a FileDescriptor from the given root folder path, UUID, and depth.
     * @param rootFolderPath The root folder path.
     * @param id The UUID of the file.
     * @param depth The depth of the directory structure.
     * @return A new FileDescriptor instance.
     */
    public static FileDescriptor fromUUIDAndDepth(Path rootFolderPath, UUID id, int depth) {
        if (rootFolderPath == null) {
            throw new IllegalArgumentException("Root folder path cannot be null");
        }

        if (id == null) {
            throw new IllegalArgumentException("'id' cannot be null");
        }

        return new FileDescriptor(rootFolderPath, id, depth);
    }

    /**
     * Creates a FileDescriptor from the given root folder path and depth, generating a new UUID.
     * @param rootFolderPath The root folder path.
     * @param depth The depth in the directory structure that the file is located.
     * @return A new FileDescriptor instance.
     */
    public static FileDescriptor fromDepth(Path rootFolderPath, int depth) {
        return new FileDescriptor(rootFolderPath, UUID.randomUUID(), depth);
    }

    /**
     * Creates a new FileDescriptor with the specified depth. All other properties remain the same.
     * @param depth The new depth for the FileDescriptor.
     * @return A new FileDescriptor instance with the specified depth.
     */
    public FileDescriptor toDepth(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("Depth cannot be negative");
        }
        return new FileDescriptor(rootFolderPath, this.id, depth);
    }

    /**
     * Reads all bytes from the file represented by this FileDescriptor.
     * @return A byte array containing the file's contents.
     * @throws IOException If an I/O error occurs reading from the file.
     */
    public byte[] readAllBytes() throws IOException {
        return Files.readAllBytes(getPath());
    }

    /**
     * Checks if the file represented by this FileDescriptor exists.
     * @return true if the file exists, false otherwise.
     */
    public boolean fileExists() {
        return Files.exists(getPath());
    }

    /**
     * Checks if the folder represented by this FileDescriptor exists.
     * @return true if the folder exists, false otherwise.
     */
    public boolean folderExists() {
        return Files.exists(getFolderPath());
    }

    /**
     * Gets the last modified time of the file represented by this FileDescriptor.
     * @return The last modified time of the file.
     * @throws IOException If an I/O error occurs.
     */
    public FileTime getLastModifiedTime() throws IOException {
        return Files.getLastModifiedTime(getPath());
    }

    /**
     * Moves the file represented by this FileDescriptor to a new location specified by the new FileDescriptor.
     * @param newFileDescriptor The FileDescriptor representing the new location.
     * @return The new FileDescriptor after the move operation.
     * @throws IOException If an I/O error occurs during the move operation.
     */
    public FileDescriptor moveTo(FileDescriptor newFileDescriptor) throws IOException {
        if (newFileDescriptor == null) {
            throw new IllegalArgumentException("New FileDescriptor cannot be null");
        }

        newFileDescriptor.ensureDirectoriesExist();
        Files.move(getPath(), newFileDescriptor.getPath());

        return newFileDescriptor;
    }

    /**
     * Deletes the file represented by this FileDescriptor and purges empty parent folders.
     * @return The current FileDescriptor instance.
     */
    public FileDescriptor purge() {
        var path = getPath();
        try {
            log.debug("Deleting file: {}", path);
            Files.delete(path);
            purgeEmptyFolders(getFolderPath());
        } catch (IOException e) {
            log.warn("Error deleting file: " + path, e);
        }
        return this;
    }

    /**
     * Gets the folder path of the file represented by this FileDescriptor.
     * @return The folder path.
     */
    public Path getFolderPath() {
        return getPath().getParent();
    }

    /**
     * Gets the full path of the file represented by this FileDescriptor.
     * @return The full file path.
     */
    public Path getPath() {
        return rootFolderPath
                   .resolve(Path.of("", getHashStrings().toArray(new String[0])))
                   .resolve(id.toString());
    }

    /**
     * <p>Ensures that the directories for the file represented by this FileDescriptor exist.</p>
     * <p>If the directories do not exist, they will be created.</p>
     * @return The current FileDescriptor instance.
     * @throws IOException If an I/O error occurs while creating directories.
     */
    public FileDescriptor ensureDirectoriesExist() throws IOException {
        Files.createDirectories(getFolderPath());
        return this;
    }

    /**
     * Writes the given byte array to the file represented by this FileDescriptor.
     * @param data The byte array to write to the file.
     * @return The current FileDescriptor instance.
     * @throws IOException If an I/O error occurs while writing to the file.
     */
    public FileDescriptor write(byte[] data) throws IOException {
        Files.write(getPath(), data);
        return this;
    }

    private List<String> getHashStrings() {
        if (hashStrings != null) {
            return hashStrings;
        }

        if (id == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }

        try {
            var hashBytes = MessageDigest.getInstance(ALGORITHM)
                                .digest(id.toString().getBytes(StandardCharsets.UTF_8));
            var base64Hash = Base64.getUrlEncoder()
                                 .withoutPadding()
                                 .encodeToString(hashBytes);
            hashStrings = base64Hash
                              .substring(0, Math.min(base64Hash.length(), depth))
                              .chars()
                              .mapToObj(c -> String.valueOf((char) c))
                              .collect(Collectors.toList());
            return hashStrings;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(ALGORITHM + " algorithm not found", e);
        }
    }

    private void purgeEmptyFolders(Path path) {
        if (!path.startsWith(rootFolderPath)) {
            throw new IllegalArgumentException("Path " + path + " is not under the root folder " + rootFolderPath);
        }

        if (path.equals(rootFolderPath))
            return;

        try {
            log.debug("Deleting empty folder: {}", path);
            if (!Files.newDirectoryStream(path).iterator().hasNext()) {
                Files.delete(path);
                log.debug("Deleted empty folder: {}", path);
                purgeEmptyFolders(path.getParent());
            } else {
                log.debug("Folder is not empty, skipping deletion: {}", path);
            }
        } catch (IOException e) {
            log.warn("Error deleting empty folder: " + path, e);
        }
    }

    private FileDescriptor(Path rootFolderPath, UUID id, int depth) {
        this.rootFolderPath = rootFolderPath;
        this.id = id;
        this.depth = depth;
    }

}