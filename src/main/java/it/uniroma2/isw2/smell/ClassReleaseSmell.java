package it.uniroma2.isw2.smell;

public record ClassReleaseSmell(
        String project,
        int releaseId,
        String classPath,
        int nSmells
) {
}