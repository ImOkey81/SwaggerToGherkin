package webant.swaggertogherkin.service;

import org.springframework.stereotype.Service;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

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
}