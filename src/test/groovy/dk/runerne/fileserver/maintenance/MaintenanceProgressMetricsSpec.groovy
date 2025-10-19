package dk.runerne.fileserver.maintenance

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

@Unroll
class MaintenanceProgressMetricsSpec extends Specification {

    private static final Instant T1 = Instant.parse("2024-01-01T10:00:00Z")
    private static final Instant T2 = Instant.parse("2024-01-01T10:00:10Z")
    private static final Instant T3 = Instant.parse("2024-01-01T10:01:40Z")

    void "getElapsedTime - #scenarie"() {
        given:
        MaintenanceProgressMetrics maintenanceProgressMetrics = new MaintenanceProgressMetrics(startTime, statusTime, numberToProcess, numberProcessed)

        when:
        Duration output = maintenanceProgressMetrics.elapsedTime

        then:
        output == expectedOutput

        where:
        startTime | statusTime | numberToProcess | numberProcessed || expectedOutput         | scenarie
        null      | T2         | 1000            | 100             || null                   | 'No start time'
        T1        | null       | 1000            | 100             || null                   | 'No status time'
        null      | null       | 1000            | 100             || null                   | 'No start time and no status time'
        T1        | T2         | 1000            | 100             || Duration.ofSeconds(10) | 'Normal case'
        T1        | T2         | 1000            | 1000            || Duration.ofSeconds(10) | 'All processed'
    }

    void "getEstimatedCompletionTime - #scenarie"() {
        given:
        MaintenanceProgressMetrics maintenanceProgressMetrics = new MaintenanceProgressMetrics(startTime, statusTime, numberToProcess, numberProcessed)

        when:
        Instant output = maintenanceProgressMetrics.estimatedCompletionTime

        then:
        output == expectedOutput

        where:
        startTime | statusTime | numberToProcess | numberProcessed || expectedOutput | scenarie
        null      | T2         | 1000            | 100             || null           | 'No start time'
        T1        | null       | 1000            | 100             || null           | 'No status time'
        T1        | T2         | 0               | 100             || null           | 'No items to process'
        T1        | T2         | 1000            | 100             || T3             | 'Normal case'
    }

    void "getRemainingTime - #scenarie"() {
        given:
        MaintenanceProgressMetrics maintenanceProgressMetrics = new MaintenanceProgressMetrics(startTime, statusTime, numberToProcess, numberProcessed)

        when:
        Duration output = maintenanceProgressMetrics.remainingTime

        then:
        output == expectedOutput

        where:
        startTime | statusTime | numberToProcess | numberProcessed || expectedOutput         | scenarie
        null      | T2         | 1000            | 100             || null                   | 'No start time'
        T1        | null       | 1000            | 100             || null                   | 'No status time'
        T1        | T2         | 0               | 100             || null                   | 'No items to process'
        T1        | T2         | 1000            | 100             || Duration.ofSeconds(90) | 'Normal case'
    }

    void "getProgressRate - #scenarie"() {
        given:
        MaintenanceProgressMetrics maintenanceProgressMetrics = new MaintenanceProgressMetrics(startTime, statusTime, numberToProcess, numberProcessed)

        when:
        Double output = maintenanceProgressMetrics.progressRate

        then:
        output == expectedOutput

        where:
        startTime | statusTime | numberToProcess | numberProcessed || expectedOutput | scenarie
        null      | T2         | 1000            | 100             || 0.0            | 'No start time'
        T1        | null       | 1000            | 100             || 0.0            | 'No status time'
        T1        | T2         | 0               | 100             || 0.0            | 'No items to process'
        T1        | T2         | 1000            | 100             || 0.01           | 'Normal case'
    }

    void "getProgressBar - #scenarie"() {
        given:
        MaintenanceProgressMetrics maintenanceProgressMetrics = new MaintenanceProgressMetrics(T1, T2, numberToProcess, numberProcessed)

        when:
        String output = maintenanceProgressMetrics.progressBar

        then:
        output == expectedOutput

        where:
        numberToProcess | numberProcessed || expectedOutput                                                                                         | scenarie
        800             | 0               || "                                                                                                    " | '0   / 800'
        800             | 80              || "██████████                                                                                          " | '80  / 800'
        800             | 100             || "████████████▌                                                                                       " | '100 / 800'
        800             | 120             || "███████████████                                                                                     " | '120 / 800'
        800             | 121             || "███████████████▏                                                                                    " | '121 / 800'
        800             | 122             || "███████████████▎                                                                                    " | '122 / 800'
        800             | 123             || "███████████████▍                                                                                    " | '123 / 800'
        800             | 124             || "███████████████▌                                                                                    " | '124 / 800'
        800             | 125             || "███████████████▋                                                                                    " | '125 / 800'
        800             | 126             || "███████████████▊                                                                                    " | '126 / 800'
        800             | 127             || "███████████████▉                                                                                    " | '127 / 800'
        800             | 128             || "████████████████                                                                                    " | '128 / 800'
        800             | 129             || "████████████████▏                                                                                   " | '129 / 800'
        800             | 400             || "██████████████████████████████████████████████████                                                  " | '400 / 800'
        800             | 800             || "████████████████████████████████████████████████████████████████████████████████████████████████████" | '800 / 800'
    }

}