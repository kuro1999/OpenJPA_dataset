package it.uniroma2.isw2.metric;

import it.uniroma2.isw2.csv.CsvUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Scrive le metriche calcolate per ogni coppia classe-release.
 *
 * Gli smell non sono scritti qui perché vengono calcolati e gestiti separatamente.
 */
public class ClassReleaseMetricCsvWriter {

    private ClassReleaseMetricCsvWriter() {
    }

    public static void writeClassReleaseMetrics(String filePath,
                                                List<ClassReleaseMetric> metrics) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.append("Project,ReleaseID,ClassPath,")
                    .append("SIZE_LOC,NOM,AVG_METHOD_SIZE,CYCLO_COMPLEXITY,FAN_OUT,")
                    .append("NR,FIX_RATE,NAUTH,")
                    .append("LOC_ADDED,MAX_LOC_ADDED,CHURN,MAX_CHURN,")
                    .append("MAX_CHANGE_SET_SIZE,AVG_MODIFIED_DIRS,")
                    .append("CLASS_AGE,AGE_SINCE_LAST_CHANGE,")
                    .append("OWNERSHIP_RATIO,CROSS_DIRECTORY_CHANGE_RATIO\n");

            for (ClassReleaseMetric metric : metrics) {
                writer.append(CsvUtils.escapeCsv(metric.getProject())).append(",");
                writer.append(CsvUtils.escapeCsv(metric.getReleaseId())).append(",");
                writer.append(CsvUtils.escapeCsv(metric.getClassPath())).append(",");

                writer.append(String.valueOf(metric.getSizeLoc())).append(",");
                writer.append(String.valueOf(metric.getNom())).append(",");
                writer.append(formatDouble(metric.getAvgMethodSize())).append(",");
                writer.append(String.valueOf(metric.getCycloComplexity())).append(",");
                writer.append(String.valueOf(metric.getFanOut())).append(",");

                writer.append(String.valueOf(metric.getNr())).append(",");
                writer.append(formatDouble(metric.getFixRate())).append(",");
                writer.append(String.valueOf(metric.getNAuth())).append(",");

                writer.append(String.valueOf(metric.getLocAdded())).append(",");
                writer.append(String.valueOf(metric.getMaxLocAdded())).append(",");
                writer.append(String.valueOf(metric.getChurn())).append(",");
                writer.append(String.valueOf(metric.getMaxChurn())).append(",");

                writer.append(String.valueOf(metric.getMaxChangeSetSize())).append(",");
                writer.append(formatDouble(metric.getAvgModifiedDirs())).append(",");

                writer.append(String.valueOf(metric.getClassAge())).append(",");
                writer.append(String.valueOf(metric.getAgeSinceLastChange())).append(",");

                writer.append(formatDouble(metric.getOwnershipRatio())).append(",");
                writer.append(formatDouble(metric.getCrossDirectoryChangeRatio())).append("\n");
            }
        }
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.6f", value);
    }
}