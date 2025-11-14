package dk.runerne.fileserver.filehandling;

import dk.runerne.fileserver.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <p>Service for distributing files across different directory depths and managing orphaned files.</p>
 * <p>This service provides methods to clean up orphaned files, retrieve all file descriptors for a given UUID,
 * and ensure that files are stored at the correct directory depth as specified in the configuration.</p>
 */
@Service
@Slf4j
public class FileDistributorService {

    @Autowired private ConfigurationService configurationService;

    /**
     * Cleans up orphaned files for the given FileDescriptor, keeping only the specified depth from the configuration.
     *
     * @param fileDescriptor the FileDescriptor for which to clean up orphaned files.
     */
    public void cleanUpOrphans(FileDescriptor fileDescriptor) {
        cleanUpOrphans(fileDescriptor, configurationService.getRequestedFileDepth());
    }

    /**
     * Retrieves all FileDescriptors for the given UUID across all directory depths.
     *
     * @param id the UUID of the file.
     * @return a Set of FileDescriptors corresponding to the given UUID.
     */
    public Set<FileDescriptor> getAllFileDescriptorsForId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }

        Set<FileDescriptor> fileDescriptors = new HashSet<>();
        for (int level = 0; ; level++) {
            var candidate = FileDescriptor.fromUUIDAndDepth(configurationService.getDataRootFolderPath(), id, level);
            if (!candidate.folderExists()) {
                return fileDescriptors; // Stop if the folder does not exist
            }

            if (candidate.fileExists()) {
                fileDescriptors.add(candidate);
            }
        }
    }

    /**
     * Ensures that the given FileDescriptor is at the correct directory depth as specified in the configuration.
     * If not, it moves the file to the correct depth.
     *
     * @param fileDescriptor the FileDescriptor to check and potentially move.
     * @return the FileDescriptor at the correct directory depth.
     */
    public FileDescriptor ensureCorrectLevel(FileDescriptor fileDescriptor) {
        var requestedDepth = configurationService.getRequestedFileDepth();
        var path = fileDescriptor.getPath();

        log.debug("Ensuring right level for file: {}", path);
        if (fileDescriptor.getDepth() == requestedDepth)
            return fileDescriptor;

        log.info("Path {} does not match requested depth {}, normalizing to correct level", path, requestedDepth);
        var requestedFileDescriptor = fileDescriptor.toDepth(requestedDepth);
        try {
            requestedFileDescriptor.ensureDirectoriesExist();
            Files.deleteIfExists(requestedFileDescriptor.getPath());

            log.info("Moving file from {} to {}", path, requestedFileDescriptor.getPath());
            fileDescriptor.moveTo(requestedFileDescriptor);

            if (fileDescriptor.getDepth() > requestedDepth) {
                cleanUpSubFolders(requestedFileDescriptor.toDepth(requestedDepth));
            }

            return requestedFileDescriptor;
        } catch (IOException e) {
            return fileDescriptor;
        }
    }

    private static void cleanUpSubFolders(FileDescriptor fileDescriptor) throws IOException {
        int deepestLevel = findDeepestLevel(fileDescriptor);
        int targetDepth = fileDescriptor.getDepth();
        for (int level = deepestLevel; level > targetDepth; level--) {
            var lowerFileDescriptor = fileDescriptor.toDepth(level);
            var folderPath = lowerFileDescriptor.getPath().getParent();
            if (Files.list(folderPath).count() > 0) {
                return;
            }
            Files.delete(folderPath);
        }
    }

    private static int findDeepestLevel(FileDescriptor fileDescriptor) {
        for (int level = fileDescriptor.getDepth(); ; level++) {
            var lowerFileDescriptor = fileDescriptor.toDepth(level);
            if (!lowerFileDescriptor.folderExists()) {
                return level - 1;
            }
        }
    }

    private void cleanUpOrphans(FileDescriptor fileDescriptor, int depthToKeep) {
        if (fileDescriptor == null || fileDescriptor.getId() == null) {
            throw new IllegalArgumentException("FileDescriptor or its UUID cannot be null");
        }

        log.debug("Cleaning up orphans for UUID: {}, keeping depth: {}", fileDescriptor.getId(), depthToKeep);

        // Clean up upper levels
        for (int level = 0; level < depthToKeep; level++) {
            var candiateToBePurged = fileDescriptor.toDepth(level);
            if (candiateToBePurged.fileExists()) {
                candiateToBePurged.purge();
            }
        }

        // Clean up lower levels
        for (int level = depthToKeep + 1; ; level++) {
            var lowerFileDescriptor = fileDescriptor.toDepth(level);
            if (!lowerFileDescriptor.folderExists()) {
                return; // Stop if the folder does not exist
            }

            if (lowerFileDescriptor.fileExists()) {
                lowerFileDescriptor.purge();
            }
        }
    }

}