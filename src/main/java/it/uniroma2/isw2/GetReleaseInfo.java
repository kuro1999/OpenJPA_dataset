package it.uniroma2.isw2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GetReleaseInfo {

    private static final Logger LOGGER = Logger.getLogger(GetReleaseInfo.class.getName());

    private static final String PROJECT_NAME = "OPENJPA";
    private static final String RELEASES_ENDPOINT =
            "https://issues.apache.org/jira/rest/api/2/project/";
    private static final String OUTPUT_FILE_SUFFIX = "VersionInfo.csv";
    private static final String CSV_HEADER = "Index,Version ID,Version Name,Date";
    private static final int MIN_RELEASES_REQUIRED = 6;

    private static final Map<LocalDateTime, String> RELEASE_NAMES = new HashMap<>();
    private static final Map<LocalDateTime, String> RELEASE_IDS = new HashMap<>();
    private static final List<LocalDateTime> RELEASES = new ArrayList<>();

    private static int numVersions;

    private GetReleaseInfo() {
        // Utility class.
    }

    public static void main(String[] args) {
        try {
            generateReleaseInfo(PROJECT_NAME);
        } catch (IOException | JSONException e) {
            LOGGER.log(Level.SEVERE, "Errore durante il recupero delle release.", e);
        }
    }

    private static void generateReleaseInfo(String projectName) throws IOException, JSONException {
        String url = RELEASES_ENDPOINT + projectName;
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            addReleaseIfDateIsAvailable(version);
        }

        if (RELEASES.size() >= MIN_RELEASES_REQUIRED) {
            writeVersionInfoCsv(projectName);
        }
    }

    private static void addReleaseIfDateIsAvailable(JSONObject version) throws JSONException {
        if (version.has("releaseDate")) {
            String name = version.has("name") ? version.get("name").toString() : "";
            String id = version.has("id") ? version.get("id").toString() : "";

            addRelease(version.get("releaseDate").toString(), name, id);
        }
    }

    private static void writeVersionInfoCsv(String projectName) throws IOException {
        String outputFileName = projectName + OUTPUT_FILE_SUFFIX;
        Path outputPath = Path.of(outputFileName);

        numVersions = RELEASES.size();

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            writer.newLine();

            for (int i = 0; i < RELEASES.size(); i++) {
                LocalDateTime releaseDate = RELEASES.get(i);
                int index = i + 1;

                writer.write(String.valueOf(index));
                writer.write(",");
                writer.write(RELEASE_IDS.get(releaseDate));
                writer.write(",");
                writer.write(RELEASE_NAMES.get(releaseDate));
                writer.write(",");
                writer.write(releaseDate.toString());
                writer.newLine();
            }
        }

        LOGGER.info(() -> "File CSV generato correttamente: " + outputPath.toAbsolutePath());
    }

    public static void addRelease(String strDate, String name, String id) {
        LocalDate date = LocalDate.parse(strDate);
        LocalDateTime dateTime = date.atStartOfDay();

        if (!RELEASES.contains(dateTime)) {
            RELEASES.add(dateTime);
        }

        RELEASE_NAMES.put(dateTime, name);
        RELEASE_IDS.put(dateTime, id);
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream inputStream = new URL(url).openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8)
             )) {
            return new JSONObject(readAll(reader));
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        int currentChar;

        while ((currentChar = reader.read()) != -1) {
            content.append((char) currentChar);
        }

        return content.toString();
    }

    public static int getNumVersions() {
        return numVersions;
    }

    public static Map<LocalDateTime, String> getReleaseNames() {
        return Map.copyOf(RELEASE_NAMES);
    }

    public static Map<LocalDateTime, String> getReleaseIds() {
        return Map.copyOf(RELEASE_IDS);
    }

    public static List<LocalDateTime> getReleases() {
        return List.copyOf(RELEASES);
    }
}