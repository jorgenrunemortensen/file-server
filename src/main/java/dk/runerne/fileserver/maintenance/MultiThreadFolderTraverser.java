package dk.runerne.fileserver.maintenance;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>Utility for traversing a folder structure in a multi-threaded manner.</p>
 * <p>This class allows for concurrent processing of files within a specified folder structure, enabling efficient handling of large datasets. It supports processing files at a specified depth and
 * provides mechanisms to track progress and terminate the traversal.</p>
 */
@Slf4j
public class MultiThreadFolderTraverser {

    private final Path dataRootFolder;
    private final ExecutorService pool;
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private final BiConsumer<Path, Integer> fileProcessor;

    private final AtomicInteger filesProcessed = new AtomicInteger(0);
    private final AtomicInteger filesToProcess = new AtomicInteger(0);

    private Instant startTime;

    /**
     * Constructs a MultiThreadFolderTraverser.
     *
     * @param dataRootFolder     the root folder to start traversal from.
     * @param maxConcurrency     the maximum number of concurrent threads to use.
     * @param fileProcessor      a consumer to process misplaced files.
     * @param completionCallback a runnable to execute upon completion of the traversal.
     */
    public MultiThreadFolderTraverser(
        Path dataRootFolder,
        int maxConcurrency,
        BiConsumer<Path, Integer> fileProcessor,
        Runnable completionCallback
    ) {
        this.dataRootFolder = dataRootFolder;
        pool = Executors.newFixedThreadPool(maxConcurrency);
        this.fileProcessor = fileProcessor;
        finished.thenRun(completionCallback);
    }

    /**
     * Starts the folder traversal.
     *
     * @return the current instance of MultiThreadFolderTraverser.
     */
    public MultiThreadFolderTraverser start() {
        this.startTime = Instant.now();
        filesToProcess.set(countTotalNumberOfFiles(dataRootFolder));
        submitJob(dataRootFolder, 0, 1.0);
        return this;
    }

    /**
     * Terminates the folder traversal.
     */
    public void terminate() {
        pool.shutdownNow();
        try {
            pool.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES);
            activeJobs.set(0);
            finished.complete(null);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the current maintenance status.
     *
     * @return the current MaintenanceJobStatus.
     */
    public MaintenanceJobStatus getMaintenanceStatus() {
        return MaintenanceJobStatus.maintainingWithMetrics(
            getMaintenanceState(),
            new MaintenanceProgressMetrics(
                startTime,
                Instant.now(),
                filesToProcess.get(),
                filesProcessed.get()
            )
        );
    }

    private static int countTotalNumberOfFiles(Path path) {
        try (var stream = Files.walk(path)) {
            return (int) (stream.filter(p -> Files.isRegularFile(p) || Files.isDirectory(p)).count());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MaintenanceJobState getMaintenanceState() {
        return activeJobs.get() > 0
                   ? MaintenanceJobState.MAINTAINING
                   : MaintenanceJobState.IDLE;
    }

    private void submitJob(Path folder, int depth, double part) {
        activeJobs.incrementAndGet();
        pool.submit(() -> {
            try {
                processFolder(folder, depth, part);
            } finally {
                if (activeJobs.decrementAndGet() == 0) {
                    finished.complete(null);
                }
            }
        });
    }

    private void processFolder(Path folder, int depth, double part) {
        try {
            var files = Files.list(folder).collect(Collectors.toList());
            Collections.shuffle(files);
            var itemRate = part / files.size();
            files.forEach(path -> {
                if (Files.isDirectory(path)) {
                    submitJob(path, depth + 1, itemRate);
                } else {
                    fileProcessor.accept(path, depth);
                }
                filesProcessed.incrementAndGet();
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}