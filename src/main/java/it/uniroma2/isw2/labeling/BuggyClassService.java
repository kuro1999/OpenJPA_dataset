package it.uniroma2.isw2.labeling;

import it.uniroma2.isw2.model.TicketBuggyClass;
import it.uniroma2.isw2.model.TicketFixCommit;
import it.uniroma2.isw2.proportion.EnhancedTicket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servizio unico per:
 * - individuare i fix commit associati ai ticket
 * - estrarre le buggy classes con una versione semplificata di SZZ
 */
public class BuggyClassService {

    private static final Pattern TICKET_PATTERN =
            Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern HUNK_PATTERN =
            Pattern.compile("^@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@.*$");

    private BuggyClassService() {
    }

    /**
     * Cerca i fix commit associati ai ticket del dataset.
     * Fa un solo passaggio sull'intero log Git.
     */
    public static List<TicketFixCommit> findFixCommits(List<EnhancedTicket> tickets,
                                                       String repositoryPath) throws IOException {
        List<TicketFixCommit> result = new ArrayList<>();

        if (tickets == null || tickets.isEmpty()) {
            return result;
        }

        Set<String> validTicketIds = extractValidTicketIds(tickets);
        Map<String, TicketFixCommit> unique = new LinkedHashMap<>();

        try {
            List<String> lines = GitCommandRunner.runCommand(
                    repositoryPath,
                    "git",
                    "log",
                    "--all",
                    "--regexp-ignore-case",
                    "--format=%H\t%ct\t%s"
            );

            for (String line : lines) {
                addFixCommitsFromLogLine(line, validTicketIds, unique);
            }

            result.addAll(unique.values());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante la ricerca dei fix commit.", e);
        }
    }

    /**
     * Estrae le buggy classes a partire dai fix commit usando SZZ semplificato.
     */
    public static List<TicketBuggyClass> extractBuggyClasses(List<TicketFixCommit> fixCommits,
                                                             String repositoryPath) throws IOException {
        List<TicketBuggyClass> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (fixCommits == null || fixCommits.isEmpty()) {
            return result;
        }

        for (TicketFixCommit fixCommit : fixCommits) {
            addBuggyClassesForFixCommit(fixCommit, repositoryPath, seen, result);
        }

        return result;
    }

    private static void addFixCommitsFromLogLine(String line,
                                                 Set<String> validTicketIds,
                                                 Map<String, TicketFixCommit> unique) {
        String[] parts = line.split("\t", 3);

        if (parts.length >= 3) {
            String commitHash = parts[0].trim();
            String epochString = parts[1].trim();
            String subject = parts[2];
            Long epochSeconds = parseEpochSeconds(epochString);

            if (isValidCommitData(commitHash, epochSeconds)) {
                addMatchingTicketFixCommits(
                        subject,
                        validTicketIds,
                        unique,
                        commitHash,
                        epochSeconds
                );
            }
        }
    }

