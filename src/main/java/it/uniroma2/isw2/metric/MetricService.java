package it.uniroma2.isw2.metric;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import it.uniroma2.isw2.labeling.GitCommandRunner;
import it.uniroma2.isw2.model.Release;
import it.uniroma2.isw2.model.ReleaseJavaClass;
import it.uniroma2.isw2.proportion.DateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calcola le metriche classe-release selezionate per la Milestone 1.
 *
 * Le metriche statiche sono calcolate sullo snapshot della release Ri.
 * Le metriche storiche sono calcolate usando solo la storia Git disponibile fino a Ri.
 *
 * Gli smell non sono calcolati qui perché vengono gestiti separatamente.
 */
public class MetricService {

    private static final DateTimeFormatter GIT_BEFORE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern COMMIT_HASH_PATTERN =
            Pattern.compile("\\b[0-9a-fA-F]{7,40}\\b");

    private final String projectName;
    private final Path repositoryPath;
    private final Set<String> fixCommitHashes;
    private final Map<String, List<String>> changedFilesByCommit = new HashMap<>();

    public MetricService(String projectName,
                         Path repositoryPath) {
        this(projectName, repositoryPath, null);
    }

    public MetricService(String projectName,
                         Path repositoryPath,
                         Path ticketFixCommitsFile) {
        this.projectName = projectName;
        this.repositoryPath = repositoryPath;
        this.fixCommitHashes = loadFixCommitHashes(ticketFixCommitsFile);
    }

    public List<ClassReleaseMetric> computeMetrics(List<Release> selectedReleases,
                                                   List<ReleaseJavaClass> releaseJavaClasses)
            throws IOException {
        List<ClassReleaseMetric> result = new ArrayList<>();

        String originalCommitHash = getCurrentCommitHash();

        try {
            for (Release release : selectedReleases) {
                String releaseId = release.getVersionId();

                String snapshotCommitHash = findLastCommitOfReleaseDay(
                        release.getDate(),
                        originalCommitHash
                );

                if (snapshotCommitHash.isBlank()) {
                    continue;
                }

                checkout(snapshotCommitHash);

                List<ReleaseJavaClass> classesOfRelease =
                        filterClassesByRelease(releaseJavaClasses, releaseId);

                Set<String> internalClassNames =
                        buildInternalClassNames(classesOfRelease);

                for (ReleaseJavaClass javaClass : classesOfRelease) {
                    Path absoluteClassPath = repositoryPath
                            .resolve(javaClass.getClassPath())
                            .toAbsolutePath()
                            .normalize();

                    SourceMetrics sourceMetrics =
                            computeSourceMetrics(absoluteClassPath, internalClassNames);

                    HistoricalMetrics historicalMetrics =
                            computeHistoricalMetrics(javaClass.getClassPath(), release.getDate());

                    result.add(new ClassReleaseMetric(
                            projectName,
                            releaseId,
                            normalizePath(javaClass.getClassPath()),

                            sourceMetrics.sizeLoc,
                            sourceMetrics.nom,
                            sourceMetrics.avgMethodSize,
                            sourceMetrics.cycloComplexity,
                            sourceMetrics.fanOut,

                            historicalMetrics.nr,
                            historicalMetrics.fixRate,
                            historicalMetrics.nAuth,

                            historicalMetrics.locAdded,
                            historicalMetrics.maxLocAdded,
                            historicalMetrics.churn,
                            historicalMetrics.maxChurn,
                            historicalMetrics.maxChangeSetSize,
                            historicalMetrics.avgModifiedDirs,

                            historicalMetrics.classAge,
                            historicalMetrics.ageSinceLastChange,
                            historicalMetrics.ownershipRatio,
                            historicalMetrics.crossDirectoryChangeRatio
                    ));
                }

                System.out.println("Metriche calcolate per release "
                        + release.getVersionName()
                        + " (" + releaseId + ").");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante il calcolo delle metriche.", e);
        } finally {
            try {
                checkout(originalCommitHash);
            } catch (Exception e) {
                System.out.println("Attenzione: impossibile ripristinare il commit originale della repository.");
                e.printStackTrace();
            }
        }

        return result;
    }

    private List<ReleaseJavaClass> filterClassesByRelease(List<ReleaseJavaClass> releaseJavaClasses,
                                                          String releaseId) {
        List<ReleaseJavaClass> result = new ArrayList<>();

        for (ReleaseJavaClass javaClass : releaseJavaClasses) {
            if (releaseId.equals(javaClass.getReleaseId())) {
                result.add(javaClass);
            }
        }

        return result;
    }

    private Set<String> buildInternalClassNames(List<ReleaseJavaClass> classesOfRelease) {
        Set<String> internalClassNames = new HashSet<>();

        for (ReleaseJavaClass javaClass : classesOfRelease) {
            String fullyQualifiedName = toFullyQualifiedName(javaClass.getClassPath());

            if (!fullyQualifiedName.isBlank()) {
                internalClassNames.add(fullyQualifiedName);
            }
        }

        return internalClassNames;
    }

    private SourceMetrics computeSourceMetrics(Path absoluteClassPath,
                                               Set<String> internalClassNames) throws IOException {
        SourceMetrics metrics = new SourceMetrics();

        if (!Files.exists(absoluteClassPath)) {
            return metrics;
        }

        String source = Files.readString(absoluteClassPath);
        metrics.sizeLoc = countLines(source);

        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(absoluteClassPath);

            metrics.nom = countMethods(compilationUnit);
            metrics.avgMethodSize = safeRatio(metrics.sizeLoc, metrics.nom);
            metrics.cycloComplexity = countCyclomaticComplexity(compilationUnit);
            metrics.fanOut = countFanOut(compilationUnit, internalClassNames);
        } catch (ParseProblemException e) {
            /*
             * Se una vecchia release contiene codice non parsabile da JavaParser,
             * manteniamo almeno SIZE_LOC e lasciamo a 0 le metriche strutturali.
             */
            metrics.nom = 0;
            metrics.avgMethodSize = 0.0;
            metrics.cycloComplexity = 0;
            metrics.fanOut = 0;
        }

        return metrics;
    }

