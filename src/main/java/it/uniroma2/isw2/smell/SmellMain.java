package it.uniroma2.isw2.smell;

import it.uniroma2.isw2.csv.ReleaseCsvReader;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.proportion.DateUtils;
import it.uniroma2.isw2.selector.ReleaseSelector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline autonoma per il calcolo di NSmells.
 *
 * La pipeline:
 * - legge le release del progetto;
 * - seleziona il primo 34% delle release;
 * - effettua checkout allo snapshot di ogni release selezionata;
 * - esegue PMD sulle classi Java di produzione;
 * - produce un CSV finale con NSmells per ogni coppia classe-release.
 */
public class SmellMain {

    private static final String PROJECT_NAME = "OPENJPA";
    private static final String RELEASES_FILE = PROJECT_NAME + "VersionInfo.csv";
    private static final double RELEASES_TO_KEEP = 0.34;

    private static final DateTimeFormatter GIT_BEFORE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Path REPOSITORY_PATH = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\openjpa"
    );

    private static final Path PMD_EXECUTABLE = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\PMD\\pmd-dist-7.24.0-bin\\pmd-bin-7.24.0\\bin\\pmd.bat"
    );

    private static final Path PMD_RULESET_PATH = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\isw2-dataset-openjpa\\pmd-config\\pmd-ruleset.xml"
    );

    private static final Path PMD_FILE_LISTS_DIRECTORY = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\isw2-dataset-openjpa\\pmd-config\\pmd-filelists"
    );

    private static final Path PMD_REPORTS_DIRECTORY = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\isw2-dataset-openjpa\\pmd-config\\pmd-reports"
    );

    private static final Path RELEASE_SMELLS_DIRECTORY = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\isw2-dataset-openjpa\\output\\smells\\by-release"
    );

    private static final Path FINAL_SMELLS_FILE = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\isw2-dataset-openjpa\\output\\smells\\OPENJPA_ClassReleaseSmells.csv"
    );

    private SmellMain() {
    }

    public static void main(String[] args) {
        String originalGitRef = null;

        try {
            System.out.println("Avvio pipeline smell per il progetto " + PROJECT_NAME + ".");

            originalGitRef = getCurrentGitRef();

            List<Release> allReleases = ReleaseCsvReader.loadReleases(RELEASES_FILE);
            System.out.println("Release lette: " + allReleases.size());

            List<Release> selectedReleases =
                    ReleaseSelector.selectInitialReleases(allReleases, RELEASES_TO_KEEP);
            System.out.println("Release selezionate per gli smell: " + selectedReleases.size());

            initializeFinalSmellsFile();

            for (Release release : selectedReleases) {
                computeSmellsForRelease(release, originalGitRef);
            }

            System.out.println("CSV finale smell generato in: " + FINAL_SMELLS_FILE);
            System.out.println("Pipeline smell completata correttamente.");

        } catch (IOException | InterruptedException e) {
            System.err.println("Errore durante la pipeline smell: " + e.getMessage());
            e.printStackTrace();

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

        } finally {
            restoreOriginalGitRef(originalGitRef);
        }
    }

    private static void computeSmellsForRelease(Release release, String baseGitRef)
            throws IOException, InterruptedException {

        int releaseId = Integer.parseInt(release.getVersionId());

        System.out.println("Calcolo smell per release "
                + releaseId
                + " - "
                + release.getVersionName()
                + " - "
                + release.getDate());

        String snapshotCommitHash = findLastCommitOfReleaseDay(release.getDate(), baseGitRef);

        if (snapshotCommitHash.isBlank()) {
            System.out.println("Nessuno snapshot trovato per release " + releaseId + ". Release saltata.");
            return;
        }

        checkoutSnapshot(snapshotCommitHash);

        Path fileListPath = PMD_FILE_LISTS_DIRECTORY.resolve(
                PROJECT_NAME + "_release_" + releaseId + "_production-files.txt"
        );

        Path releaseOutputPath = RELEASE_SMELLS_DIRECTORY.resolve(
                PROJECT_NAME + "_ClassReleaseSmells_release_" + releaseId + ".csv"
        );

        ProductionJavaFileListBuilder fileListBuilder = new ProductionJavaFileListBuilder();

        List<String> productionJavaFiles = fileListBuilder.findProductionJavaFiles(REPOSITORY_PATH);
        fileListBuilder.writeFileList(fileListPath, productionJavaFiles);

        System.out.println("Release " + releaseId
                + " - file Java di produzione trovati: "
                + productionJavaFiles.size());

        PmdRunner pmdRunner = new PmdRunner(
                PMD_EXECUTABLE,
                PMD_RULESET_PATH,
                PMD_REPORTS_DIRECTORY
        );

        Path pmdReportPath = pmdRunner.run(releaseId, fileListPath);

        PmdSmellCsvBuilder smellCsvBuilder = new PmdSmellCsvBuilder();

        smellCsvBuilder.build(
                PROJECT_NAME,
                releaseId,
                REPOSITORY_PATH,
                fileListPath,
                pmdReportPath,
                releaseOutputPath
        );

        appendReleaseSmellsToFinalFile(releaseOutputPath);

        System.out.println("Release " + releaseId
                + " - smell completati. CSV parziale: "
                + releaseOutputPath);
    }

    private static String findLastCommitOfReleaseDay(String releaseDate, String baseGitRef)
            throws IOException, InterruptedException {

        LocalDateTime endExclusive = DateUtils.parseReleaseDate(releaseDate).plusDays(1);
        String formattedDate = endExclusive.format(GIT_BEFORE_FORMAT);

        List<String> lines = runGitCommand(
                "git",
                "rev-list",
                "-n",
                "1",
                "--before=" + formattedDate,
                "--first-parent",
                baseGitRef
        );

        if (lines.isEmpty()) {
            return "";
        }

        return lines.get(0).trim();
    }

    private static void checkoutSnapshot(String commitHash) throws IOException, InterruptedException {
        runGitCommand(
                "git",
                "checkout",
                "-f",
                commitHash
        );
    }

    private static String getCurrentGitRef() throws IOException, InterruptedException {
        List<String> branchLines = runGitCommand(
                "git",
                "rev-parse",
                "--abbrev-ref",
                "HEAD"
        );

        List<String> commitLines = runGitCommand(
                "git",
                "rev-parse",
                "HEAD"
        );

        String currentBranch = branchLines.isEmpty() ? "HEAD" : branchLines.get(0).trim();
        String currentCommit = commitLines.isEmpty() ? "" : commitLines.get(0).trim();

        if (!"HEAD".equals(currentBranch)) {
            return currentBranch;
        }

        return currentCommit;
    }

    private static void restoreOriginalGitRef(String originalGitRef) {
        if (originalGitRef == null || originalGitRef.isBlank()) {
            return;
        }

        try {
            runGitCommand(
                    "git",
                    "checkout",
                    "-f",
                    originalGitRef
            );

            System.out.println("Repository ripristinato su: " + originalGitRef);
        } catch (IOException | InterruptedException e) {
            System.err.println("Impossibile ripristinare il repository su: " + originalGitRef);
            e.printStackTrace();

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<String> runGitCommand(String... command)
            throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(REPOSITORY_PATH.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = process.inputReader()) {
            String line;

            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Comando Git fallito con exit code "
                    + exitCode
                    + ": "
                    + String.join(" ", command)
                    + System.lineSeparator()
                    + String.join(System.lineSeparator(), lines));
        }

        return lines;
    }

    private static void initializeFinalSmellsFile() throws IOException {
        Path parent = FINAL_SMELLS_FILE.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.createDirectories(RELEASE_SMELLS_DIRECTORY);

        try (BufferedWriter writer = Files.newBufferedWriter(FINAL_SMELLS_FILE)) {
            writer.write("Project,ReleaseId,ClassPath,NSmells");
            writer.newLine();
        }
    }

    private static void appendReleaseSmellsToFinalFile(Path releaseOutputPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(releaseOutputPath);
             BufferedWriter writer = Files.newBufferedWriter(
                     FINAL_SMELLS_FILE,
                     java.nio.file.StandardOpenOption.APPEND
             )) {

            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}