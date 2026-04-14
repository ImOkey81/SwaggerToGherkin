package webant.swaggertogherkin.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import webant.swaggertogherkin.dto.ArtifactResponse;
import webant.swaggertogherkin.dto.GenerationStatusResponse;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.dto.GherkinResponse;
import webant.swaggertogherkin.dto.HealthResponse;
import webant.swaggertogherkin.dto.JobDetailsResponse;
import webant.swaggertogherkin.dto.JobListResponse;
import webant.swaggertogherkin.dto.JobResultResponse;
import webant.swaggertogherkin.dto.SupportedLanguagesResponse;
import webant.swaggertogherkin.dto.TestGenerationResponse;
import webant.swaggertogherkin.model.Artifact;
import webant.swaggertogherkin.model.Job;
import webant.swaggertogherkin.model.JobServiceType;
import webant.swaggertogherkin.model.JobStatus;
import webant.swaggertogherkin.service.ArtifactService;
import webant.swaggertogherkin.service.GherkinGeneratorService;
import webant.swaggertogherkin.service.JobService;
import webant.swaggertogherkin.service.SwaggerTestGeneratorService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class GherkinGeneratorController {

    private final GherkinGeneratorService gherkinService;
    private final SwaggerTestGeneratorService testGeneratorService;
    private final JobService jobService;
    private final ArtifactService artifactService;

    public GherkinGeneratorController(GherkinGeneratorService gherkinService,
                                      SwaggerTestGeneratorService testGeneratorService,
                                      JobService jobService,
                                      ArtifactService artifactService) {
        this.gherkinService = gherkinService;
        this.testGeneratorService = testGeneratorService;
        this.jobService = jobService;
        this.artifactService = artifactService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "swagger");
    }

    @GetMapping("/supported-languages")
    public SupportedLanguagesResponse supportedLanguages() {
        return new SupportedLanguagesResponse(testGeneratorService.getSupportedLanguages());
    }

    @PostMapping(value = "/generate-gherkin", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public GherkinResponse generateGherkin(@RequestBody GitHubRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repoUrl", request == null ? null : request.getRepoUrl());
        payload.put("filePath", request == null ? null : request.getFilePath());
        Job job = jobService.createJob(
                JobServiceType.swagger_gherkin,
                "Generate gherkin",
                payload
        );
        try {
            jobService.markProcessing(job.getId());
            String gherkin = gherkinService.generateGherkinFromGitHub(request);
            jobService.saveGherkinResult(job.getId(), gherkin);
            jobService.markDone(job.getId());
            return new GherkinResponse("success", gherkin);
        } catch (Exception exception) {
            jobService.markFailed(job.getId(), exception.getMessage());
            throw exception;
        }
    }

    @PostMapping(value = "/generate-tests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TestGenerationResponse generateTests(@RequestBody GitHubRequest request) {
        String generationId = testGeneratorService.generateTestsFromGitHub(request);
        return new TestGenerationResponse(
                "Generation started",
                generationId,
                "/generated-tests/" + generationId,
                "PENDING"
        );
    }

    @GetMapping("/generated-tests/{generationId}/status")
    public GenerationStatusResponse getGenerationStatus(@PathVariable String generationId) {
        return testGeneratorService.getGenerationStatus(generationId);
    }

    @GetMapping("/generated-tests/{generationId}")
    public ResponseEntity<byte[]> downloadGeneratedTests(@PathVariable String generationId) throws Exception {
        byte[] archive = testGeneratorService.getGeneratedTestsArchiveById(generationId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("generated-tests.zip").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(archive);
    }

    @GetMapping("/generated-tests/{generationId}/files")
    public List<String> listGeneratedFiles(@PathVariable String generationId) throws Exception {
        return testGeneratorService.getGeneratedFilesById(generationId);
    }

    @GetMapping("/generated-tests/{generationId}/file")
    public ResponseEntity<byte[]> getGeneratedFile(
            @PathVariable String generationId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean download
    ) throws Exception {
        byte[] content = testGeneratorService.getGeneratedFileContentById(generationId, path);

        HttpHeaders headers = new HttpHeaders();
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (download) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        } else {
            headers.setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    @GetMapping("/jobs/{jobId}")
    public JobDetailsResponse getJob(@PathVariable UUID jobId) {
        return jobService.getJob(jobId);
    }

    @GetMapping("/jobs/{jobId}/result")
    public JobResultResponse getJobResult(@PathVariable UUID jobId) {
        return jobService.getJobResult(jobId);
    }

    @GetMapping("/jobs/{jobId}/artifacts")
    public List<ArtifactResponse> getJobArtifacts(@PathVariable UUID jobId) {
        jobService.getJob(jobId);
        return artifactService.listArtifacts(jobId);
    }

    @GetMapping("/jobs/{jobId}/artifacts/{artifactId}")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable UUID jobId, @PathVariable UUID artifactId) throws Exception {
        jobService.getJob(jobId);
        Artifact artifact = artifactService.getArtifact(jobId, artifactId);
        Path artifactPath = Path.of(artifact.getFilePath());
        byte[] content = Files.readAllBytes(artifactPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                artifact.getMimeType() == null || artifact.getMimeType().isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : artifact.getMimeType()
        ));
        headers.setContentDisposition(ContentDisposition.attachment().filename(artifact.getFileName()).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    @GetMapping("/jobs")
    public JobListResponse listJobs(
            @RequestParam(required = false) JobServiceType serviceType,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return jobService.listJobs(serviceType, status, limit, offset);
    }

}
