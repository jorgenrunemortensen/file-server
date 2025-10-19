package dk.runerne.fileserver

import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path

@Unroll
class ConfigurationServiceSpec extends Specification {

    private ConfigurationService configurationService = new ConfigurationService()

    void 'getDataRootFolderPath - OK'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'rootFolder', '/root')
        ReflectionTestUtils.setField(configurationService, 'dataSubfolder', '/subfolder')

        when:
        String output = configurationService.getDataRootFolderPath()

        then:
        output == "${File.separator}root${File.separator}subfolder"
    }

    void "getDataRootFolderPath - No dataSubfolder - #scenarie"() {
        given:
        ReflectionTestUtils.setField(configurationService, 'rootFolder', '/root')
        ReflectionTestUtils.setField(configurationService, 'dataSubfolder', dataSubfolder)

        when:
        configurationService.getDataRootFolderPath()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message == 'Data subfolder is not configured. Please set \'app.data-subfolder\' in application properties or application.yml.'

        where:
        dataSubfolder || scenarie
        null          || 'null'
        ''            || 'empty'
    }

    void 'getRequestedFileDepth - Default hard value'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'rootFolder', '/root')
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config-subfolder')
        ReflectionTestUtils.setField(configurationService, 'fileDepthFilename', 'file-depth.txt')

        when:
        int output = configurationService.getRequestedFileDepth()

        then:
        output == 4
    }

    void 'getRequestedFileDepth - Default configured value'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'defaultFileDepth', 5)
        ReflectionTestUtils.setField(configurationService, 'rootFolder', '/root')
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config-subfolder')
        ReflectionTestUtils.setField(configurationService, 'fileDepthFilename', 'file-depth.txt')

        when:
        int output = configurationService.getRequestedFileDepth()

        then:
        output == 5
    }

    void 'getRequestedFileDepth - From file - OK'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'rootFolder', 'src/test/resources')
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config')
        ReflectionTestUtils.setField(configurationService, 'fileDepthFilename', 'file-depth.txt')

        when:
        int output = configurationService.getRequestedFileDepth()

        then:
        output == 6
    }

    void 'getRequestedFileDepth - From file - Illegal content'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'rootFolder', 'src/test/resources')
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config')
        ReflectionTestUtils.setField(configurationService, 'fileDepthFilename', 'file-depth-illegal.txt')

        when:
        configurationService.getRequestedFileDepth()

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == 'Error reading file depth from file-depth-illegal.txt'
        e.cause instanceof NumberFormatException
        e.cause.message == 'For input string: "This is not a number"'
    }

    void 'getRequestedFileDepth - From non-existing file'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'rootFolder', 'src/test/resources')
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config')
        ReflectionTestUtils.setField(configurationService, 'fileDepthFilename', 'non-existing.txt')

        when:
        int output = configurationService.getRequestedFileDepth()

        then:
        output == 4
    }

    void "getConfiguredRootFolderPath - configuredSubfolder is #scenarie"() {
        given:
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', configSubfolder)

        when:
        configurationService.getConfigRootFolderPath()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message == 'Config subfolder is not configured. Please set \'app.config-subfolder\' in application properties or application.yml.'

        where:
        configSubfolder || scenarie
        null            || 'null'
        ''              || 'empty'
    }

    void "getConfiguredRootFolderPath - root folder is #scenarie"() {
        given:
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config-subfolder')
        ReflectionTestUtils.setField(configurationService, 'rootFolder', rootFolder)

        when:
        configurationService.getConfigRootFolderPath()

        then:
        IllegalStateException e = thrown(IllegalStateException)
        e.message == 'Root folder is not configured. Please set \'app.root-folder\' in application properties or application.yml.'

        where:
        rootFolder || scenarie
        null       || 'null'
        ''         || 'empty'
    }

    void 'getConfiguredRootFolderPath - OK'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'configSubfolder', 'config-subfolder')
        ReflectionTestUtils.setField(configurationService, 'rootFolder', 'rootFolder')

        when:
        Path output = configurationService.getConfigRootFolderPath()

        then:
        output == Path.of("rootFolder${File.separator}config-subfolder")
    }

    void 'getMaxMaintenanceConcurrentThreads'() {
        given:
        ReflectionTestUtils.setField(configurationService, 'maxMaintenanceConcurrentThreads', 10)

        when:
        int output = configurationService.getMaxMaintenanceConcurrentThreads()

        then:
        output == 10
    }

}