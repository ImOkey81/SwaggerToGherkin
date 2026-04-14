package webant.swaggertogherkin.service;

import io.swagger.codegen.v3.DefaultGenerator;
import io.swagger.codegen.v3.config.CodegenConfigurator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.dto.GenerationStatusResponse;
import webant.swaggertogherkin.exception.ApiException;
import webant.swaggertogherkin.model.Artifact;
import webant.swaggertogherkin.model.ArtifactType;
import webant.swaggertogherkin.model.Job;
import webant.swaggertogherkin.model.JobServiceType;
import webant.swaggertogherkin.model.JobStatus;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SwaggerTestGeneratorService {

    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "java",
            "kotlin",
            "python",
            "csharp",
            "go",
            "php",
            "ruby",
            "typescript-fetch"
    );

    private final GitHubContentFetcher contentFetcher;
    private final JobService jobService;
    private final ArtifactService artifactService;
    private final Path storageRoot;

    public SwaggerTestGeneratorService(
            GitHubContentFetcher contentFetcher,
            JobService jobService,
            ArtifactService artifactService,
            @Value("${app.storage.root:temp/storage}") String storageRoot
    ) {
        this.contentFetcher = contentFetcher;
        this.jobService = jobService;
        this.artifactService = artifactService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public String generateTestsFromGitHub(GitHubRequest request) {
        validateRequest(request, true);
        String language = normalizeLanguage(request.getLanguage());
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new ApiException(
                    "UNSUPPORTED_LANGUAGE",
                    "Unsupported language: " + request.getLanguage(),
                    HttpStatus.BAD_REQUEST
            );
        }

        Job job = jobService.createJob(
                JobServiceType.swagger_tests,
                "Generate tests",
                Map.of(
                        "repoUrl", request.getRepoUrl(),
                        "filePath", request.getFilePath(),
                        "language", language
                )
        );
        Thread.ofVirtual().start(() -> runGeneration(job.getId(), request, language));
        return job.getId().toString();
    }

    public byte[] getGeneratedTestsArchiveById(String generationId) throws IOException {
        UUID jobId = parseJobId(generationId);
        ensureReadyJob(jobId);
        Artifact archive = artifactService.getArchiveArtifact(jobId);
        return Files.readAllBytes(Path.of(archive.getFilePath()));
    }

    public List<String> getGeneratedFilesById(String generationId) throws IOException {
        UUID jobId = parseJobId(generationId);
        ensureReadyJob(jobId);
        return artifactService.listArtifacts(jobId).stream()
                .filter(artifact -> ArtifactType.generated_file.name().equals(artifact.artifactType()))
                .map(artifact -> artifact.fileName())
                .sorted()
                .toList();
    }

    public byte[] getGeneratedFileContentById(String generationId, String filePath) throws IOException {
        UUID jobId = parseJobId(generationId);
        ensureReadyJob(jobId);
        Path resolvedFilePath = Path.of(artifactService.getGeneratedFileArtifact(jobId, filePath).getFilePath());
        if (!Files.exists(resolvedFilePath) || !Files.isRegularFile(resolvedFilePath)) {
            throw new ApiException("FILE_NOT_FOUND", "Generated file not found: " + filePath, HttpStatus.NOT_FOUND);
        }
        return Files.readAllBytes(resolvedFilePath);
    }

    public GenerationStatusResponse getGenerationStatus(String generationId) {
        Job job = jobService.getJobEntity(parseJobId(generationId));
        String status = switch (job.getStatus()) {
            case pending -> "PENDING";
            case processing -> "PROCESSING";
            case done -> "DONE";
            case failed -> "ERROR";
        };
        return new GenerationStatusResponse(
                generationId,
                status,
                job.getStatus() == JobStatus.done ? "/generated-tests/" + generationId : null,
                job.getErrorMessage() == null ? defaultStatusMessage(job.getStatus()) : job.getErrorMessage()
        );
    }

    public List<String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    private void runGeneration(UUID jobId, GitHubRequest request, String language) {
        Path tempFile = null;
        Path outputPath = null;
        try {
            jobService.markProcessing(jobId);
            String swaggerContent = contentFetcher.fetchContent(request.getRepoUrl(), request.getFilePath());
            tempFile = Files.createTempFile("swagger-", ".yaml");
            Files.writeString(tempFile, swaggerContent);
            outputPath = generateTests(tempFile.toFile(), language, jobId);
            Path archivePath = createArchive(outputPath, jobId);
            registerArtifacts(jobId, outputPath, archivePath);
            jobService.saveTestResult(jobId, Map.of(
                    "generationId", jobId.toString(),
                    "downloadPath", "/generated-tests/" + jobId
            ));
            jobService.markDone(jobId);
        } catch (Exception exception) {
            jobService.markFailed(jobId, exception.getMessage());
            deleteRecursively(outputPath);
        } finally {
            deleteRecursively(tempFile);
        }
    }

    private Job ensureReadyJob(UUID jobId) {
        Job job = jobService.getJobEntity(jobId);
        if (job.getStatus() == JobStatus.pending || job.getStatus() == JobStatus.processing) {
            throw new ApiException("GENERATION_NOT_READY", "Generation is still in progress", HttpStatus.CONFLICT);
        }
        if (job.getStatus() == JobStatus.failed) {
            throw new ApiException("GENERATION_FAILED", job.getErrorMessage(), HttpStatus.BAD_REQUEST);
        }
        return job;
    }

    private void validateRequest(GitHubRequest request, boolean languageRequired) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }
        if (languageRequired && (request.getLanguage() == null || request.getLanguage().isBlank())) {
            throw new IllegalArgumentException("language is required");
        }
    }

    private String normalizeLanguage(String language) {
        return language == null ? null : language.trim().toLowerCase();
    }

    private Path generateTests(File swaggerFile, String language, UUID jobId) throws Exception {
        Path outputDir = storageRoot.resolve("jobs").resolve(jobId.toString()).resolve("generated-files");
        Files.createDirectories(outputDir);
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setInputSpec(swaggerFile.getAbsolutePath())
                .setLang(language)
                .setOutputDir(outputDir.toString());

        new DefaultGenerator()
                .opts(configurator.toClientOptInput())
                .generate();

        return outputDir;
    }

    private Path createArchive(Path outputDir, UUID jobId) throws IOException {
        Path archivePath = storageRoot.resolve("jobs").resolve(jobId.toString()).resolve("generated-tests.zip");
        Files.createDirectories(archivePath.getParent());
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            zipDirectory(outputDir, outputDir, zipOutputStream);
        }
        return archivePath;
    }

    private void registerArtifacts(UUID jobId, Path outputDir, Path archivePath) throws IOException {
        artifactService.saveArtifact(jobId, ArtifactType.generated_zip, archivePath.getFileName().toString(), archivePath, "application/zip");
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> artifactService.saveArtifact(
                            jobId,
                            ArtifactType.generated_file,
                            outputDir.relativize(path).toString().replace('\\', '/'),
                            path,
                            detectMimeType(path)
                    ));
        }
    }

    private String detectMimeType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException ignored) {
            return "application/octet-stream";
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void zipDirectory(Path rootPath, Path currentPath, ZipOutputStream zipOutputStream) throws IOException {
        try (Stream<Path> paths = Files.walk(currentPath)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String entryName = rootPath.relativize(path).toString().replace('\\', '/');
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zipOutputStream);
                zipOutputStream.closeEntry();
            }
        }
    }

    private UUID parseJobId(String generationId) {
        try {
            return UUID.fromString(generationId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException("GENERATION_NOT_FOUND", "Generation id not found: " + generationId, HttpStatus.NOT_FOUND);
        }
    }

    private String defaultStatusMessage(JobStatus status) {
        return switch (status) {
            case pending -> "Generation queued";
            case processing -> "Generation is in progress";
            case done -> "Tests generated successfully";
            case failed -> "Generation failed";
        };
    }
}
