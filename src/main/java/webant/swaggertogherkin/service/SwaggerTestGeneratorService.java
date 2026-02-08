package webant.swaggertogherkin.service;

import org.springframework.stereotype.Service;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

        List<List<String>> commandCandidates = buildCommandCandidates(swaggerFile, language, outputDir);
        List<String> startErrors = new ArrayList<>();

        for (List<String> command : commandCandidates) {
            try {
                Process process = new ProcessBuilder(command).start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return outputDir;
                }

                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("swagger-codegen failed with exit code " + exitCode + ". Stderr: " + stderr);
            } catch (IOException startError) {
                startErrors.add(String.join(" ", command) + " -> " + startError.getMessage());
            }
        }

        throw new RuntimeException(
                "No swagger codegen executable found. Tried: " + String.join(" | ", startErrors)
                        + ". If you run with Docker, publish the port: docker run -p 8082:8082 swagger_to_gherkin"
        );
    }

    private List<List<String>> buildCommandCandidates(File swaggerFile, String language, String outputDir) {
        List<String> generateArgs = List.of(
                "generate",
                "-i", swaggerFile.getAbsolutePath(),
                "-l", language,
                "-o", outputDir
        );

        List<List<String>> candidates = new ArrayList<>();
        candidates.add(buildCommand("swagger-codegen", generateArgs));
        candidates.add(buildCommand("swagger-codegen-cli", generateArgs));

        String jarPath = System.getenv("SWAGGER_CODEGEN_CLI_JAR");
        if (jarPath != null && !jarPath.isBlank()) {
            List<String> jarCommand = new ArrayList<>();
            jarCommand.add("java");
            jarCommand.add("-jar");
            jarCommand.add(jarPath);
            jarCommand.addAll(generateArgs);
            candidates.add(jarCommand);
        }

        return candidates;
    }

    private List<String> buildCommand(String executable, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);
        return command;
    }
}