    private static Long parseEpochSeconds(String epochString) {
        try {
            return Long.parseLong(epochString);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValidCommitData(String commitHash, Long epochSeconds) {
        return !commitHash.isBlank() && epochSeconds != null;
    }

    private static void addMatchingTicketFixCommits(String subject,
                                                    Set<String> validTicketIds,
                                                    Map<String, TicketFixCommit> unique,
                                                    String commitHash,
                                                    long epochSeconds) {
        Matcher matcher = TICKET_PATTERN.matcher(subject);

        while (matcher.find()) {
            String ticketId = matcher.group().toUpperCase(Locale.ROOT);

            if (validTicketIds.contains(ticketId)) {
                String key = ticketId + "|" + commitHash;
                unique.putIfAbsent(
                        key,
                        new TicketFixCommit(ticketId, commitHash, epochSeconds)
                );
            }
        }
    }

    private static void addBuggyClassesForFixCommit(TicketFixCommit fixCommit,
                                                    String repositoryPath,
                                                    Set<String> seen,
                                                    List<TicketBuggyClass> result) throws IOException {
        String parentHash = findParentCommit(fixCommit.getFixCommitHash(), repositoryPath);

        if (!parentHash.isBlank()) {
            List<String> changedFiles = findChangedFiles(
                    parentHash,
                    fixCommit.getFixCommitHash(),
                    repositoryPath
            );

            for (String filePath : changedFiles) {
                addBuggyClassIfNeeded(
                        fixCommit,
                        parentHash,
                        filePath,
                        repositoryPath,
                        seen,
                        result
                );
            }
        }
    }

    private static void addBuggyClassIfNeeded(TicketFixCommit fixCommit,
                                              String parentHash,
                                              String filePath,
                                              String repositoryPath,
                                              Set<String> seen,
                                              List<TicketBuggyClass> result) throws IOException {
        String normalizedPath = normalizePath(filePath);

        if (isProductionJavaClass(normalizedPath)
                && isBuggyJavaFileByBlame(
                parentHash,
                fixCommit.getFixCommitHash(),
                normalizedPath,
                repositoryPath
        )) {
            addBuggyClassIfNotSeen(fixCommit, normalizedPath, seen, result);
        }
    }

    private static void addBuggyClassIfNotSeen(TicketFixCommit fixCommit,
                                               String normalizedPath,
                                               Set<String> seen,
                                               List<TicketBuggyClass> result) {
        String key = fixCommit.getTicketId() + "|" + normalizedPath;

        if (seen.add(key)) {
            result.add(new TicketBuggyClass(
                    fixCommit.getTicketId(),
                    fixCommit.getFixCommitHash(),
                    normalizedPath
            ));
        }
    }

    private static Set<String> extractValidTicketIds(List<EnhancedTicket> tickets) {
        Set<String> validTicketIds = new HashSet<>();

        for (EnhancedTicket ticket : tickets) {
            if (ticket.getTicketId() != null && !ticket.getTicketId().isBlank()) {
                validTicketIds.add(ticket.getTicketId().trim().toUpperCase(Locale.ROOT));
            }
        }

        return validTicketIds;
    }

    private static String findParentCommit(String fixCommitHash,
                                           String repositoryPath) throws IOException {
        try {
            List<String> lines = GitCommandRunner.runCommand(
                    repositoryPath,
                    "git",
                    "rev-list",
                    "--parents",
                    "-n",
                    "1",
                    fixCommitHash
            );

            if (lines.isEmpty()) {
                return "";
            }

            String[] parts = lines.get(0).trim().split("\\s+");
            if (parts.length < 2) {
                return "";
            }

            return parts[1].trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante la ricerca del parent commit.", e);
        }
    }

    private static List<String> findChangedFiles(String parentHash,
                                                 String fixCommitHash,
                                                 String repositoryPath) throws IOException {
        try {
            return GitCommandRunner.runCommand(
                    repositoryPath,
                    "git",
                    "diff",
                    "--name-only",
                    parentHash,
                    fixCommitHash,
                    "--"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante la lettura dei file modificati.", e);
        }
    }

    private static boolean isBuggyJavaFileByBlame(String parentHash,
                                                  String fixCommitHash,
                                                  String filePath,
                                                  String repositoryPath) throws IOException {
        List<int[]> parentRanges = extractParentChangedRanges(
                parentHash,
                fixCommitHash,
                filePath,
                repositoryPath
        );

        for (int[] range : parentRanges) {
            if (hasValidBlameRange(range, parentHash, filePath, repositoryPath)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasValidBlameRange(int[] range,
                                              String parentHash,
                                              String filePath,
                                              String repositoryPath) throws IOException {
        int start = range[0];
        int end = range[1];

        return end >= start
                && !blameParentRange(parentHash, filePath, start, end, repositoryPath).isEmpty();
    }

    private static List<int[]> extractParentChangedRanges(String parentHash,
                                                          String fixCommitHash,
                                                          String filePath,
                                                          String repositoryPath) throws IOException {
        List<int[]> ranges = new ArrayList<>();

        try {
            List<String> diffLines = GitCommandRunner.runCommand(
                    repositoryPath,
                    "git",
                    "diff",
                    "-U0",
                    parentHash,
                    fixCommitHash,
                    "--",
                    filePath
            );

            for (String line : diffLines) {
                int[] range = extractParentRangeFromDiffLine(line);

                ranges.add(range);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante l'analisi del diff.", e);
        }

        return ranges;
    }

    private static int[] extractParentRangeFromDiffLine(String line) {
        Matcher matcher = HUNK_PATTERN.matcher(line);

        if (!matcher.matches()) {
            return new int[0];
        }

        int oldStart = Integer.parseInt(matcher.group(1));
        int oldCount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));

        /*
         * Se oldCount = 0, l'hunk contiene solo aggiunte.
         * In questa versione semplificata di SZZ non possiamo fare blame
         * su righe precedenti che non esistono.
         */
        if (oldCount == 0) {
            return new int[0];
        }

        int end = oldStart + oldCount - 1;

        return new int[]{oldStart, end};
    }

    private static List<String> blameParentRange(String parentHash,
                                                 String filePath,
                                                 int start,
                                                 int end,
                                                 String repositoryPath) throws IOException {
        try {
            return GitCommandRunner.runCommand(
                    repositoryPath,
                    "git",
                    "blame",
                    "-w",
                    "-l",
                    "-L",
                    start + "," + end,
                    parentHash,
                    "--",
                    filePath
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante git blame.", e);
        }
    }

    private static boolean isProductionJavaClass(String path) {
        return path.endsWith(".java")
                && path.contains("/src/main/java/")
                && !path.contains("/src/test/java")
                && !path.contains("/src/it/")
                && !path.contains("/testDependencies/")
                && !path.contains("/test-dependencies/")
                && !path.contains("/testFixtures/")
                && !path.contains("/test-fixtures/")
                && !path.contains("examples/src")
                && !path.contains("junit5/")
                && !path.contains("kubernetes/")
                && !path.contains("/osgi");
    }

    private static String normalizePath(String path) {
        return path.replace("\\", "/").trim();
    }
}