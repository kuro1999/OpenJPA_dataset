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
import java.util.logging.Level;
import java.util.logging.Logger;

class RetrieveTicketsID {

    private static final Logger LOGGER = Logger.getLogger(RetrieveTicketsID.class.getName());

    private static final String PROJECT_NAME = "OPENJPA";
    private static final String OUTPUT_FILE_SUFFIX = "Tickets.csv";
    private static final String CSV_HEADER = "TicketID,CreationDate,ResolutionDate,AffectedVersions\n";

    private static final String ISSUES_FIELD = "issues";
    private static final String TOTAL_FIELD = "total";
    private static final String FIELDS_FIELD = "fields";
    private static final String KEY_FIELD = "key";
    private static final String CREATED_FIELD = "created";
    private static final String RESOLUTION_DATE_FIELD = "resolutiondate";
    private static final String VERSIONS_FIELD = "versions";
    private static final String NAME_FIELD = "name";

    private static final int PAGE_SIZE = 1000;

    private RetrieveTicketsID() {
        // Utility class.
    }

    public static void main(String[] args) {
        try {
            retrieveTickets(PROJECT_NAME);
            LOGGER.info("CSV creato con successo.");
        } catch (IOException | JSONException e) {
            LOGGER.log(Level.SEVERE, "Errore nella creazione del CSV dei ticket.", e);
        }
    }

    private static void retrieveTickets(String projectName) throws IOException, JSONException {
        Path outputPath = Path.of(projectName + OUTPUT_FILE_SUFFIX);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);

            int startAt = 0;
            int total;

            do {
                JSONObject json = readJsonFromUrl(buildSearchUrl(projectName, startAt));
                JSONArray issues = json.getJSONArray(ISSUES_FIELD);
                total = json.getInt(TOTAL_FIELD);

                writeIssues(writer, issues);

                startAt += issues.length();
            } while (startAt < total);
        }
    }

    private static String buildSearchUrl(String projectName, int startAt) {
        return "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                + projectName
                + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR%22status%22=%22resolved%22)"
                + "AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                + startAt
                + "&maxResults="
                + PAGE_SIZE;
    }

    private static void writeIssues(BufferedWriter writer, JSONArray issues)
            throws IOException, JSONException {
        for (int index = 0; index < issues.length(); index++) {
            JSONObject issue = issues.getJSONObject(index);
            writeIssue(writer, issue);
        }
    }

    private static void writeIssue(BufferedWriter writer, JSONObject issue)
            throws IOException, JSONException {
        JSONObject fields = issue.getJSONObject(FIELDS_FIELD);

        String key = issue.getString(KEY_FIELD);
        String created = getOptionalString(fields, CREATED_FIELD);
        String resolutionDate = getOptionalString(fields, RESOLUTION_DATE_FIELD);
        String affectedVersions = extractAffectedVersions(fields);

        writer.write(escapeCsv(key));
        writer.write(",");
        writer.write(escapeCsv(created));
        writer.write(",");
        writer.write(escapeCsv(resolutionDate));
        writer.write(",");
        writer.write(escapeCsv(affectedVersions));
        writer.newLine();
    }

    private static String getOptionalString(JSONObject jsonObject, String fieldName)
            throws JSONException {
        if (jsonObject.has(fieldName) && !jsonObject.isNull(fieldName)) {
            return jsonObject.getString(fieldName);
        }

        return "";
    }

    private static String extractAffectedVersions(JSONObject fields) throws JSONException {
        StringBuilder affectedVersions = new StringBuilder();

        if (fields.has(VERSIONS_FIELD) && !fields.isNull(VERSIONS_FIELD)) {
            JSONArray versions = fields.getJSONArray(VERSIONS_FIELD);

            for (int index = 0; index < versions.length(); index++) {
                appendVersionName(affectedVersions, versions.getJSONObject(index));
            }
        }

        return affectedVersions.toString();
    }

    private static void appendVersionName(StringBuilder affectedVersions, JSONObject versionObject)
            throws JSONException {
        if (versionObject.has(NAME_FIELD)) {
            if (!affectedVersions.isEmpty()) {
                affectedVersions.append(";");
            }

            affectedVersions.append(versionObject.getString(NAME_FIELD));
        }
    }

    public static JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
        try (InputStream inputStream = new URL(url).openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8)
             )) {
            return new JSONArray(readAll(reader));
        }
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

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}