package it.uniroma2.isw2.smell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PmdRunner {

    private static final long PMD_TIMEOUT_MINUTES = 10;

    private final Path pmdExecutable;
    private final Path rulesetPath;
    private final Path reportsDirectory;

    public PmdRunner(Path pmdExecutable, Path rulesetPath, Path reportsDirectory) {
        this.pmdExecutable = pmdExecutable;
        this.rulesetPath = rulesetPath;
        this.reportsDirectory = reportsDirectory;
    }

    public Path run(int releaseId, Path fileListPath) throws IOException, InterruptedException {
        Files.createDirectories(reportsDirectory);

        Path reportPath = reportsDirectory.resolve("pmd-report-release-" + releaseId + ".csv");
        Path logPath = reportsDirectory.resolve("pmd-log-release-" + releaseId + ".txt");

        List<String> command = List.of(
                pmdExecutable.toString(),
                "check",
                "--file-list", fileListPath.toString(),
                "-R", rulesetPath.toString(),
                "-f", "csv",
                "-r", reportPath.toString(),
                "--no-cache",
                "--no-fail-on-violation",
                "--no-fail-on-error"
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));

        Process process = processBuilder.start();

        boolean completed = process.waitFor(PMD_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        if (!completed) {
            process.destroyForcibly();
            throw new IOException("PMD non ha terminato entro "
                    + PMD_TIMEOUT_MINUTES
                    + " minuti. Log: "
                    + logPath);
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            throw new IOException("Errore durante l'esecuzione di PMD. Exit code: "
                    + exitCode
                    + ". Log: "
                    + logPath);
        }

        System.out.println("Log PMD generato in: " + logPath);

        return reportPath;
    }
}