    private int countLines(String source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }

        return (int) source.lines().count();
    }

    private int countMethods(CompilationUnit compilationUnit) {
        return compilationUnit.findAll(MethodDeclaration.class).size();
    }

    private int countCyclomaticComplexity(CompilationUnit compilationUnit) {
        int methodCount = countMethods(compilationUnit);

        int ifCount = compilationUnit.findAll(IfStmt.class).size();
        int forCount = compilationUnit.findAll(ForStmt.class).size();
        int forEachCount = compilationUnit.findAll(ForEachStmt.class).size();
        int whileCount = compilationUnit.findAll(WhileStmt.class).size();
        int doCount = compilationUnit.findAll(DoStmt.class).size();
        int catchCount = compilationUnit.findAll(CatchClause.class).size();
        int conditionalCount = compilationUnit.findAll(ConditionalExpr.class).size();

        int switchCaseCount = 0;
        for (SwitchEntry switchEntry : compilationUnit.findAll(SwitchEntry.class)) {
            if (!switchEntry.getLabels().isEmpty()) {
                switchCaseCount++;
            }
        }

        int logicalOperatorCount = 0;
        for (BinaryExpr binaryExpr : compilationUnit.findAll(BinaryExpr.class)) {
            if (binaryExpr.getOperator() == BinaryExpr.Operator.AND
                    || binaryExpr.getOperator() == BinaryExpr.Operator.OR) {
                logicalOperatorCount++;
            }
        }

        return methodCount
                + ifCount
                + forCount
                + forEachCount
                + whileCount
                + doCount
                + catchCount
                + conditionalCount
                + switchCaseCount
                + logicalOperatorCount;
    }

    private int countFanOut(CompilationUnit compilationUnit,
                            Set<String> internalClassNames) {
        Set<String> dependencies = new HashSet<>();

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String importedName = importDeclaration.getNameAsString();

            if (importDeclaration.isAsterisk()) {
                addWildcardDependencies(importedName, internalClassNames, dependencies);
            } else if (importDeclaration.isStatic()) {
                dependencies.add(normalizeStaticImport(importedName));
            } else {
                dependencies.add(importedName);
            }
        }

        return dependencies.size();
    }

    private void addWildcardDependencies(String packageName,
                                         Set<String> internalClassNames,
                                         Set<String> dependencies) {
        String packagePrefix = packageName + ".";
        boolean matchedInternalClass = false;

        for (String internalClassName : internalClassNames) {
            if (internalClassName.startsWith(packagePrefix)) {
                dependencies.add(internalClassName);
                matchedInternalClass = true;
            }
        }

        if (!matchedInternalClass) {
            dependencies.add(packageName + ".*");
        }
    }

    private String normalizeStaticImport(String importedName) {
        int lastDotIndex = importedName.lastIndexOf('.');

        if (lastDotIndex < 0) {
            return importedName;
        }

        String lastSegment = importedName.substring(lastDotIndex + 1);

        if (lastSegment.isBlank()) {
            return importedName;
        }

        if (Character.isLowerCase(lastSegment.charAt(0))) {
            return importedName.substring(0, lastDotIndex);
        }

        return importedName;
    }

    private String findLastCommitOfReleaseDay(String releaseDate,
                                              String referenceCommitHash)
            throws IOException, InterruptedException {
        LocalDateTime endExclusive = DateUtils.parseReleaseDate(releaseDate).plusDays(1);
        String formattedDate = endExclusive.format(GIT_BEFORE_FORMAT);

        List<String> lines = GitCommandRunner.runCommand(
                repositoryPath.toString(),
                "git",
                "rev-list",
                "-n",
                "1",
                "--before=" + formattedDate,
                "--first-parent",
                referenceCommitHash
        );

        if (lines.isEmpty()) {
            return "";
        }

        return lines.get(0).trim();
    }

    private String getCurrentCommitHash() throws IOException {
        try {
            List<String> lines = GitCommandRunner.runCommand(
                    repositoryPath.toString(),
                    "git",
                    "rev-parse",
                    "HEAD"
            );

            if (lines.isEmpty()) {
                return "";
            }

            return lines.get(0).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruzione durante la lettura del commit corrente.", e);
        }
    }

    private void checkout(String commitHash) throws IOException, InterruptedException {
        if (commitHash == null || commitHash.isBlank()) {
            return;
        }

        GitCommandRunner.runCommand(
                repositoryPath.toString(),
                "git",
                "checkout",
                "-f",
                commitHash
        );
    }

    private String toFullyQualifiedName(String classPath) {
        String normalizedPath = normalizePath(classPath);

        int srcMainJavaIndex = normalizedPath.indexOf("/src/main/java/");
        if (srcMainJavaIndex < 0) {
            return "";
        }

        String javaRelativePath = normalizedPath.substring(
                srcMainJavaIndex + "/src/main/java/".length()
        );

        if (!javaRelativePath.endsWith(".java")) {
            return "";
        }

        return javaRelativePath
                .substring(0, javaRelativePath.length() - ".java".length())
                .replace("/", ".");
    }

    private String normalizePath(String path) {
        return path.replace("\\", "/").trim();
    }

    private HistoricalMetrics computeHistoricalMetrics(String classPath,
                                                       String releaseDate)
            throws IOException, InterruptedException {
        LocalDateTime currentReleaseDate = DateUtils.parseReleaseDate(releaseDate);

        List<String> lines = GitCommandRunner.runCommand(
                repositoryPath.toString(),
                "git",
                "log",
                "--follow",
                "--numstat",
                "--pretty=format:__COMMIT__%H%x09%an%x09%ct",
                "--",
                classPath
        );

        HistoricalAccumulator accumulator = new HistoricalAccumulator();
        CurrentCommit currentCommit = null;

        for (String line : lines) {
            if (line.startsWith("__COMMIT__")) {
                if (currentCommit != null) {
                    CommitFileStats stats = computeCommitFileStats(
                            currentCommit.commitHash,
                            classPath
                    );

                    accumulator.accept(
                            currentCommit,
                            stats,
                            isFixCommit(currentCommit.commitHash)
                    );
                }

                currentCommit = parseCommitHeader(line);
                continue;
            }

            if (currentCommit == null) {
                continue;
            }

            String trimmed = line.trim();

            if (trimmed.isBlank()) {
                continue;
            }

            String[] parts = trimmed.split("\\t");

            if (parts.length < 2) {
                continue;
            }

            int added = parseNumstatValue(parts[0]);
            int deleted = parseNumstatValue(parts[1]);

            currentCommit.added += added;
            currentCommit.deleted += deleted;
        }

        if (currentCommit != null) {
            CommitFileStats stats = computeCommitFileStats(
                    currentCommit.commitHash,
                    classPath
            );

            accumulator.accept(
                    currentCommit,
                    stats,
                    isFixCommit(currentCommit.commitHash)
            );
        }

        return accumulator.toHistoricalMetrics(currentReleaseDate);
    }

    private CurrentCommit parseCommitHeader(String line) {
        String payload = line.substring("__COMMIT__".length());
        String[] parts = payload.split("\\t", 3);

        if (parts.length < 3) {
            return new CurrentCommit("", "", 0L);
        }

        String commitHash = parts[0].trim().toLowerCase(Locale.ROOT);
        String author = parts[1].trim();

        long epochSeconds;

        try {
            epochSeconds = Long.parseLong(parts[2].trim());
        } catch (NumberFormatException e) {
            epochSeconds = 0L;
        }

        return new CurrentCommit(commitHash, author, epochSeconds);
    }

    private int parseNumstatValue(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private CommitFileStats computeCommitFileStats(String commitHash,
                                                   String classPath)
            throws IOException, InterruptedException {
        List<String> changedFiles = getChangedFiles(commitHash);
        String classDirectory = getDirectory(classPath);

        Set<String> modifiedDirectories = new HashSet<>();

        int changeSetSize = 0;
        boolean crossDirectoryChange = false;

        for (String file : changedFiles) {
            String normalizedFile = normalizePath(file);

            if (normalizedFile.isBlank()) {
                continue;
            }

            changeSetSize++;

            String currentDirectory = getDirectory(normalizedFile);
            modifiedDirectories.add(currentDirectory);

            if (!currentDirectory.equals(classDirectory)) {
                crossDirectoryChange = true;
            }
        }

        return new CommitFileStats(
                changeSetSize,
                modifiedDirectories.size(),
                crossDirectoryChange
        );
    }

    private List<String> getChangedFiles(String commitHash)
            throws IOException, InterruptedException {
        if (commitHash == null || commitHash.isBlank()) {
            return List.of();
        }

        List<String> cachedValue = changedFilesByCommit.get(commitHash);

        if (cachedValue != null) {
            return cachedValue;
        }

        List<String> changedFiles = GitCommandRunner.runCommand(
                repositoryPath.toString(),
                "git",
                "diff-tree",
                "--no-commit-id",
                "--name-only",
                "-r",
                "--root",
                commitHash
        );

        changedFilesByCommit.put(commitHash, changedFiles);
        return changedFiles;
    }

    private String getDirectory(String path) {
        String normalizedPath = normalizePath(path);
        int lastSlashIndex = normalizedPath.lastIndexOf('/');

        if (lastSlashIndex < 0) {
            return "";
        }

        return normalizedPath.substring(0, lastSlashIndex);
    }

    private boolean isFixCommit(String commitHash) {
        if (commitHash == null || commitHash.isBlank() || fixCommitHashes.isEmpty()) {
            return false;
        }

        String normalizedCommitHash = commitHash.toLowerCase(Locale.ROOT);

        for (String fixCommitHash : fixCommitHashes) {
            if (normalizedCommitHash.equals(fixCommitHash)
                    || normalizedCommitHash.startsWith(fixCommitHash)
                    || fixCommitHash.startsWith(normalizedCommitHash)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> loadFixCommitHashes(Path ticketFixCommitsFile) {
        Set<String> result = new HashSet<>();

        if (ticketFixCommitsFile == null || !Files.isRegularFile(ticketFixCommitsFile)) {
            return result;
        }

        try {
            List<String> lines = Files.readAllLines(ticketFixCommitsFile);

            for (String line : lines) {
                Matcher matcher = COMMIT_HASH_PATTERN.matcher(line);

                while (matcher.find()) {
                    result.add(matcher.group().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            System.out.println("Attenzione: impossibile leggere i fix commit da "
                    + ticketFixCommitsFile);
        }

        return result;
    }

    private static long daysBetweenCommitAndRelease(long commitEpochSeconds,
                                                    LocalDateTime releaseDate) {
        if (commitEpochSeconds <= 0) {
            return 0;
        }

        LocalDateTime commitDate = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(commitEpochSeconds),
                ZoneOffset.UTC
        );

        long days = ChronoUnit.DAYS.between(commitDate, releaseDate);

        return Math.max(days, 0);
    }

    private static double safeRatio(double numerator,
                                    double denominator) {
        if (denominator <= 0) {
            return 0.0;
        }

        return numerator / denominator;
    }

    private static class SourceMetrics {

        private int sizeLoc;
        private int nom;
        private double avgMethodSize;
        private int cycloComplexity;
        private int fanOut;
    }

    private static class CurrentCommit {

        private final String commitHash;
        private final String author;
        private final long epochSeconds;

        private int added;
        private int deleted;

        private CurrentCommit(String commitHash,
                              String author,
                              long epochSeconds) {
            this.commitHash = commitHash;
            this.author = author;
            this.epochSeconds = epochSeconds;
        }
    }

    private static class CommitFileStats {

        private final int changeSetSize;
        private final int modifiedDirectories;
        private final boolean crossDirectoryChange;

        private CommitFileStats(int changeSetSize,
                                int modifiedDirectories,
                                boolean crossDirectoryChange) {
            this.changeSetSize = changeSetSize;
            this.modifiedDirectories = modifiedDirectories;
            this.crossDirectoryChange = crossDirectoryChange;
        }
    }

    private static class HistoricalAccumulator {

        private int nr;
        private int nFix;

        private int locAdded;
        private int maxLocAdded;

        private int churn;
        private int maxChurn;

        private int maxChangeSetSize;
        private int modifiedDirectoriesSum;
        private int crossDirectoryCommits;

        private long firstCommitEpochSeconds = Long.MAX_VALUE;
        private long lastCommitEpochSeconds;

        private final Map<String, Integer> commitsByAuthor = new HashMap<>();

        private void accept(CurrentCommit commit,
                            CommitFileStats stats,
                            boolean fixCommit) {
            if (commit.commitHash == null || commit.commitHash.isBlank()) {
                return;
            }

            nr++;

            if (fixCommit) {
                nFix++;
            }

            if (commit.author != null && !commit.author.isBlank()) {
                commitsByAuthor.merge(commit.author, 1, Integer::sum);
            }

            if (commit.epochSeconds > 0) {
                firstCommitEpochSeconds = Math.min(
                        firstCommitEpochSeconds,
                        commit.epochSeconds
                );

                lastCommitEpochSeconds = Math.max(
                        lastCommitEpochSeconds,
                        commit.epochSeconds
                );
            }

            int commitChurn = commit.added + commit.deleted;

            locAdded += commit.added;
            maxLocAdded = Math.max(maxLocAdded, commit.added);

            churn += commitChurn;
            maxChurn = Math.max(maxChurn, commitChurn);

            maxChangeSetSize = Math.max(maxChangeSetSize, stats.changeSetSize);
            modifiedDirectoriesSum += stats.modifiedDirectories;

            if (stats.crossDirectoryChange) {
                crossDirectoryCommits++;
            }
        }

        private HistoricalMetrics toHistoricalMetrics(LocalDateTime releaseDate) {
            HistoricalMetrics metrics = new HistoricalMetrics();

            if (nr == 0) {
                return metrics;
            }

            metrics.nr = nr;
            metrics.fixRate = safeRatio(nFix, nr);
            metrics.nAuth = commitsByAuthor.size();

            metrics.locAdded = locAdded;
            metrics.maxLocAdded = maxLocAdded;

            metrics.churn = churn;
            metrics.maxChurn = maxChurn;

            metrics.maxChangeSetSize = maxChangeSetSize;
            metrics.avgModifiedDirs = safeRatio(modifiedDirectoriesSum, nr);

            if (firstCommitEpochSeconds != Long.MAX_VALUE) {
                metrics.classAge = daysBetweenCommitAndRelease(
                        firstCommitEpochSeconds,
                        releaseDate
                );
            }

            metrics.ageSinceLastChange = daysBetweenCommitAndRelease(
                    lastCommitEpochSeconds,
                    releaseDate
            );

            metrics.ownershipRatio = computeOwnershipRatio();
            metrics.crossDirectoryChangeRatio = safeRatio(crossDirectoryCommits, nr);

            return metrics;
        }

        private double computeOwnershipRatio() {
            if (nr == 0 || commitsByAuthor.isEmpty()) {
                return 0.0;
            }

            int maxCommitsBySingleAuthor = 0;

            for (int commits : commitsByAuthor.values()) {
                maxCommitsBySingleAuthor = Math.max(
                        maxCommitsBySingleAuthor,
                        commits
                );
            }

            return safeRatio(maxCommitsBySingleAuthor, nr);
        }
    }

    private static class HistoricalMetrics {

        private int nr;
        private double fixRate;
        private int nAuth;

        private int locAdded;
        private int maxLocAdded;

        private int churn;
        private int maxChurn;

        private int maxChangeSetSize;
        private double avgModifiedDirs;

        private long classAge;
        private long ageSinceLastChange;

        private double ownershipRatio;
        private double crossDirectoryChangeRatio;
    }
}