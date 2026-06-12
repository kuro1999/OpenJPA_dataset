package it.uniroma2.isw2;

import it.uniroma2.isw2.csv.*;
import it.uniroma2.isw2.labeling.*;
import it.uniroma2.isw2.metric.ClassReleaseMetric;
import it.uniroma2.isw2.metric.ClassReleaseMetricCsvWriter;
import it.uniroma2.isw2.metric.MetricService;
import it.uniroma2.isw2.model.*;
import it.uniroma2.isw2.proportion.*;
import it.uniroma2.isw2.selector.ReleaseSelector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe principale del progetto.
 * Coordina le fasi iniziali della costruzione del dataset.
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final String PROJECT_NAME = "OPENJPA";
    private static final String RELEASES_FILE = PROJECT_NAME + "VersionInfo.csv";
    private static final String TICKETS_FILE = PROJECT_NAME + "Tickets.csv";
    private static final double RELEASES_TO_KEEP = 0.34;

    private static final String CLASS_RELEASE_METRICS_FILE =
            PROJECT_NAME + "_ClassReleaseMetrics.csv";

    private static final String FINAL_CLASS_RELEASE_LABELS_FILE =
            PROJECT_NAME + "_FinalClassReleaseLabels.csv";

    private static final String TICKET_FIX_COMMITS_FILE =
            PROJECT_NAME + "_TicketFixCommits.csv";

    private static final String TICKET_BUGGY_CLASSES_FILE =
            PROJECT_NAME + "_TicketBuggyClasses.csv";

    private static final String FINAL_DATASET_FILE =
            "dataset_" + PROJECT_NAME + ".csv";

    private static final String REPOSITORY_PATH_PROPERTY = "isw2.repository.path";
    private static final String REPOSITORY_PATH_ENV = "ISW2_REPOSITORY_PATH";

    private static final String SMELLS_FILE_PROPERTY = "isw2.smells.file";
    private static final String SMELLS_FILE_ENV = "ISW2_SMELLS_FILE";

    private static final Path DEFAULT_REPOSITORY_PATH = Path.of("openjpa");

    private static final Path DEFAULT_CLASS_RELEASE_SMELLS_FILE = Path.of(
            "output",
            "smells",
            PROJECT_NAME + "_ClassReleaseSmells.csv"
    );

    private Main() {
        // Utility class.
    }

    public static void main(String[] args) {
        Path projectRepoPath = resolveRepositoryPath(args);
        Path classReleaseSmellsFile = resolveClassReleaseSmellsFile(args);

        try {
            LOGGER.info(() -> "Avvio costruzione dataset del progetto " + PROJECT_NAME + ".");

            List<Release> allReleases = ReleaseCsvReader.loadReleases(RELEASES_FILE);
            LOGGER.info(() -> "Release lette: " + allReleases.size());

            List<Release> selectedReleases =
                    ReleaseSelector.selectInitialReleases(allReleases, RELEASES_TO_KEEP);
            LOGGER.info(() -> "Release selezionate per il dataset finale: " + selectedReleases.size());

            List<Ticket> tickets = TicketCsvReader.loadTickets(TICKETS_FILE);
            LOGGER.info(() -> "Ticket letti: " + tickets.size());

            List<EnhancedTicket> enhancedTickets =
                    TicketVersionEnricher.enrichTickets(tickets, allReleases);
            LOGGER.info("Ticket arricchiti creati.");

            List<EnhancedTicket> avBasedTickets =
                    AffectedVersionIVResolver.assignInitialIVFromAV(enhancedTickets, allReleases);
            LOGGER.info("Ticket con IV iniziale da AV creati.");

            double proportion =
                    ProportionService.calculateAverageProportion(avBasedTickets, allReleases);
            LOGGER.info(() -> "Proportion media calcolata: " + proportion);

            List<EnhancedTicket> estimatedTickets =
                    ProportionService.estimateMissingInjectedVersions(
                            avBasedTickets,
                            allReleases,
                            proportion
                    );
            LOGGER.info("Ticket con IV stimata creati.");

            LOGGER.info("Selezione release + fase AV -> IV -> P completate con successo.");

            Map<String, String> computedAvByTicketId =
                    ProportionService.buildComputedAvMap(estimatedTickets, allReleases);
            LOGGER.info(() -> "Ticket con ComputedAV costruiti in memoria: "
                    + computedAvByTicketId.size());

            if (!csvExists(FINAL_CLASS_RELEASE_LABELS_FILE)) {
                buildClassReleaseLabels(
                        estimatedTickets,
                        selectedReleases,
                        computedAvByTicketId,
                        projectRepoPath
                );
            }

            List<ReleaseJavaClass> releaseJavaClasses =
                    ReleaseInventoryService.buildReleaseInventory(
                            PROJECT_NAME,
                            selectedReleases,
                            projectRepoPath.toString()
                    );
            LOGGER.info(() -> "Coppie classe-release generate: " + releaseJavaClasses.size());

            LOGGER.info(() -> "File finale delle label presente: " + FINAL_CLASS_RELEASE_LABELS_FILE);

            computeMetricsIfNeeded(
                    selectedReleases,
                    releaseJavaClasses,
                    projectRepoPath
            );

            LOGGER.info(() -> "Working directory: " + Path.of("").toAbsolutePath());

            if (!requiredCsvFilesExist(classReleaseSmellsFile)) {
                return;
            }

            FinalDatasetMerger.buildFinalDataset(
                    CLASS_RELEASE_METRICS_FILE,
                    FINAL_CLASS_RELEASE_LABELS_FILE,
                    classReleaseSmellsFile.toString(),
                    FINAL_DATASET_FILE
            );

            LOGGER.info(() -> "Dataset finale creato: " + FINAL_DATASET_FILE);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore durante l'esecuzione del flusso principale.", e);
        }
    }

    private static void buildClassReleaseLabels(List<EnhancedTicket> estimatedTickets,
                                                List<Release> selectedReleases,
                                                Map<String, String> computedAvByTicketId,
                                                Path projectRepoPath) throws IOException {
        List<TicketFixCommit> ticketFixCommits =
                BuggyClassService.findFixCommits(
                        estimatedTickets,
                        projectRepoPath.toString()
                );

        TicketFixCommitCsvWriter.writeTicketFixCommits(
                TICKET_FIX_COMMITS_FILE,
                ticketFixCommits
        );

        LOGGER.info(() -> "File ticket-fix commit creato: " + TICKET_FIX_COMMITS_FILE);

        List<TicketBuggyClass> ticketBuggyClasses =
                BuggyClassService.extractBuggyClasses(
                        ticketFixCommits,
                        projectRepoPath.toString()
                );

        TicketBuggyClassCsvWriter.writeTicketBuggyClasses(
                TICKET_BUGGY_CLASSES_FILE,
                ticketBuggyClasses
        );

        LOGGER.info(() -> "File ticket-buggy classes creato: " + TICKET_BUGGY_CLASSES_FILE);

        List<ReleaseJavaClass> releaseJavaClasses =
                ReleaseInventoryService.buildReleaseInventory(
                        PROJECT_NAME,
                        selectedReleases,
                        projectRepoPath.toString()
                );

        LOGGER.info(() -> "Coppie classe-release generate: " + releaseJavaClasses.size());

        FinalDatasetCsvWriter.writeFinalDataset(
                FINAL_CLASS_RELEASE_LABELS_FILE,
                releaseJavaClasses,
                selectedReleases,
                computedAvByTicketId,
                ticketBuggyClasses
        );

        LOGGER.info(() -> "File finale classe-release yes/no creato: "
                + FINAL_CLASS_RELEASE_LABELS_FILE);
    }

    private static void computeMetricsIfNeeded(List<Release> selectedReleases,
                                               List<ReleaseJavaClass> releaseJavaClasses,
                                               Path projectRepoPath) throws IOException {
        if (!csvExists(CLASS_RELEASE_METRICS_FILE)) {
            MetricService metricService =
                    new MetricService(
                            PROJECT_NAME,
                            projectRepoPath,
                            Path.of(TICKET_FIX_COMMITS_FILE)
                    );

            List<ClassReleaseMetric> classReleaseMetrics =
                    metricService.computeMetrics(
                            selectedReleases,
                            releaseJavaClasses
                    );

            ClassReleaseMetricCsvWriter.writeClassReleaseMetrics(
                    CLASS_RELEASE_METRICS_FILE,
                    classReleaseMetrics
            );

            LOGGER.info(() -> "File metriche classe-release creato: "
                    + CLASS_RELEASE_METRICS_FILE);
        } else {
            LOGGER.info(() -> "File metriche classe-release già presente: "
                    + CLASS_RELEASE_METRICS_FILE);
            LOGGER.info("Calcolo metriche saltato.");
        }
    }

    private static boolean requiredCsvFilesExist(Path classReleaseSmellsFile) throws IOException {
        if (!csvExists(CLASS_RELEASE_METRICS_FILE)) {
            LOGGER.warning(() -> "File metriche mancante: "
                    + Path.of(CLASS_RELEASE_METRICS_FILE).toAbsolutePath());
            return false;
        }

        if (!csvExists(FINAL_CLASS_RELEASE_LABELS_FILE)) {
            LOGGER.warning(() -> "File label mancante: "
                    + Path.of(FINAL_CLASS_RELEASE_LABELS_FILE).toAbsolutePath());
            return false;
        }

        if (!csvExists(classReleaseSmellsFile.toString())) {
            LOGGER.warning(() -> "File smell mancante: "
                    + classReleaseSmellsFile.toAbsolutePath());
            LOGGER.warning(() -> "Controlla che il file si chiami esattamente: "
                    + classReleaseSmellsFile);
            return false;
        }

        return true;
    }

    private static Path resolveRepositoryPath(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]);
        }

        return resolveConfiguredPath(
                REPOSITORY_PATH_PROPERTY,
                REPOSITORY_PATH_ENV,
                DEFAULT_REPOSITORY_PATH
        );
    }

    private static Path resolveClassReleaseSmellsFile(String[] args) {
        if (args.length > 1 && !args[1].isBlank()) {
            return Path.of(args[1]);
        }

        return resolveConfiguredPath(
                SMELLS_FILE_PROPERTY,
                SMELLS_FILE_ENV,
                DEFAULT_CLASS_RELEASE_SMELLS_FILE
        );
    }

    private static Path resolveConfiguredPath(String propertyName,
                                              String environmentName,
                                              Path defaultPath) {
        String propertyValue = System.getProperty(propertyName);

        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue);
        }

        String environmentValue = System.getenv(environmentName);

        if (environmentValue != null && !environmentValue.isBlank()) {
            return Path.of(environmentValue);
        }

        return defaultPath;
    }

    private static boolean csvExists(String filePath) throws IOException {
        Path path = Path.of(filePath);
        return Files.isRegularFile(path) && Files.size(path) > 0;
    }
}