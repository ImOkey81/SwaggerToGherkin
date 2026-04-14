package webant.swaggertogherkin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import webant.swaggertogherkin.dto.ArtifactResponse;
import webant.swaggertogherkin.dto.GenerationStatusResponse;
import webant.swaggertogherkin.dto.JobDetailsResponse;
import webant.swaggertogherkin.model.Job;
import webant.swaggertogherkin.model.JobServiceType;
import webant.swaggertogherkin.model.JobStatus;
import webant.swaggertogherkin.service.ArtifactService;
import webant.swaggertogherkin.service.GherkinGeneratorService;
import webant.swaggertogherkin.service.JobService;
import webant.swaggertogherkin.service.SwaggerTestGeneratorService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GherkinGeneratorController.class)
class GherkinGeneratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GherkinGeneratorService gherkinGeneratorService;

    @MockitoBean
    private SwaggerTestGeneratorService swaggerTestGeneratorService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private ArtifactService artifactService;

    @Test
    void generateTestsReturnsPendingJsonWithGenerationIdWhenFilePathIsMissing() throws Exception {
        when(swaggerTestGeneratorService.generateTestsFromGitHub(any())).thenReturn("abc-123");

        mockMvc.perform(post("/generate-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repoUrl": "https://github.com/ImOkey81/ShadowPinger1/blob/main/openapi.yaml",
                                  "language": "java"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Generation started"))
                .andExpect(jsonPath("$.generationId").value("abc-123"))
                .andExpect(jsonPath("$.downloadPath").value("/generated-tests/abc-123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void generateGherkinReturnsJsonWhenRepoUrlPointsToDirectFile() throws Exception {
        Job job = pendingJob();
        when(jobService.createJob(eq(JobServiceType.swagger_gherkin), eq("Generate gherkin"), any())).thenReturn(job);
        when(gherkinGeneratorService.generateGherkinFromGitHub(any())).thenReturn("Feature: Demo");

        mockMvc.perform(post("/generate-gherkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repoUrl": "https://github.com/example/repo/blob/main/openapi.yaml"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.gherkin").value("Feature: Demo"));
    }

    @Test
    void generateGherkinReturnsBadRequestForRepositoryRootWithoutFilePath() throws Exception {
        Job job = pendingJob();
        when(jobService.createJob(eq(JobServiceType.swagger_gherkin), eq("Generate gherkin"), any())).thenReturn(job);
        when(gherkinGeneratorService.generateGherkinFromGitHub(any()))
                .thenThrow(new IllegalArgumentException("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL"));

        mockMvc.perform(post("/generate-gherkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repoUrl": "https://github.com/example/repo"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL"));
    }

    @Test
    void generateGherkinReturnsBadRequestForMissingRemoteFile() throws Exception {
        Job job = pendingJob();
        when(jobService.createJob(eq(JobServiceType.swagger_gherkin), eq("Generate gherkin"), any())).thenReturn(job);
        when(gherkinGeneratorService.generateGherkinFromGitHub(any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(post("/generate-gherkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "repoUrl": "https://github.com/example/repo/blob/main/openapi.yaml"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("Swagger/OpenAPI file not found"));
    }

    @Test
    void generateGherkinRejectsInvalidJsonBody() throws Exception {
        mockMvc.perform(post("/generate-gherkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("Invalid JSON request body"));
    }

    @Test
    void generateGherkinRejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/generate-gherkin")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("repoUrl=https://github.com/example/repo/blob/main/openapi.yaml"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("Content-Type must be application/json"));
    }

    @Test
    void healthReturnsServiceState() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("swagger"));
    }

    @Test
    void supportedLanguagesReturnsBackendList() throws Exception {
        when(swaggerTestGeneratorService.getSupportedLanguages()).thenReturn(List.of("java", "python"));

        mockMvc.perform(get("/supported-languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[0]").value("java"))
                .andExpect(jsonPath("$.languages[1]").value("python"));
    }

    @Test
    void generationStatusReturnsStatusPayload() throws Exception {
        when(swaggerTestGeneratorService.getGenerationStatus("abc-123"))
                .thenReturn(new GenerationStatusResponse("abc-123", "DONE", "/generated-tests/abc-123", "Tests generated successfully"));

        mockMvc.perform(get("/generated-tests/abc-123/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value("abc-123"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.downloadPath").value("/generated-tests/abc-123"));
    }

    @Test
    void getJobArtifactsReturnsStoredArtifacts() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobService.getJob(jobId)).thenReturn(new JobDetailsResponse(
                jobId,
                "swagger_tests",
                "done",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null
        ));
        when(artifactService.listArtifacts(jobId)).thenReturn(List.of(
                new ArtifactResponse(UUID.randomUUID(), "generated_zip", "generated-tests.zip", "application/zip", 128L, Instant.now())
        ));

        mockMvc.perform(get("/jobs/" + jobId + "/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactType").value("generated_zip"))
                .andExpect(jsonPath("$[0].fileName").value("generated-tests.zip"));
    }

    private Job pendingJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setServiceType(JobServiceType.swagger_gherkin);
        job.setStatus(JobStatus.pending);
        return job;
    }
}
