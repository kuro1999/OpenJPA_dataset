package it.uniroma2.isw2.smell;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PmdSmellCsvBuilder {

    public void build(
            String project,
            int releaseId,
            Path repositoryPath,
            Path fileListPath,
            Path pmdReportPath,
            Path outputPath
    ) throws IOException {

        Map<String, Integer> smellsByClassPath = readPmdReport(repositoryPath, pmdReportPath);
        List<String> productionClasses = readProductionClasses(repositoryPath, fileListPath);

        List<ClassReleaseSmell> rows = new ArrayList<>();

        for (String classPath : productionClasses) {
            int nSmells = smellsByClassPath.getOrDefault(classPath, 0);

            rows.add(new ClassReleaseSmell(
                    project,
                    releaseId,
                    classPath,
                    nSmells
            ));
        }

        writeOutput(outputPath, rows);
    }

    private Map<String, Integer> readPmdReport(Path repositoryPath, Path pmdReportPath) throws IOException {
        Map<String, Integer> smellsByClassPath = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(pmdReportPath)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsvLine(line);

                if (columns.size() < 3) {
                    continue;
                }

                String filePath = columns.get(2);
                String classPath = toRepositoryRelativePath(repositoryPath, filePath);

                smellsByClassPath.merge(classPath, 1, Integer::sum);
            }
        }

        return smellsByClassPath;
    }

    private List<String> readProductionClasses(Path repositoryPath, Path fileListPath) throws IOException {
        List<String> productionClasses = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(fileListPath)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String classPath = toRepositoryRelativePath(repositoryPath, line);
                productionClasses.add(classPath);
            }
        }

        return productionClasses;
    }

    private String toRepositoryRelativePath(Path repositoryPath, String filePath) {
        Path normalizedRepositoryPath = repositoryPath.toAbsolutePath().normalize();
        Path normalizedFilePath = Path.of(filePath).toAbsolutePath().normalize();

        return normalizedRepositoryPath.relativize(normalizedFilePath)
                .toString()
                .replace("\\", "/")
                .trim();
    }

    private void writeOutput(Path outputPath, List<ClassReleaseSmell> rows) throws IOException {
        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("Project,ReleaseId,ClassPath,NSmells");
            writer.newLine();

            for (ClassReleaseSmell row : rows) {
                writer.write(escapeCsv(row.project()));
                writer.write(",");
                writer.write(String.valueOf(row.releaseId()));
                writer.write(",");
                writer.write(escapeCsv(row.classPath()));
                writer.write(",");
                writer.write(String.valueOf(row.nSmells()));
                writer.newLine();
            }
        }
    }

    private List<String> parseCsvLine(String line) {
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

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}