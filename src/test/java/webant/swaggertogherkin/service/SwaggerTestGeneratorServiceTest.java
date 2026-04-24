package webant.swaggertogherkin.service;

import org.junit.jupiter.api.Test;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.model.Job;
import webant.swaggertogherkin.model.JobServiceType;
import webant.swaggertogherkin.model.JobStatus;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwaggerTestGeneratorServiceTest {

    private final GitHubContentFetcher contentFetcher = mock(GitHubContentFetcher.class);
    private final JobService jobService = mock(JobService.class);
    private final ArtifactService artifactService = mock(ArtifactService.class);
    private final SwaggerTestGeneratorService service = new SwaggerTestGeneratorService(
            contentFetcher,
            jobService,
            artifactService,
            Path.of("temp/storage").toString()
    );

    @Test
    void generateTestsAllowsMissingFilePathForDirectFileUrl() {
        GitHubRequest request = new GitHubRequest();
        request.setRepoUrl("https://github.com/example/repo/blob/main/openapi.yaml");
        request.setLanguage("java");

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setServiceType(JobServiceType.swagger_tests);
        job.setStatus(JobStatus.pending);

        when(contentFetcher.fetchContent(request.getRepoUrl(), null)).thenReturn("openapi: 3.0.3");
        when(jobService.createJob(eq(JobServiceType.swagger_tests), eq("Generate tests"), argThat(payload -> true))).thenReturn(job);

        String generationId = service.generateTestsFromGitHub(request);

        assertThat(generationId).isEqualTo(job.getId().toString());
        verify(jobService).createJob(
                eq(JobServiceType.swagger_tests),
                eq("Generate tests"),
                argThat(payload ->
                        request.getRepoUrl().equals(payload.get("repoUrl"))
                                && payload.containsKey("filePath")
                                && payload.get("filePath") == null
                                && "java".equals(payload.get("language"))
                )
        );
    }

    @Test
    void generateTestsRejectsRepositoryRootWithoutFilePath() {
        GitHubRequest request = new GitHubRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setLanguage("java");

        when(contentFetcher.fetchContent(request.getRepoUrl(), null))
                .thenThrow(new IllegalArgumentException("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL"));

        assertThatThrownBy(() -> service.generateTestsFromGitHub(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL");
    }

    @Test
    void generateTestsPropagatesRemoteFileNotFoundBeforeStartingJob() {
        GitHubRequest request = new GitHubRequest();
        request.setRepoUrl("https://github.com/example/repo/blob/main/openapi.yaml");
        request.setLanguage("java");

        doThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Not Found",
                null,
                null,
                null
        ))
                .when(contentFetcher)
                .fetchContent(request.getRepoUrl(), null);

        assertThatThrownBy(() -> service.generateTestsFromGitHub(request))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.NotFound.class);
    }

}
