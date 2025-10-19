package dk.runerne.fileserver.filehandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileNotFoundException;
import java.util.UUID;

/**
 * <p>This controller handles HTTP requests for file operations such as create, read, update, and delete.</p>
 * <p>The base path for all endpoints in this controller is configured via the 'api.base-path' property.</p>
 * <p>The class implements the controller level and forwards all valid requests to the appropriate methods in {@link FileService}</p>.
 */
@RestController
@RequestMapping("${api.base-path}/file")
@Slf4j
public class FileController {

    /**
     * The file service used to perform file operations.
     */
    @Autowired FileService fileService;

    /**
     * Creates a new file with the provided data.
     *
     * @param data the data to be stored in the new file.
     * @return <p>a ResponseEntity containing the UUID of the created file and HTTP status.</p>
     * <p>The value must be used when later reading, updating and deleting the file.</p>
     */
    @PostMapping()
    public ResponseEntity<UUID> create(@RequestBody byte[] data) {
        var start = System.currentTimeMillis();
        try {
            UUID id = fileService.create(data);
            return ResponseEntity
                       .status(HttpStatus.CREATED)
                       .body(id);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            var duration = System.currentTimeMillis() - start;
            log.debug("File creation took {} ms", duration);
        }
    }

    /**
     * Reads the file with the specified UUID.
     *
     * @param id the UUID of the file to be read.
     * @return a ResponseEntity containing the file data and HTTP status.
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> read(@PathVariable("id") UUID id) {
        try {
            byte[] data = fileService.read(id);
            return ResponseEntity.ok(data);
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates the file with the specified UUID using the provided data.
     *
     * @param data the new data for the file.
     * @param id   the UUID of the file to be updated.
     * @return a ResponseEntity with HTTP status.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@RequestBody byte[] data, @PathVariable("id") UUID id) {
        try {
            fileService.update(id, data);
            return ResponseEntity.ok().build();
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes the file with the specified UUID.
     *
     * @param id the UUID of the file to be deleted.
     * @return a ResponseEntity with HTTP status.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        try {
            fileService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}