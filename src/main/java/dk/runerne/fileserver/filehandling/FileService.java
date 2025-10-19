package dk.runerne.fileserver.filehandling;

import dk.runerne.fileserver.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
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
        try {
            return FileDescriptor.fromDepth(configurationService.getDataRootFolderPath(), configurationService.getRequestedFileDepth())
                       .ensureDirectoriesExist()
                       .write(data)
                       .getId();
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
        try {
            var fileDescriptor = FileDescriptor.fromUUIDAndDepth(configurationService.getDataRootFolderPath(), id, configurationService.getRequestedFileDepth());
            if (!fileDescriptor.fileExists()) {
                throw new FileNotFoundException("File with ID " + id + " does not exist.");
            }
            fileDescriptor.write(data);
            fileDistributorService.cleanUpOrphans(fileDescriptor);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Error updating file with ID " + id, e);
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
        var fileDescriptors = fileDistributorService.getAllFileDescriptorsForId(id);
        if (fileDescriptors.isEmpty()) {
            throw new FileNotFoundException("File with ID " + id + " does not exist.");
        }

        try {
            var youngestFileDescriptor = fileDescriptors.stream()
                                             .max(Comparator.comparing(fileDescriptor -> {
                                                 try {
                                                     return fileDescriptor.getLastModifiedTime();
                                                 } catch (IOException e) {
                                                     throw new RuntimeException("Error getting last modified time for file " + fileDescriptor.getPath(), e);
                                                 }
                                             }));
            return fileDistributorService.ensureCorrectLevel(youngestFileDescriptor.get()).readAllBytes();
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

}