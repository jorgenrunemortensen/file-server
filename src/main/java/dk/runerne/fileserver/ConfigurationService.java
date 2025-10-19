package dk.runerne.fileserver;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Hold and provides the system configuration. Configuration values are available by calling the various getter-methods.
 */
@Service
public class ConfigurationService {

    /**
     * If nothing else is configured, this depth will be used.
     */
    private static final int DEFAULT_DEPTH = 4;

    /**
     * <p>The root folder where all data and configuration subfolders are located.</p>
     * <p>The default value is configured in application.properties or application.yml with the key 'app.root-folder'.</p>
     */
    @Value("${app.root-folder}")
    private String rootFolder;

    /**
     * <p>The subfolder under the root folder where all data is stored.</p>
     * <p>The default value is configured in application.properties or application.yml with the key 'app.data-subfolder'.</p>
     */
    @Value("${app.data-subfolder: data}")
    private String dataSubfolder;

    /**
     * <p>The subfolder under the root folder where all configuration files are stored.</p>
     * <p>The default value is configured in application.properties or application.yml with the key 'app.config-subfolder'.</p>
     */
    @Value("${app.config-subfolder: config}")
    private String configSubfolder;

    /**
     * <p>The filename under the config folder where the desired file depth is stored.</p>
     */
    @Value("${app.file-depth-filename}")
    private String fileDepthFilename;

    /**
     * <p>The default file depth to use if no file specifies a depth.</p>
     * <p>The default value is configured in application.properties or application.yml with the key 'app.default-file-depth'.</p>
     */
    @Value("${app.default-file-depth}")
    private Integer defaultFileDepth;

    /** <p>The maximum number of concurrent maintenance tasks allowed.</p>
     * <p>The value is configured in application.properties or application.yml with the key 'app.maintenance-max-concurrency'.</p>
     */
    @Getter
    @Value("${app.max-maintenance-concurrent-threads: 4}")
    private int maxMaintenanceConcurrentThreads;

    /**
     * Get the path to the data root folder.
     * @return The path to the data root folder.
     */
    public Path getDataRootFolderPath() {
        if (dataSubfolder == null || dataSubfolder.isEmpty()) {
            throw new IllegalStateException("Data subfolder is not configured. Please set 'app.data-subfolder' in application properties or application.yml.");
        }
        return Path.of(getRootFolder(), dataSubfolder);
    }

    /**
     * <p>Determines the requested file depth.</p>
     * <p>The file depth is determined by attempting to get the file depth information from different sources. This is done in the following order:</p>
     * <ol>
     *     <li>By reading the value from a file. (see {@link #getFileDepthFromFile()}).</li>
     *     <li>By reading the value from the configuration in <b>application.properties</b> or <b>application</b> (see {@link #defaultFileDepth}).</li>
     *     <li>Using the default value which is the value 4.</li>
     * </ol>
     * <p>The first source that provides a valid value is used.</p>
     * @return The requested file depth.
     */
    public int getRequestedFileDepth() {
        return getFileDepthFromFile()
                   .or(this::getDefaultConfiguredFileDepth)
                   .orElse(DEFAULT_DEPTH);
    }

    /**
     * Get the path to the config root folder. The config folder is located under the root folder on the path specified by {@link #configSubfolder}.
     * @return The path to the config root folder.
     */
    public Path getConfigRootFolderPath() {
        if (configSubfolder == null || configSubfolder.isEmpty()) {
            throw new IllegalStateException("Config subfolder is not configured. Please set 'app.config-subfolder' in application properties or application.yml.");
        }
        return Paths.get(getRootFolder(), configSubfolder);
    }

    /**
     * Get the root folder. The root folder is specified by {@link #rootFolder}.
     * @return The root folder.
     */
    private String getRootFolder() {
        if (rootFolder == null || rootFolder.isEmpty()) {
            throw new IllegalStateException("Root folder is not configured. Please set 'app.root-folder' in application properties or application.yml.");
        }
        return rootFolder;
    }

    /**
     * <p>Reads the file depth from a file located in the config folder. The file is named according to {@link #fileDepthFilename}.</p>
     * <p>The content of the file must be a text representing a positive integer or zero.</p>
     * @return An Optional containing the file depth if the file exists and is readable, otherwise an empty Optional.
     */
    private Optional<Integer> getFileDepthFromFile() {
        Path fileDepthPath = getConfigRootFolderPath().resolve(fileDepthFilename);
        if (!fileDepthPath.toFile().exists()) {
            return Optional.empty();
        }

        try {
            String depthString = new String(Files.readAllBytes(fileDepthPath)).trim();
            return Optional.of(Integer.parseInt(depthString));
        } catch (Exception e) {
            throw new RuntimeException("Error reading file depth from " + fileDepthFilename, e);
        }
    }

    /**
     * Get the default file depth from the configuration.
     * @return An Optional containing the default file depth if configured, otherwise an empty Optional.
     */
    private Optional<Integer> getDefaultConfiguredFileDepth() {
        return Optional.ofNullable(defaultFileDepth);
    }

}