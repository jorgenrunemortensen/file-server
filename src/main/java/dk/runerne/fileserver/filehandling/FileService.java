package dk.runerne.fileserver.filehandling;

import dk.runerne.fileserver.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.UUID;

/**
 * <p>Service for handling file operations such as create, read, update, and delete.</p>
 * <p>This service interacts with the file system based on configurations provided by {@link ConfigurationService}
 * and utilizes {@link FileDistributorService} for managing file distribution and orphan cleanup.</p>
 */
@Service
@Slf4j
public class FileService {

    @Autowired private ConfigurationService configurationService;
    @Autowired private FileDistributorService fileDistributorService;

    /**
     * <p>Creates a new file with the given data and returns its UUID.</p>
     * <p>The UUID is randomly generated.</p>
     *
     * @param data the data to be written to the file.
     * @return the UUID of the newly created file.
     */
    public UUID create(byte[] data) {
        return create(data, null);
    }

    /**
     * <p>Creates a new file with the given data and optional content type metadata, and returns its UUID.</p>
     * <p>The UUID is randomly generated.</p>
     *
     * @param data        the data to be written to the file.
     * @param contentType optional MIME type to store alongside the file, or {@code null}.
     * @return the UUID of the newly created file.
     */
    public UUID create(byte[] data, String contentType) {
        try {
            FileDescriptor descriptor = FileDescriptor.fromDepth(
                configurationService.getDataRootFolderPath(),
                configurationService.getRequestedFileDepth()
            )
                .ensureDirectoriesExist()
                .write(data);
            if (contentType != null && !contentType.isBlank()) {
                descriptor.writeContentType(contentType);
            }
            return descriptor.getId();
        } catch (IOException e) {
            throw new RuntimeException("Error creating file", e);
        }
    }

    /**
     * <p>Updates the file with the specified UUID with new data.</p>
     * <p>If the file does not exist, a {@link FileNotFoundException} is thrown.</p>
     *
     * @param id   the UUID of the file to be updated.
     * @param data the new data to be written to the file.
     * @throws FileNotFoundException if the file with the specified UUID does not exist.
     */
    public void update(UUID id, byte[] data) throws FileNotFoundException {
        update(id, data, null);
    }

    /**
     * <p>Updates the file with the specified UUID with new data and optional content type metadata.</p>
     * <p>If the file does not exist, a {@link FileNotFoundException} is thrown.</p>
     *
     * @param id          the UUID of the file to be updated.
     * @param data        the new data to be written to the file.
     * @param contentType optional MIME type to store alongside the file, or {@code null} to clear stored metadata.
     * @throws FileNotFoundException if the file with the specified UUID does not exist.
     */
    public void update(UUID id, byte[] data, String contentType) throws FileNotFoundException {
        try {
            var fileDescriptor = FileDescriptor.fromUUIDAndDepth(
                configurationService.getDataRootFolderPath(),
                id,
                configurationService.getRequestedFileDepth()
            );
            if (!fileDescriptor.fileExists()) {
                throw new FileNotFoundException("File with ID " + id + " does not exist.");
            }
            fileDescriptor.write(data);
            fileDescriptor.writeContentType(contentType);
            fileDistributorService.cleanUpOrphans(fileDescriptor);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Error updating file with ID " + id, e);
        }
    }

    /**
     * <p>Resolves the file with the specified UUID into a streamable {@link StoredFile} metadata snapshot
     * without loading the file contents into memory.</p>
     *
     * @param id                  the UUID of the file to resolve.
     * @param contentTypeOverride optional Content-Type override requested by the caller.
     * @return a {@link StoredFile} referencing the file on disk along with size, ETag, last-modified time, and media type.
     * @throws FileNotFoundException    if the file with the specified UUID does not exist.
     * @throws IllegalArgumentException if {@code contentTypeOverride} is provided but is not a valid concrete media type.
     */
    public StoredFile resolve(UUID id, String contentTypeOverride) throws FileNotFoundException {
        if (contentTypeOverride != null && !contentTypeOverride.isBlank()) {
            ContentTypeDetector.parseConcreteMediaType(contentTypeOverride);
        }

        FileDescriptor normalizedDescriptor = resolveNormalizedDescriptor(id);
        try {
            Path path = normalizedDescriptor.getPath();
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            String storedContentType = normalizedDescriptor.readContentType().orElse(null);
            MediaType mediaType = ContentTypeDetector.resolve(path, storedContentType, contentTypeOverride);
            String eTag = StoredFile.computeETag(attributes.size(), attributes.lastModifiedTime());
            return new StoredFile(
                id,
                path,
                attributes.size(),
                attributes.lastModifiedTime().toInstant(),
                mediaType,
                eTag
            );
        } catch (IOException e) {
            throw new RuntimeException("Error resolving file with ID " + id, e);
        }
    }

    /**
     * <p>Reads the file with the specified UUID and returns its data.</p>
     * <p>If the file does not exist, a {@link FileNotFoundException} is thrown.</p>
     *
     * @param id the UUID of the file to be read.
     * @return the data of the file as a byte array.
     * @throws FileNotFoundException if the file with the specified UUID does not exist.
     */
    public byte[] read(UUID id) throws FileNotFoundException {
        try {
            return resolveNormalizedDescriptor(id).readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error reading file with ID " + id, e);
        }
    }

    /**
     * <p>Deletes the file with the specified UUID.</p>
     * <p>If the file does not exist, a {@link FileNotFoundException} is thrown.</p>
     *
     * @param id the UUID of the file to be deleted.
     * @throws FileNotFoundException if the file with the specified UUID does not exist.
     */
    public void delete(UUID id) throws FileNotFoundException {
        var fileDescriptors = fileDistributorService.getAllFileDescriptorsForId(id);
        if (fileDescriptors.isEmpty()) {
            throw new FileNotFoundException("File with ID " + id + " does not exist.");
        }

        fileDescriptors.forEach(FileDescriptor::purge);
    }

    private FileDescriptor resolveNormalizedDescriptor(UUID id) throws FileNotFoundException {
        var fileDescriptors = fileDistributorService.getAllFileDescriptorsForId(id);
        if (fileDescriptors.isEmpty()) {
            throw new FileNotFoundException("File with ID " + id + " does not exist.");
        }

        var youngestFileDescriptor = fileDescriptors.stream()
            .max(Comparator.comparing(fileDescriptor -> {
                try {
                    return fileDescriptor.getLastModifiedTime();
                } catch (IOException e) {
                    throw new RuntimeException("Error getting last modified time for file " + fileDescriptor.getPath(), e);
                }
            }))
            .orElseThrow(() -> new FileNotFoundException("File with ID " + id + " does not exist."));

        return fileDistributorService.ensureCorrectLevel(youngestFileDescriptor);
    }

}
