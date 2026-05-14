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

public class ClassRankerBySmells {

    /*
     * ==========================
     * CONFIGURAZIONE STATICA
     * ==========================
     *
     * Il repository deve già essere posizionato sull'ultima release da analizzare.
     * Non viene fatto checkout e non viene applicato il filtro del 34%.
     */

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
            "resultset", "descriptor", "definition", "info" , "parser" , "query" ,
            "decorator" , "asm" , "hashset"
    );

    private static final List<String> GENERATED_KEYWORDS = List.of(
            "generated", "target/generated", "build/generated", "protobuf",
            "grpc", "thrift", "avro", "openapi", "swagger" , "generator"
    );

    private ClassRankerBySmells() {
    }

    public static void main(String[] args) {
        try {
            System.out.println("Avvio ranking classi per smell.");
            printFilterConfiguration();
            System.out.println("Posizionamento repository sull'ultima release disponibile: " + REPOSITORY_PATH);

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

            accepted.sort(
                    Comparator.comparingInt(ClassCandidate::nSmells).reversed()
                            .thenComparing(Comparator.comparingInt(ClassCandidate::loc).reversed())
                            .thenComparing(Comparator.comparingInt(ClassCandidate::methods).reversed())
                            .thenComparing(ClassCandidate::classPath)
            );

            discarded.sort(
                    Comparator.comparingInt(ClassCandidate::nSmells).reversed()
                            .thenComparing(ClassCandidate::discardReasonsText)
                            .thenComparing(ClassCandidate::classPath)
            );

            List<ClassCandidate> selected = selectClassesByNameAlgorithm(accepted, FIRST_NAME);

            writeCsv(OUTPUT_DIRECTORY.resolve("ranked_classes.csv"), accepted);
            writeCsv(OUTPUT_DIRECTORY.resolve("discarded_classes.csv"), discarded);
            writeCsv(OUTPUT_DIRECTORY.resolve("selected_classes.csv"), selected);

            printSummary(candidates, accepted, discarded, selected);

            System.out.println("Ranking completato.");
            System.out.println("Output directory: " + OUTPUT_DIRECTORY);

        } catch (IOException | InterruptedException e) {
            System.err.println("Errore durante il ranking delle classi: " + e.getMessage());
            e.printStackTrace();

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void printFilterConfiguration() {
        System.out.println("Filtri configurati:");
        System.out.println("- MIN_LOC = " + MIN_LOC);
        System.out.println("- MIN_METHODS = " + MIN_METHODS);
        System.out.println("- MIN_PUBLIC_METHODS = " + MIN_PUBLIC_METHODS);
        System.out.println("- MIN_NSMELLS = " + MIN_NSMELLS);
    }

    private static void checkoutLatestRelease() throws IOException, InterruptedException {
        String latestTag = findLatestTag();

        runGitCommand("git", "checkout", "-f", latestTag);

        String currentCommit = runGitCommandAndGetFirstLine(
                "git",
                "rev-parse",
                "HEAD"
        );

        System.out.println("Checkout effettuato sulla release: " + latestTag);
        System.out.println("Commit corrente: " + currentCommit);
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

        System.out.println("File Java di produzione trovati: " + productionJavaFiles.size());
        System.out.println("File list PMD: " + fileListPath);

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

        System.out.println("CSV NSmells generato in: " + smellCsvPath);

        return smellCsvPath;
    }

    private static List<ClassCandidate> buildCandidatesFromSmellCsv(Path smellCsvPath)
            throws IOException {

        List<ClassCandidate> candidates = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(smellCsvPath, StandardCharsets.UTF_8)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> columns = parseCsvLine(line);

                if (columns.size() < 4) {
                    continue;
                }

                String project = columns.get(0).trim();
                int releaseId = Integer.parseInt(columns.get(1).trim());
                String classPath = columns.get(2).trim();
                int nSmells = Integer.parseInt(columns.get(3).trim());

                Path absoluteClassPath = REPOSITORY_PATH.resolve(classPath).normalize();

                SourceMetrics metrics = analyzeSourceFile(absoluteClassPath);

                candidates.add(new ClassCandidate(
                        project,
                        releaseId,
                        classPath,
                        nSmells,
                        metrics.loc(),
                        metrics.methods(),
                        metrics.publicMethods(),
                        metrics.branchingKeywords(),
                        metrics.isInterface(),
                        metrics.isEnum(),
                        metrics.isAnnotation(),
                        metrics.isAbstract()
                ));
            }
        }

        return candidates;
    }

    private static SourceMetrics analyzeSourceFile(Path sourcePath) {
        if (!Files.exists(sourcePath)) {
            return new SourceMetrics(0, 0, 0, 0, false, false, false, false);
        }

        try {
            String code = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String codeWithoutComments = removeCommentsSafely(code);

            int loc = countLoc(codeWithoutComments);
            int branchingKeywords = countBranchingKeywords(code);

            CompilationUnit compilationUnit = StaticJavaParser.parse(code);

            String simpleClassName = sourcePath.getFileName()
                    .toString()
                    .replace(".java", "");

            Optional<ClassOrInterfaceDeclaration> classOrInterface =
                    compilationUnit.findFirst(
                            ClassOrInterfaceDeclaration.class,
                            declaration -> declaration.getNameAsString().equals(simpleClassName)
                    );

            boolean isInterface = false;
            boolean isAbstract = false;
            boolean isEnum = false;
            boolean isAnnotation = false;

            int methods = 0;
            int publicMethods = 0;

            if (classOrInterface.isPresent()) {
                ClassOrInterfaceDeclaration declaration = classOrInterface.get();

                isInterface = declaration.isInterface();
                isAbstract = declaration.isAbstract();

                methods = declaration.findAll(MethodDeclaration.class).size()
                        + declaration.findAll(ConstructorDeclaration.class).size();

                publicMethods = (int) declaration.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(MethodDeclaration::isPublic)
                        .count();

                publicMethods += (int) declaration.findAll(ConstructorDeclaration.class)
                        .stream()
                        .filter(ConstructorDeclaration::isPublic)
                        .count();

            } else {
                isEnum = compilationUnit.findFirst(
                        EnumDeclaration.class,
                        declaration -> declaration.getNameAsString().equals(simpleClassName)
                ).isPresent();

                isAnnotation = compilationUnit.findFirst(
                        AnnotationDeclaration.class,
                        declaration -> declaration.getNameAsString().equals(simpleClassName)
                ).isPresent();

                methods = compilationUnit.findAll(MethodDeclaration.class).size()
                        + compilationUnit.findAll(ConstructorDeclaration.class).size();

                publicMethods = (int) compilationUnit.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(MethodDeclaration::isPublic)
                        .count();

                publicMethods += (int) compilationUnit.findAll(ConstructorDeclaration.class)
                        .stream()
                        .filter(ConstructorDeclaration::isPublic)
                        .count();
            }

            return new SourceMetrics(
                    loc,
                    methods,
                    publicMethods,
                    branchingKeywords,
                    isInterface,
                    isEnum,
                    isAnnotation,
                    isAbstract
            );

        } catch (Exception parsingException) {
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
                return new SourceMetrics(0, 0, 0, 0, false, false, false, false);
            }
        }
    }

    private static int countBranchingKeywords(String code) {
        int count = 0;

        String codeWithoutComments = removeCommentsSafely(code);

        for (String line : codeWithoutComments.split("\\R")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("if ")
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
                    || trimmed.contains(" ? ")) {
                count++;
            }
        }

        return count;
    }

    private static void applyFilters(ClassCandidate candidate) {
        String normalizedPath = candidate.classPath()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "/");

        if (!isProductionJavaClass(candidate.classPath())) {
            candidate.discardReasons.add("NOT_PRODUCTION_CLASS");
        }

        if (candidate.nSmells() < MIN_NSMELLS) {
            candidate.discardReasons.add("LOW_NSMELLS<" + MIN_NSMELLS);
        }

        if (candidate.loc() < MIN_LOC) {
            candidate.discardReasons.add("LOW_LOC<" + MIN_LOC);
        }

        if (candidate.loc() > MAX_LOC) {
            candidate.discardReasons.add("TOO_LARGE_LOC>" + MAX_LOC);
        }

        if (candidate.methods() > MAX_METHODS) {
            candidate.discardReasons.add("TOO_MANY_METHODS>" + MAX_METHODS);
        }

        if (candidate.publicMethods() > MAX_PUBLIC_METHODS) {
            candidate.discardReasons.add("TOO_MANY_PUBLIC_METHODS>" + MAX_PUBLIC_METHODS);
        }

        if (candidate.branchingKeywords() < MIN_BRANCHING_KEYWORDS) {
            candidate.discardReasons.add("LOW_BRANCHING<" + MIN_BRANCHING_KEYWORDS);
        }

        if (candidate.methods() < MIN_METHODS) {
            candidate.discardReasons.add("FEW_METHODS<" + MIN_METHODS);
        }

        if (candidate.publicMethods() < MIN_PUBLIC_METHODS) {
            candidate.discardReasons.add("FEW_PUBLIC_METHODS<" + MIN_PUBLIC_METHODS);
        }

        if (candidate.isInterface()) {
            candidate.discardReasons.add("INTERFACE");
        }

        if (candidate.isAbstract()) {
            candidate.discardReasons.add("ABSTRACT_CLASS");
        }

        if (candidate.isEnum()) {
            candidate.discardReasons.add("ENUM");
        }

        if (candidate.isAnnotation()) {
            candidate.discardReasons.add("ANNOTATION");
        }

        if (containsAny(normalizedPath, GUI_KEYWORDS)) {
            candidate.discardReasons.add("LIKELY_GUI_CLASS");
        }

        if (containsAny(normalizedPath, SIMPLE_ROLE_KEYWORDS)) {
            candidate.discardReasons.add("LIKELY_SIMPLE_ROLE");
        }

        if (containsAny(normalizedPath, GENERATED_KEYWORDS)) {
            candidate.discardReasons.add("GENERATED_CODE");
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

            int rank = 1;

            for (ClassCandidate row : rows) {
                writer.write(csv(rank));
                writer.write(",");
                writer.write(csv(row.project()));
                writer.write(",");
                writer.write(csv(row.releaseId()));
                writer.write(",");
                writer.write(csv(row.classPath()));
                writer.write(",");
                writer.write(csv(row.nSmells()));
                writer.write(",");
                writer.write(csv(row.loc()));
                writer.write(",");
                writer.write(csv(row.branchingKeywords()));
                writer.write(",");
                writer.write(csv(row.methods()));
                writer.write(",");
                writer.write(csv(row.publicMethods()));
                writer.write(",");
                writer.write(csv(row.isInterface()));
                writer.write(",");
                writer.write(csv(row.isEnum()));
                writer.write(",");
                writer.write(csv(row.isAnnotation()));
                writer.write(",");
                writer.write(csv(row.isAbstract()));
                writer.write(",");
                writer.write(csv(row.discardReasonsText()));
                writer.newLine();

                rank++;
            }
        }
    }

    private static void printSummary(
            List<ClassCandidate> all,
            List<ClassCandidate> accepted,
            List<ClassCandidate> discarded,
            List<ClassCandidate> selected
    ) {
        System.out.println();
        System.out.println("===== SUMMARY =====");
        System.out.println("Classi totali analizzate: " + all.size());
        System.out.println("Classi accettate: " + accepted.size());
        System.out.println("Classi scartate: " + discarded.size());

        System.out.println();
        System.out.println("Top 10 classi accettate per NSmells:");

        accepted.stream()
                .limit(10)
                .forEach(candidate -> System.out.println(
                        "- "
                                + candidate.classPath()
                                + " | NSmells="
                                + candidate.nSmells()
                                + " | LOC="
                                + candidate.loc()
                                + " | methods="
                                + candidate.methods()
                                + " | publicMethods="
                                + candidate.publicMethods()
                ));

        System.out.println();
        System.out.println("Classi selezionate con algoritmo del nome:");

        if (selected.isEmpty()) {
            System.out.println("Nessuna classe selezionata.");
        } else {
            for (ClassCandidate candidate : selected) {
                System.out.println(
                        "- "
                                + candidate.classPath()
                                + " | NSmells="
                                + candidate.nSmells()
                                + " | LOC="
                                + candidate.loc()
                                + " | methods="
                                + candidate.methods()
                                + " | publicMethods="
                                + candidate.publicMethods()
                );
            }
        }
    }

    private static String removeCommentsSafely(String code) {
        StringBuilder result = new StringBuilder();

        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < code.length(); i++) {
            char current = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    result.append(current);
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (!inString && !inChar && current == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }

            if (!inString && !inChar && current == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }

            if (!inChar && current == '"' && !isEscaped(code, i)) {
                inString = !inString;
            }

            if (!inString && current == '\'' && !isEscaped(code, i)) {
                inChar = !inChar;
            }

            result.append(current);
        }

        return result.toString();
    }

    private static boolean isEscaped(String text, int index) {
        int backslashes = 0;

        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            backslashes++;
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

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (currentChar == ',' && !insideQuotes) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }

        columns.add(current.toString());

        return columns;
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

        private ClassCandidate(
                String project,
                int releaseId,
                String classPath,
                int nSmells,
                int loc,
                int methods,
                int publicMethods,
                int branchingKeywords,
                boolean isInterface,
                boolean isEnum,
                boolean isAnnotation,
                boolean isAbstract
        ) {
            this.project = project;
            this.releaseId = releaseId;
            this.classPath = classPath;
            this.nSmells = nSmells;
            this.loc = loc;
            this.methods = methods;
            this.publicMethods = publicMethods;
            this.branchingKeywords = branchingKeywords;
            this.isInterface = isInterface;
            this.isEnum = isEnum;
            this.isAnnotation = isAnnotation;
            this.isAbstract = isAbstract;
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
    }
}