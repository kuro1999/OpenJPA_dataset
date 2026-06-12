package it.uniroma2.isw2;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.uniroma2.isw2.smell.PmdRunner;
import it.uniroma2.isw2.smell.PmdSmellCsvBuilder;
import it.uniroma2.isw2.smell.ProductionJavaFileListBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClassRankerBySmells {

    /*
     * ==========================
     * CONFIGURAZIONE STATICA
     * ==========================
     *
     * Il repository deve già essere posizionato sull'ultima release da analizzare.
     * Non viene fatto checkout e non viene applicato il filtro del 34%.
     */

    private static final Logger LOGGER = Logger.getLogger(ClassRankerBySmells.class.getName());

    private static final String PROJECT_NAME = "OPENJPA";

    /*
     * Può essere un valore fittizio se lavori solo sull'ultima release.
     * Serve solo per riusare il formato CSV già previsto dalla pipeline smell.
     */
    private static final int RELEASE_ID = 999;

    private static final String FIRST_NAME = "Edoardo";

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

    private static final Path OUTPUT_DIRECTORY = Path.of(
            "C:\\Users\\edoar\\OneDrive\\Desktop\\ISW2\\isw2-dataset-openjpa\\chosen_classes"
    );

    private static final int MIN_LOC = 300;
    private static final int MAX_LOC = 2500;

    private static final int MIN_METHODS = 10;
    private static final int MAX_METHODS = 90;

    private static final int MIN_PUBLIC_METHODS = 5;
    private static final int MAX_PUBLIC_METHODS = 40;

    private static final int MIN_NSMELLS = 5;
    private static final int MIN_BRANCHING_KEYWORDS = 15;

    private static final List<String> GUI_KEYWORDS = List.of(
            "gui", "ui", "view", "window", "panel", "button", "dialog", "frame",
            "screen", "page", "component", "widget", "swing", "awt", "javafx",
            "servlet", "controller", "web", "template"
    );

    private static final List<String> SIMPLE_ROLE_KEYWORDS = List.of(
            "dto", "vo", "pojo", "bean", "model", "entity", "constant", "constants",
            "exception", "error", "abstract", "interface", "enum",
            "xml", "formatter", "dictionary", "demo", "prepared",
            "resultset", "descriptor", "definition", "info", "parser", "query",
            "decorator", "asm", "hashset"
    );

    private static final List<String> GENERATED_KEYWORDS = List.of(
            "generated", "target/generated", "build/generated", "protobuf",
            "grpc", "thrift", "avro", "openapi", "swagger", "generator"
    );

    private ClassRankerBySmells() {
    }

    public static void main(String[] args) {
        try {
            LOGGER.info("Avvio ranking classi per smell.");
            printFilterConfiguration();
            LOGGER.info(() -> "Posizionamento repository sull'ultima release disponibile: " + REPOSITORY_PATH);

            checkoutLatestRelease();

            Files.createDirectories(OUTPUT_DIRECTORY);

            Path smellCsvPath = computeSmellsOnCurrentRepository();

            List<ClassCandidate> candidates = buildCandidatesFromSmellCsv(smellCsvPath);

            List<ClassCandidate> accepted = new ArrayList<>();
            List<ClassCandidate> discarded = new ArrayList<>();

            for (ClassCandidate candidate : candidates) {
                applyFilters(candidate);

                if (candidate.discardReasons.isEmpty()) {
                    accepted.add(candidate);
                } else {
                    discarded.add(candidate);
                }
            }

            sortAcceptedCandidates(accepted);
            sortDiscardedCandidates(discarded);

            List<ClassCandidate> selected = selectClassesByNameAlgorithm(accepted, FIRST_NAME);

            writeCsv(OUTPUT_DIRECTORY.resolve("ranked_classes.csv"), accepted);
            writeCsv(OUTPUT_DIRECTORY.resolve("discarded_classes.csv"), discarded);
            writeCsv(OUTPUT_DIRECTORY.resolve("selected_classes.csv"), selected);

            printSummary(candidates, accepted, discarded, selected);

            LOGGER.info("Ranking completato.");
            LOGGER.info(() -> "Output directory: " + OUTPUT_DIRECTORY);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore durante il ranking delle classi.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Ranking delle classi interrotto.", e);
        }
    }

    private static void sortAcceptedCandidates(List<ClassCandidate> accepted) {
        accepted.sort(
                Comparator.comparingInt(ClassCandidate::nSmells).reversed()
                        .thenComparing(Comparator.comparingInt(ClassCandidate::loc).reversed())
                        .thenComparing(Comparator.comparingInt(ClassCandidate::methods).reversed())
                        .thenComparing(ClassCandidate::classPath)
        );
    }

    private static void sortDiscardedCandidates(List<ClassCandidate> discarded) {
        discarded.sort(
                Comparator.comparingInt(ClassCandidate::nSmells).reversed()
                        .thenComparing(ClassCandidate::discardReasonsText)
                        .thenComparing(ClassCandidate::classPath)
        );
    }

    private static void printFilterConfiguration() {
        LOGGER.info("Filtri configurati:");
        LOGGER.info(() -> "- MIN_LOC = " + MIN_LOC);
        LOGGER.info(() -> "- MIN_METHODS = " + MIN_METHODS);
        LOGGER.info(() -> "- MIN_PUBLIC_METHODS = " + MIN_PUBLIC_METHODS);
        LOGGER.info(() -> "- MIN_NSMELLS = " + MIN_NSMELLS);
    }

    private static void checkoutLatestRelease() throws IOException, InterruptedException {
        String latestTag = findLatestTag();

        runGitCommand("git", "checkout", "-f", latestTag);

        String currentCommit = runGitCommandAndGetFirstLine(
                "git",
                "rev-parse",
                "HEAD"
        );

        LOGGER.info(() -> "Checkout effettuato sulla release: " + latestTag);
        LOGGER.info(() -> "Commit corrente: " + currentCommit);
    }

    private static String findLatestTag() throws IOException, InterruptedException {
        return runGitCommandAndGetFirstLine(
                "git",
                "tag",
                "--sort=-creatordate"
        );
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
            throw new IOException(
                    "Comando Git fallito con exit code "
                            + exitCode
                            + ": "
                            + String.join(" ", command)
            );
        }

        return lines;
    }

    private static String runGitCommandAndGetFirstLine(String... command)
            throws IOException, InterruptedException {

        List<String> lines = runGitCommand(command);

        if (lines.isEmpty()) {
            throw new IOException("Nessun tag trovato nel repository.");
        }

        return lines.get(0).trim();
    }

    private static Path computeSmellsOnCurrentRepository()
            throws IOException, InterruptedException {

        Files.createDirectories(PMD_FILE_LISTS_DIRECTORY);
        Files.createDirectories(PMD_REPORTS_DIRECTORY);
        Files.createDirectories(OUTPUT_DIRECTORY);

        Path fileListPath = PMD_FILE_LISTS_DIRECTORY.resolve(
                PROJECT_NAME + "_current_release_production_files.txt"
        );

        Path smellCsvPath = OUTPUT_DIRECTORY.resolve(
                PROJECT_NAME + "_CurrentRelease_ClassSmells.csv"
        );

        ProductionJavaFileListBuilder fileListBuilder = new ProductionJavaFileListBuilder();

        List<String> productionJavaFiles =
                fileListBuilder.findProductionJavaFiles(REPOSITORY_PATH);

        fileListBuilder.writeFileList(fileListPath, productionJavaFiles);

        LOGGER.info(() -> "File Java di produzione trovati: " + productionJavaFiles.size());
        LOGGER.info(() -> "File list PMD: " + fileListPath);

        PmdRunner pmdRunner = new PmdRunner(
                PMD_EXECUTABLE,
                PMD_RULESET_PATH,
                PMD_REPORTS_DIRECTORY
        );

        Path pmdReportPath = pmdRunner.run(RELEASE_ID, fileListPath);

        PmdSmellCsvBuilder smellCsvBuilder = new PmdSmellCsvBuilder();

        smellCsvBuilder.build(
                PROJECT_NAME,
                RELEASE_ID,
                REPOSITORY_PATH,
                fileListPath,
                pmdReportPath,
                smellCsvPath
        );

        LOGGER.info(() -> "CSV NSmells generato in: " + smellCsvPath);

        return smellCsvPath;
    }

    private static List<ClassCandidate> buildCandidatesFromSmellCsv(Path smellCsvPath)
            throws IOException {

        List<ClassCandidate> candidates = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(smellCsvPath, StandardCharsets.UTF_8)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                Optional<ClassCandidate> candidate = parseCandidateLine(line);
                candidate.ifPresent(candidates::add);
            }
        }

        return candidates;
    }

    private static Optional<ClassCandidate> parseCandidateLine(String line) {
        if (line.isBlank()) {
            return Optional.empty();
        }

        List<String> columns = parseCsvLine(line);

        if (columns.size() < 4) {
            return Optional.empty();
        }

        String project = columns.get(0).trim();
        int releaseId = Integer.parseInt(columns.get(1).trim());
        String classPath = columns.get(2).trim();
        int nSmells = Integer.parseInt(columns.get(3).trim());

        Path absoluteClassPath = REPOSITORY_PATH.resolve(classPath).normalize();
        SourceMetrics metrics = analyzeSourceFile(absoluteClassPath);

        return Optional.of(ClassCandidate.builder()
                .project(project)
                .releaseId(releaseId)
                .classPath(classPath)
                .nSmells(nSmells)
                .loc(metrics.loc())
                .methods(metrics.methods())
                .publicMethods(metrics.publicMethods())
                .branchingKeywords(metrics.branchingKeywords())
                .isInterface(metrics.isInterface())
                .isEnum(metrics.isEnum())
                .isAnnotation(metrics.isAnnotation())
                .isAbstract(metrics.isAbstract())
                .build());
    }

    private static SourceMetrics analyzeSourceFile(Path sourcePath) {
        if (!Files.exists(sourcePath)) {
            return emptySourceMetrics();
        }

        try {
            return analyzeSourceFileWithParser(sourcePath);
        } catch (Exception parsingException) {
            return analyzeSourceFileLineBased(sourcePath);
        }
    }

    private static SourceMetrics analyzeSourceFileWithParser(Path sourcePath) throws IOException {
        String code = Files.readString(sourcePath, StandardCharsets.UTF_8);
        String codeWithoutComments = removeCommentsSafely(code);

        int loc = countLoc(codeWithoutComments);
        int branchingKeywords = countBranchingKeywords(code);

        CompilationUnit compilationUnit = StaticJavaParser.parse(code);
        String simpleClassName = sourcePath.getFileName()
                .toString()
                .replace(".java", "");

        Optional<ClassOrInterfaceDeclaration> classOrInterface = findMainClassOrInterface(
                compilationUnit,
                simpleClassName
        );

        if (classOrInterface.isPresent()) {
            return buildSourceMetricsFromClassDeclaration(
                    classOrInterface.get(),
                    loc,
                    branchingKeywords
            );
        }

        return buildSourceMetricsFromCompilationUnit(
                compilationUnit,
                simpleClassName,
                loc,
                branchingKeywords
        );
    }

    private static Optional<ClassOrInterfaceDeclaration> findMainClassOrInterface(
            CompilationUnit compilationUnit,
            String simpleClassName
    ) {
        return compilationUnit.findFirst(
                ClassOrInterfaceDeclaration.class,
                declaration -> declaration.getNameAsString().equals(simpleClassName)
        );
    }

    private static SourceMetrics buildSourceMetricsFromClassDeclaration(
            ClassOrInterfaceDeclaration declaration,
            int loc,
            int branchingKeywords
    ) {
        int methods = declaration.findAll(MethodDeclaration.class).size()
                + declaration.findAll(ConstructorDeclaration.class).size();

        int publicMethods = countPublicMethods(declaration.findAll(MethodDeclaration.class))
                + countPublicConstructors(declaration.findAll(ConstructorDeclaration.class));

        return new SourceMetrics(
                loc,
                methods,
                publicMethods,
                branchingKeywords,
                declaration.isInterface(),
                false,
                false,
                declaration.isAbstract()
        );
    }

    private static SourceMetrics buildSourceMetricsFromCompilationUnit(
            CompilationUnit compilationUnit,
            String simpleClassName,
            int loc,
            int branchingKeywords
    ) {
        boolean isEnum = compilationUnit.findFirst(
                EnumDeclaration.class,
                declaration -> declaration.getNameAsString().equals(simpleClassName)
        ).isPresent();

        boolean isAnnotation = compilationUnit.findFirst(
                AnnotationDeclaration.class,
                declaration -> declaration.getNameAsString().equals(simpleClassName)
        ).isPresent();

        int methods = compilationUnit.findAll(MethodDeclaration.class).size()
                + compilationUnit.findAll(ConstructorDeclaration.class).size();

        int publicMethods = countPublicMethods(compilationUnit.findAll(MethodDeclaration.class))
                + countPublicConstructors(compilationUnit.findAll(ConstructorDeclaration.class));

        return new SourceMetrics(
                loc,
                methods,
                publicMethods,
                branchingKeywords,
                false,
                isEnum,
                isAnnotation,
                false
        );
    }

    private static int countPublicMethods(List<MethodDeclaration> methods) {
        return (int) methods.stream()
                .filter(MethodDeclaration::isPublic)
                .count();
    }

    private static int countPublicConstructors(List<ConstructorDeclaration> constructors) {
        return (int) constructors.stream()
                .filter(ConstructorDeclaration::isPublic)
                .count();
    }

    private static SourceMetrics analyzeSourceFileLineBased(Path sourcePath) {
        try {
            String code = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String codeWithoutComments = removeCommentsSafely(code);

            return new SourceMetrics(
                    countLoc(codeWithoutComments),
                    countMethodsLineBased(code),
                    countPublicMethodsLineBased(code),
                    countBranchingKeywords(code),
                    false,
                    false,
                    false,
                    false
            );
        } catch (IOException ioException) {
            return emptySourceMetrics();
        }
    }

    private static SourceMetrics emptySourceMetrics() {
        return new SourceMetrics(0, 0, 0, 0, false, false, false, false);
    }

    private static int countBranchingKeywords(String code) {
        int count = 0;
        String codeWithoutComments = removeCommentsSafely(code);

        for (String line : codeWithoutComments.split("\\R")) {
            if (containsBranchingKeyword(line.trim())) {
                count++;
            }
        }

        return count;
    }

    private static boolean containsBranchingKeyword(String trimmed) {
        return trimmed.startsWith("if ")
                || trimmed.startsWith("if(")
                || trimmed.startsWith("else if ")
                || trimmed.startsWith("for ")
                || trimmed.startsWith("for(")
                || trimmed.startsWith("while ")
                || trimmed.startsWith("while(")
                || trimmed.startsWith("switch ")
                || trimmed.startsWith("switch(")
                || trimmed.startsWith("case ")
                || trimmed.startsWith("catch ")
                || trimmed.startsWith("catch(")
                || trimmed.contains(" ? ");
    }

    private static void applyFilters(ClassCandidate candidate) {
        String normalizedPath = candidate.classPath()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "/");

        addDiscardReasonIf(!isProductionJavaClass(candidate.classPath()), candidate, "NOT_PRODUCTION_CLASS");
        addDiscardReasonIf(candidate.nSmells() < MIN_NSMELLS, candidate, "LOW_NSMELLS<" + MIN_NSMELLS);
        addDiscardReasonIf(candidate.loc() < MIN_LOC, candidate, "LOW_LOC<" + MIN_LOC);
        addDiscardReasonIf(candidate.loc() > MAX_LOC, candidate, "TOO_LARGE_LOC>" + MAX_LOC);
        addDiscardReasonIf(candidate.methods() > MAX_METHODS, candidate, "TOO_MANY_METHODS>" + MAX_METHODS);
        addDiscardReasonIf(candidate.publicMethods() > MAX_PUBLIC_METHODS, candidate, "TOO_MANY_PUBLIC_METHODS>" + MAX_PUBLIC_METHODS);
        addDiscardReasonIf(candidate.branchingKeywords() < MIN_BRANCHING_KEYWORDS, candidate, "LOW_BRANCHING<" + MIN_BRANCHING_KEYWORDS);
        addDiscardReasonIf(candidate.methods() < MIN_METHODS, candidate, "FEW_METHODS<" + MIN_METHODS);
        addDiscardReasonIf(candidate.publicMethods() < MIN_PUBLIC_METHODS, candidate, "FEW_PUBLIC_METHODS<" + MIN_PUBLIC_METHODS);
        addDiscardReasonIf(candidate.isInterface(), candidate, "INTERFACE");
        addDiscardReasonIf(candidate.isAbstract(), candidate, "ABSTRACT_CLASS");
        addDiscardReasonIf(candidate.isEnum(), candidate, "ENUM");
        addDiscardReasonIf(candidate.isAnnotation(), candidate, "ANNOTATION");
        addDiscardReasonIf(containsAny(normalizedPath, GUI_KEYWORDS), candidate, "LIKELY_GUI_CLASS");
        addDiscardReasonIf(containsAny(normalizedPath, SIMPLE_ROLE_KEYWORDS), candidate, "LIKELY_SIMPLE_ROLE");
        addDiscardReasonIf(containsAny(normalizedPath, GENERATED_KEYWORDS), candidate, "GENERATED_CODE");
    }

    private static void addDiscardReasonIf(boolean condition,
                                           ClassCandidate candidate,
                                           String reason) {
        if (condition) {
            candidate.discardReasons.add(reason);
        }
    }

    private static List<ClassCandidate> selectClassesByNameAlgorithm(
            List<ClassCandidate> rankedClasses,
            String firstName
    ) {
        if (firstName == null || firstName.isBlank()) {
            return List.of();
        }

        char firstLetter = Character.toUpperCase(firstName.trim().charAt(0));

        if (firstLetter < 'A' || firstLetter > 'Z') {
            return List.of();
        }

        int letterNumber = firstLetter - 'A' + 1;
        int x = letterNumber % 5;

        if (rankedClasses.size() < (2 * x + 2)) {
            return List.of();
        }

        List<ClassCandidate> selected = new ArrayList<>();

        selected.add(rankedClasses.get(x));
        selected.add(rankedClasses.get(rankedClasses.size() - 1 - x));

        return selected;
    }

    private static void writeCsv(Path outputPath, List<ClassCandidate> rows)
            throws IOException {

        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(String.join(",",
                    "Rank",
                    "Project",
                    "ReleaseId",
                    "ClassPath",
                    "NSmells",
                    "LOC",
                    "BranchingKeywords",
                    "Methods",
                    "PublicMethods",
                    "IsInterface",
                    "IsEnum",
                    "IsAnnotation",
                    "IsAbstract",
                    "DiscardReasons"
            ));
            writer.newLine();
            writeRows(writer, rows);
        }
    }

    private static void writeRows(BufferedWriter writer, List<ClassCandidate> rows)
            throws IOException {
        int rank = 1;

        for (ClassCandidate row : rows) {
            writer.write(toCsvRow(rank, row));
            writer.newLine();
            rank++;
        }
    }

    private static String toCsvRow(int rank, ClassCandidate row) {
        return String.join(",",
                csv(rank),
                csv(row.project()),
                csv(row.releaseId()),
                csv(row.classPath()),
                csv(row.nSmells()),
                csv(row.loc()),
                csv(row.branchingKeywords()),
                csv(row.methods()),
                csv(row.publicMethods()),
                csv(row.isInterface()),
                csv(row.isEnum()),
                csv(row.isAnnotation()),
                csv(row.isAbstract()),
                csv(row.discardReasonsText())
        );
    }

    private static void printSummary(
            List<ClassCandidate> all,
            List<ClassCandidate> accepted,
            List<ClassCandidate> discarded,
            List<ClassCandidate> selected
    ) {
        LOGGER.info(() -> buildSummary(all, accepted, discarded, selected));
    }

    private static String buildSummary(
            List<ClassCandidate> all,
            List<ClassCandidate> accepted,
            List<ClassCandidate> discarded,
            List<ClassCandidate> selected
    ) {
        StringBuilder summary = new StringBuilder();

        summary.append(System.lineSeparator());
        summary.append("===== SUMMARY =====").append(System.lineSeparator());
        summary.append("Classi totali analizzate: ").append(all.size()).append(System.lineSeparator());
        summary.append("Classi accettate: ").append(accepted.size()).append(System.lineSeparator());
        summary.append("Classi scartate: ").append(discarded.size()).append(System.lineSeparator());
        summary.append(System.lineSeparator());
        summary.append("Top 10 classi accettate per NSmells:").append(System.lineSeparator());

        accepted.stream()
                .limit(10)
                .map(ClassRankerBySmells::formatCandidateSummary)
                .forEach(line -> summary.append(line).append(System.lineSeparator()));

        summary.append(System.lineSeparator());
        summary.append("Classi selezionate con algoritmo del nome:").append(System.lineSeparator());
        appendSelectedClassesSummary(summary, selected);

        return summary.toString();
    }

    private static void appendSelectedClassesSummary(StringBuilder summary,
                                                     List<ClassCandidate> selected) {
        if (selected.isEmpty()) {
            summary.append("Nessuna classe selezionata.").append(System.lineSeparator());
        } else {
            selected.stream()
                    .map(ClassRankerBySmells::formatCandidateSummary)
                    .forEach(line -> summary.append(line).append(System.lineSeparator()));
        }
    }

    private static String formatCandidateSummary(ClassCandidate candidate) {
        return "- "
                + candidate.classPath()
                + " | NSmells="
                + candidate.nSmells()
                + " | LOC="
                + candidate.loc()
                + " | methods="
                + candidate.methods()
                + " | publicMethods="
                + candidate.publicMethods();
    }

    private static String removeCommentsSafely(String code) {
        StringBuilder result = new StringBuilder();
        CommentState state = new CommentState();
        int index = 0;

        while (index < code.length()) {
            index = processCommentRemovalCharacter(code, index, result, state);
        }

        return result.toString();
    }

    private static int processCommentRemovalCharacter(String code,
                                                      int index,
                                                      StringBuilder result,
                                                      CommentState state) {
        if (state.inLineComment) {
            return processLineCommentCharacter(code, index, result, state);
        }

        if (state.inBlockComment) {
            return processBlockCommentCharacter(code, index, state);
        }

        return processCodeCharacter(code, index, result, state);
    }

    private static int processLineCommentCharacter(String code,
                                                   int index,
                                                   StringBuilder result,
                                                   CommentState state) {
        char current = code.charAt(index);

        if (current == '\n' || current == '\r') {
            state.inLineComment = false;
            result.append(current);
        }

        return index + 1;
    }

    private static int processBlockCommentCharacter(String code,
                                                    int index,
                                                    CommentState state) {
        char current = code.charAt(index);
        char next = nextCharOrZero(code, index);

        if (current == '*' && next == '/') {
            state.inBlockComment = false;
            return index + 2;
        }

        return index + 1;
    }

    private static int processCodeCharacter(String code,
                                            int index,
                                            StringBuilder result,
                                            CommentState state) {
        char current = code.charAt(index);
        char next = nextCharOrZero(code, index);

        if (!state.inString && !state.inChar && current == '/' && next == '/') {
            state.inLineComment = true;
            return index + 2;
        }

        if (!state.inString && !state.inChar && current == '/' && next == '*') {
            state.inBlockComment = true;
            return index + 2;
        }

        updateStringAndCharState(code, index, current, state);
        result.append(current);

        return index + 1;
    }

    private static char nextCharOrZero(String text, int index) {
        if (index + 1 < text.length()) {
            return text.charAt(index + 1);
        }

        return '\0';
    }

    private static void updateStringAndCharState(String code,
                                                 int index,
                                                 char current,
                                                 CommentState state) {
        if (!state.inChar && current == '"' && !isEscaped(code, index)) {
            state.inString = !state.inString;
        }

        if (!state.inString && current == '\'' && !isEscaped(code, index)) {
            state.inChar = !state.inChar;
        }
    }

    private static boolean isEscaped(String text, int index) {
        int backslashes = 0;
        int currentIndex = index - 1;

        while (currentIndex >= 0 && text.charAt(currentIndex) == '\\') {
            backslashes++;
            currentIndex--;
        }

        return backslashes % 2 == 1;
    }

    private static int countLoc(String code) {
        int count = 0;

        for (String line : code.split("\\R")) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }

        return count;
    }

    private static int countMethodsLineBased(String code) {
        int count = 0;

        for (String line : code.split("\\R")) {
            String trimmed = line.trim();

            if (looksLikeMethodDeclaration(trimmed)) {
                count++;
            }
        }

        return count;
    }

    private static boolean isProductionJavaClass(String path) {
        String normalizedPath = path
                .replace("\\", "/")
                .trim();

        return normalizedPath.endsWith(".java")
                && normalizedPath.contains("/src/main/java/")
                && !normalizedPath.contains("/src/test/java")
                && !normalizedPath.contains("/src/it/")
                && !normalizedPath.contains("/testDependencies/")
                && !normalizedPath.contains("/test-dependencies/")
                && !normalizedPath.contains("/testFixtures/")
                && !normalizedPath.contains("/test-fixtures/")
                && !normalizedPath.contains("examples/src")
                && !normalizedPath.contains("junit5/")
                && !normalizedPath.contains("kubernetes/")
                && !normalizedPath.contains("/osgi");
    }

    private static int countPublicMethodsLineBased(String code) {
        int count = 0;

        for (String line : code.split("\\R")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("public ") && looksLikeMethodDeclaration(trimmed)) {
                count++;
            }
        }

        return count;
    }

    private static boolean looksLikeMethodDeclaration(String line) {
        if (!line.contains("(") || !line.contains(")") || !line.endsWith("{")) {
            return false;
        }

        if (line.startsWith("if ")
                || line.startsWith("for ")
                || line.startsWith("while ")
                || line.startsWith("switch ")
                || line.startsWith("catch ")
                || line.startsWith("return ")) {
            return false;
        }

        return line.contains("public ")
                || line.contains("private ")
                || line.contains("protected ")
                || line.contains("static ")
                || line.contains("final ")
                || line.contains("synchronized ");
    }

    private static boolean containsAny(String text, List<String> keywords) {
        String normalized = text.toLowerCase(Locale.ROOT).replace("\\", "/");

        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;
        int index = 0;

        while (index < line.length()) {
            CsvParseStep step = parseCsvCharacter(line, index, insideQuotes, current, columns);
            insideQuotes = step.insideQuotes();
            index = step.nextIndex();
        }

        columns.add(current.toString());
        return columns;
    }

    private static CsvParseStep parseCsvCharacter(String line,
                                                  int index,
                                                  boolean insideQuotes,
                                                  StringBuilder current,
                                                  List<String> columns) {
        char currentChar = line.charAt(index);

        if (currentChar == '"') {
            return parseQuoteCharacter(line, index, insideQuotes, current);
        }

        if (currentChar == ',' && !insideQuotes) {
            columns.add(current.toString());
            current.setLength(0);
            return new CsvParseStep(index + 1, insideQuotes);
        }

        current.append(currentChar);
        return new CsvParseStep(index + 1, insideQuotes);
    }

    private static CsvParseStep parseQuoteCharacter(String line,
                                                    int index,
                                                    boolean insideQuotes,
                                                    StringBuilder current) {
        if (isEscapedCsvQuote(line, index, insideQuotes)) {
            current.append('"');
            return new CsvParseStep(index + 2, insideQuotes);
        }

        return new CsvParseStep(index + 1, !insideQuotes);
    }

    private static boolean isEscapedCsvQuote(String line, int index, boolean insideQuotes) {
        return insideQuotes
                && index + 1 < line.length()
                && line.charAt(index + 1) == '"';
    }

    private static String csv(Object value) {
        String text = String.valueOf(value);

        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }

        return text;
    }

    private record SourceMetrics(
            int loc,
            int methods,
            int publicMethods,
            int branchingKeywords,
            boolean isInterface,
            boolean isEnum,
            boolean isAnnotation,
            boolean isAbstract
    ) {
    }

    private record CsvParseStep(int nextIndex, boolean insideQuotes) {
    }

    private static class CommentState {

        private boolean inBlockComment;
        private boolean inLineComment;
        private boolean inString;
        private boolean inChar;
    }

    private static class ClassCandidate {

        private final String project;
        private final int releaseId;
        private final String classPath;
        private final int nSmells;
        private final int loc;
        private final int methods;
        private final int publicMethods;
        private final boolean isInterface;
        private final boolean isEnum;
        private final boolean isAnnotation;
        private final boolean isAbstract;
        private final int branchingKeywords;

        private final List<String> discardReasons = new ArrayList<>();

        private ClassCandidate(Builder builder) {
            this.project = builder.project;
            this.releaseId = builder.releaseId;
            this.classPath = builder.classPath;
            this.nSmells = builder.nSmells;
            this.loc = builder.loc;
            this.methods = builder.methods;
            this.publicMethods = builder.publicMethods;
            this.branchingKeywords = builder.branchingKeywords;
            this.isInterface = builder.isInterface;
            this.isEnum = builder.isEnum;
            this.isAnnotation = builder.isAnnotation;
            this.isAbstract = builder.isAbstract;
        }

        private static Builder builder() {
            return new Builder();
        }

        private int branchingKeywords() {
            return branchingKeywords;
        }

        private String project() {
            return project;
        }

        private int releaseId() {
            return releaseId;
        }

        private String classPath() {
            return classPath;
        }

        private int nSmells() {
            return nSmells;
        }

        private int loc() {
            return loc;
        }

        private int methods() {
            return methods;
        }

        private int publicMethods() {
            return publicMethods;
        }

        private boolean isInterface() {
            return isInterface;
        }

        private boolean isEnum() {
            return isEnum;
        }

        private boolean isAnnotation() {
            return isAnnotation;
        }

        private boolean isAbstract() {
            return isAbstract;
        }

        private String discardReasonsText() {
            return String.join(";", discardReasons);
        }

        private static class Builder {

            private String project;
            private int releaseId;
            private String classPath;
            private int nSmells;
            private int loc;
            private int methods;
            private int publicMethods;
            private boolean isInterface;
            private boolean isEnum;
            private boolean isAnnotation;
            private boolean isAbstract;
            private int branchingKeywords;

            private Builder project(String project) {
                this.project = project;
                return this;
            }

            private Builder releaseId(int releaseId) {
                this.releaseId = releaseId;
                return this;
            }

            private Builder classPath(String classPath) {
                this.classPath = classPath;
                return this;
            }

            private Builder nSmells(int nSmells) {
                this.nSmells = nSmells;
                return this;
            }

            private Builder loc(int loc) {
                this.loc = loc;
                return this;
            }

            private Builder methods(int methods) {
                this.methods = methods;
                return this;
            }

            private Builder publicMethods(int publicMethods) {
                this.publicMethods = publicMethods;
                return this;
            }

            private Builder branchingKeywords(int branchingKeywords) {
                this.branchingKeywords = branchingKeywords;
                return this;
            }

            private Builder isInterface(boolean isInterface) {
                this.isInterface = isInterface;
                return this;
            }

            private Builder isEnum(boolean isEnum) {
                this.isEnum = isEnum;
                return this;
            }

            private Builder isAnnotation(boolean isAnnotation) {
                this.isAnnotation = isAnnotation;
                return this;
            }

            private Builder isAbstract(boolean isAbstract) {
                this.isAbstract = isAbstract;
                return this;
            }

            private ClassCandidate build() {
                return new ClassCandidate(this);
            }
        }
    }
}
