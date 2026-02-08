package webant.swaggertogherkin.service;

import org.springframework.stereotype.Service;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SwaggerTestGeneratorService {

    private final GitHubContentFetcher contentFetcher;

    public SwaggerTestGeneratorService(GitHubContentFetcher contentFetcher) {
        this.contentFetcher = contentFetcher;
    }

    public String generateTestsFromGitHub(GitHubRequest request) throws Exception {
        // 1. Get Swagger content
        String swaggerContent = contentFetcher.fetchContent(request.getRepoUrl(), request.getFilePath());

        // 2. Save to temp file
        Path tempFile = Files.createTempFile("swagger", ".yaml");
        Files.write(tempFile, swaggerContent.getBytes());

        // 3. Generate tests using swagger-codegen
        String outputDir = generateTests(tempFile.toFile(), request.getLanguage());

        // 4. Read generated tests
        return "Tests generated in: " + outputDir;
    }

    public byte[] getGeneratedTestsArchive(String outputDir) throws IOException {
        Path outputPath = Path.of(outputDir).toAbsolutePath().normalize();

        if (!Files.exists(outputPath) || !Files.isDirectory(outputPath)) {
            throw new IllegalArgumentException("Generated tests directory not found: " + outputDir);
        }

        Path tempDir = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!outputPath.startsWith(tempDir)) {
            throw new IllegalArgumentException("Access denied for directory: " + outputDir);
        }

        Path archivePath = Files.createTempFile("swagger-tests", ".zip");

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            zipDirectory(outputPath, outputPath, zipOutputStream);
        }

        byte[] zipContent = Files.readAllBytes(archivePath);
        Files.deleteIfExists(archivePath);
        return zipContent;
    }

    private String generateTests(File swaggerFile, String language) throws Exception {
        String outputDir = Files.createTempDirectory("swagger-tests").toString();

        ProcessBuilder builder = new ProcessBuilder(
                "swagger-codegen", "generate",
                "-i", swaggerFile.getAbsolutePath(),
                "-l", language,
                "-o", outputDir
        );

        Process process = builder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("swagger-codegen failed with exit code: " + exitCode);
        }

        return outputDir;
    }

    private void zipDirectory(Path rootPath, Path currentPath, ZipOutputStream zipOutputStream) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    zipDirectory(rootPath, path, zipOutputStream);
                } else {
                    String entryName = rootPath.relativize(path).toString();
                    zipOutputStream.putNextEntry(new ZipEntry(entryName));

                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                }
            }
        }
    }
}
