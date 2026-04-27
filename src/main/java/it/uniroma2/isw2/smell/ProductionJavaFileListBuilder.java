package it.uniroma2.isw2.smell;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ProductionJavaFileListBuilder {

    public List<String> findProductionJavaFiles(Path repositoryPath) throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> normalizePath(path.toAbsolutePath().normalize().toString()))
                    .filter(this::isProductionJavaFile)
                    .sorted()
                    .toList();
        }
    }

    public void writeFileList(Path outputPath, List<String> javaFiles) throws IOException {
        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (String javaFile : javaFiles) {
                writer.write(javaFile);
                writer.newLine();
            }
        }
    }

    private boolean isProductionJavaFile(String path) {
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

    private String normalizePath(String path) {
        return path.replace("\\", "/").trim();
    }
}