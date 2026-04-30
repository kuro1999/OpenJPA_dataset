package it.uniroma2.isw2.csv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unisce label, metriche e smell in un unico dataset finale.
 *
 * La grana del merge è la coppia:
 * Project + ReleaseID + ClassPath.
 *
 * La colonna SMELL_DENSITY viene calcolata come:
 * NSMELLS / max(1, SIZE_LOC).
 */
public class FinalDatasetMerger {

    private static final List<String> OUTPUT_HEADERS = Arrays.asList(
            "Project",
            "ReleaseID",
            "ReleaseName",
            "ReleaseIndex",
            "ClassPath",

            "SIZE_LOC",
            "NOM",
            "AVG_METHOD_SIZE",
            "CYCLO_COMPLEXITY",
            "FAN_OUT",

            "NR",
            "FIX_RATE",
            "NAUTH",

            "LOC_ADDED",
            "MAX_LOC_ADDED",
            "CHURN",
            "MAX_CHURN",
            "MAX_CHANGE_SET_SIZE",
            "AVG_MODIFIED_DIRS",

            "CLASS_AGE",
            "AGE_SINCE_LAST_CHANGE",
            "OWNERSHIP_RATIO",
            "CROSS_DIRECTORY_CHANGE_RATIO",

            "NSMELLS",
            "SMELL_DENSITY",

            "Bugginess"
    );

    private static final List<String> METRIC_HEADERS = Arrays.asList(
            "SIZE_LOC",
            "NOM",
            "AVG_METHOD_SIZE",
            "CYCLO_COMPLEXITY",
            "FAN_OUT",
            "NR",
            "FIX_RATE",
            "NAUTH",
            "LOC_ADDED",
            "MAX_LOC_ADDED",
            "CHURN",
            "MAX_CHURN",
            "MAX_CHANGE_SET_SIZE",
            "AVG_MODIFIED_DIRS",
            "CLASS_AGE",
            "AGE_SINCE_LAST_CHANGE",
            "OWNERSHIP_RATIO",
            "CROSS_DIRECTORY_CHANGE_RATIO"
    );

    private FinalDatasetMerger() {
    }

    public static void buildFinalDataset(String metricsFile,
                                         String labelsFile,
                                         String smellsFile,
                                         String outputFile) throws IOException {
        buildFinalDataset(
                Path.of(metricsFile),
                Path.of(labelsFile),
                Path.of(smellsFile),
                Path.of(outputFile)
        );
    }

    public static void buildFinalDataset(Path metricsFile,
                                         Path labelsFile,
                                         Path smellsFile,
                                         Path outputFile) throws IOException {
        CsvTable metricsTable = readCsv(metricsFile);
        CsvTable labelsTable = readCsv(labelsFile);
        CsvTable smellsTable = readCsv(smellsFile);

        Map<String, CsvRow> metricsByKey = indexByClassRelease(metricsTable);
        Map<String, CsvRow> smellsByKey = indexByClassRelease(smellsTable);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writeHeader(writer);

            for (CsvRow labelRow : labelsTable.rows) {
                String key = buildKey(labelRow);

                CsvRow metricRow = metricsByKey.get(key);
                if (metricRow == null) {
                    throw new IOException("Metriche mancanti per la coppia classe-release: " + key);
                }

                CsvRow smellRow = smellsByKey.get(key);

                int sizeLoc = parseIntOrZero(metricRow.get("SIZE_LOC"));
                int nSmells = extractNSmells(smellRow);
                double smellDensity = safeRatio(nSmells, Math.max(1, sizeLoc));

                List<String> outputValues = new ArrayList<>();

                outputValues.add(labelRow.get("Project"));
                outputValues.add(labelRow.get("ReleaseID"));
                outputValues.add(labelRow.get("ReleaseName"));
                outputValues.add(labelRow.get("ReleaseIndex"));
                outputValues.add(labelRow.get("ClassPath"));

                for (String metricHeader : METRIC_HEADERS) {
                    outputValues.add(metricRow.get(metricHeader));
                }

                outputValues.add(String.valueOf(nSmells));
                outputValues.add(formatDouble(smellDensity));
                outputValues.add(labelRow.get("Bugginess"));

                writeRow(writer, outputValues);
            }
        }
    }

    private static Map<String, CsvRow> indexByClassRelease(CsvTable table) {
        Map<String, CsvRow> result = new LinkedHashMap<>();

        for (CsvRow row : table.rows) {
            String key = buildKey(row);
            result.put(key, row);
        }

        return result;
    }

    private static String buildKey(CsvRow row) {
        String project = normalizeValue(row.get("Project"));
        String releaseId = normalizeValue(row.get("ReleaseID"));
        String classPath = normalizePath(row.get("ClassPath"));

        return project + "|" + releaseId + "|" + classPath;
    }

    private static int extractNSmells(CsvRow smellRow) {
        if (smellRow == null) {
            return 0;
        }

        String value = smellRow.get("NSmells");

        if (value.isBlank()) {
            value = smellRow.get("NSMELLS");
        }

        return parseIntOrZero(value);
    }

    private static CsvTable readCsv(Path filePath) throws IOException {
        List<String> headers = new ArrayList<>();
        List<CsvRow> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();

            if (headerLine == null || headerLine.isBlank()) {
                throw new IOException("CSV vuoto o senza header: " + filePath);
            }

            headers = parseCsvLine(removeBom(headerLine));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseCsvLine(line);
                CsvRow row = new CsvRow();

                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    String value = "";

                    if (i < values.size()) {
                        value = values.get(i);
                    }

                    row.put(header, value);
                }

                rows.add(row);
            }
        }

        return new CsvTable(headers, rows);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();

        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (insideQuotes
                        && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {
                    currentValue.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (currentChar == ',' && !insideQuotes) {
                values.add(currentValue.toString().trim());
                currentValue.setLength(0);
            } else {
                currentValue.append(currentChar);
            }
        }

        values.add(currentValue.toString().trim());

        return values;
    }

    private static void writeHeader(BufferedWriter writer) throws IOException {
        writeRow(writer, OUTPUT_HEADERS);
    }

    private static void writeRow(BufferedWriter writer,
                                 List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(",");
            }

            writer.write(escapeCsv(values.get(i)));
        }

        writer.newLine();
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean mustBeQuoted = value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");

        String escapedValue = value.replace("\"", "\"\"");

        if (mustBeQuoted) {
            return "\"" + escapedValue + "\"";
        }

        return escapedValue;
    }

    private static int parseIntOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            try {
                return (int) Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }

    private static double safeRatio(double numerator,
                                    double denominator) {
        if (denominator <= 0) {
            return 0.0;
        }

        return numerator / denominator;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }

        return header
                .replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }

        return path
                .replace("\\", "/")
                .trim();
    }

    private static String removeBom(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\uFEFF", "");
    }

    private static class CsvTable {

        private final List<String> headers;
        private final List<CsvRow> rows;

        private CsvTable(List<String> headers,
                         List<CsvRow> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    private static class CsvRow {

        private final Map<String, String> valuesByHeader = new LinkedHashMap<>();

        private void put(String header,
                         String value) {
            valuesByHeader.put(normalizeHeader(header), value);
        }

        private String get(String header) {
            String value = valuesByHeader.get(normalizeHeader(header));

            if (value == null) {
                return "";
            }

            return value;
        }
    }
}