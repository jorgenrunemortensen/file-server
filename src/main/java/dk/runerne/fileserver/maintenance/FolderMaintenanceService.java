package dk.runerne.fileserver.maintenance;

import dk.runerne.common.UUIDUtil;
import dk.runerne.fileserver.ConfigurationService;
import dk.runerne.fileserver.filehandling.FileDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for maintaining folder structure by processing misplaced files. It ensures files are located at the correct directory depth and removes redundant files.
 */
@Service
@Slf4j
public class FolderMaintenanceService {

    @Autowired private ConfigurationService configurationService;

    private Path dataRootFolder;
    private Integer requestedFileDepth;

    /**
     * Maintains the file at the specified path by ensuring it is at the correct depth and removing duplicates.
     *
     * @param filePath the path of the file to maintain.
     * @param depth    the current depth of the file.
     */
    public void maintainFile(Path filePath, int depth) {
        if (depth == getRequestedFileDepth()) {
            return; // File is already at the correct depth
        }

        if (!UUIDUtil.isValidUUID(filePath.getFileName().toString())) {
            log.warn(MessageFormat.format("Skipping misplaced file with invalid UUID name: {0}", filePath));
            return;
        }

        try {
            var fileBeingProcessed = FileDescriptor.fromPath(getDataRootFolder(), filePath);
            var candidates = findFilesByNameInHierarchy(getDataRootFolder(), filePath.getFileName().toString());
            var mostRecentCandidate = getMostRecentFileDescritptor(candidates);
            var targetPath = FileDescriptor.fromUUIDAndDepth(getDataRootFolder(), fileBeingProcessed.getId(), getRequestedFileDepth());
            moveToRequestedFileDepth(mostRecentCandidate, targetPath);
            var filesToDelete = makeSublistExcluding(candidates, targetPath);
            deleteFilesAndPurgeFolders(filesToDelete);
        } catch (IOException e) {
            log.error(MessageFormat.format("IOException while processing misplaced file: {0}", filePath), e);
        }
    }

    private static List<FileDescriptor> findFilesByNameInHierarchy(Path rootFolder, String fileName) throws IOException {
        return Files.walk(rootFolder)
                   .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals(fileName))
                   .map(path -> FileDescriptor.fromPath(rootFolder, path))
                   .collect(Collectors.toList());
    }

    private static FileDescriptor getMostRecentFileDescritptor(List<FileDescriptor> fileDescriptors) throws IOException {
        return fileDescriptors.stream()
                   .max((fd1, fd2) -> {
                       try {
                           var ft1 = Files.getLastModifiedTime(fd1.getPath());
                           var ft2 = Files.getLastModifiedTime(fd2.getPath());
                           return ft1.compareTo(ft2);
                       } catch (IOException e) {
                           throw new RuntimeException(e);
                       }
                   })
                   .orElse(null);
    }

    private static void moveToRequestedFileDepth(FileDescriptor file, FileDescriptor targetFile) throws IOException {
        log.debug(MessageFormat.format("Moving file {0} to requested depth path {1}", file.getPath(), targetFile.getPath()));
        Path targetFolder = targetFile.getFolderPath();
        if (!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }
        Files.move(file.getPath(), targetFile.getPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<FileDescriptor> makeSublistExcluding(List<FileDescriptor> list, FileDescriptor exclude) {
        return list.stream()
                   .filter(fd -> !fd.equals(exclude))
                   .collect(Collectors.toList());
    }

    private void deleteFilesAndPurgeFolders(Collection<FileDescriptor> fileDescriptors) throws IOException {
        fileDescriptors.forEach(fd -> {
            try {
                log.debug(MessageFormat.format("Deleting file: {0}", fd.getPath()));
                var path = fd.getPath();
                Files.deleteIfExists(path);
                deleteEmptyFoldersUpwards(path.getParent());
            } catch (IOException e) {
                log.warn(MessageFormat.format("Error deleting file: {0}", fd.getPath()), e);
            }
        });
    }

    private void deleteEmptyFoldersUpwards(Path startFolder) throws IOException {
        Path currentFolder = startFolder;
        while (currentFolder != null && !currentFolder.equals(dataRootFolder)) {
            try {
                if (Files.list(currentFolder).findAny().isEmpty()) {
                    log.debug(MessageFormat.format("Deleting empty folder: {0}", currentFolder));
                    Files.delete(currentFolder);
                    currentFolder = currentFolder.getParent();
                } else {
                    break;
                }
            } catch (IOException e) {
                log.warn(MessageFormat.format("Error deleting folder: {0}", currentFolder), e);
                break;
            }
        }
    }

    private Path getDataRootFolder() {
        if (dataRootFolder == null) {
            dataRootFolder = configurationService.getDataRootFolderPath();
        }
        return dataRootFolder;
    }

    private int getRequestedFileDepth() {
        if (requestedFileDepth == null) {
            requestedFileDepth = configurationService.getRequestedFileDepth();
        }
        return requestedFileDepth;
    }

}