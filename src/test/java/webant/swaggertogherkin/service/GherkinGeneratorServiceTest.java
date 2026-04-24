package webant.swaggertogherkin.service;

import org.junit.jupiter.api.Test;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GherkinGeneratorServiceTest {

    private final GitHubContentFetcher contentFetcher = mock(GitHubContentFetcher.class);
    private final GherkinGeneratorService service = new GherkinGeneratorService(contentFetcher);

    @Test
    void generatesAvailableOperationScenariosForSingleResourceWithNegativeCases() throws Exception {
        when(contentFetcher.fetchContent("https://github.com/example/repo", "openapi.yaml"))
                .thenReturn("""
                        openapi: 3.0.3
                        info:
                          title: User API
                          version: 1.0.0
                        paths:
                          /users:
                            post:
                              summary: Create user
                              responses:
                                '201':
                                  description: Created
                                '400':
                                  description: Invalid payload
                            get:
                              summary: List users
                              responses:
                                '200':
                                  description: OK
                          /users/{userId}:
                            get:
                              summary: Get user
                              parameters:
                                - name: userId
                                  in: path
                                  required: true
                                  schema:
                                    type: integer
                              responses:
                                '200':
                                  description: OK
                                '404':
                                  description: Not found
                            put:
                              summary: Update user
                              parameters:
                                - name: userId
                                  in: path
                                  required: true
                                  schema:
                                    type: integer
                              responses:
                                '200':
                                  description: Updated
                                '400':
                                  description: Invalid payload
                            delete:
                              summary: Delete user
                              parameters:
                                - name: userId
                                  in: path
                                  required: true
                                  schema:
                                    type: integer
                              responses:
                                '204':
                                  description: Deleted
                                '404':
                                  description: Not found
                        """);

        GitHubRequest request = new GitHubRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setFilePath("openapi.yaml");

        String gherkin = service.generateGherkinFromGitHub(request);

        assertThat(gherkin).contains("Feature: API scenarios for users");
        assertThat(gherkin).contains("Scenario: Create user successfully");
        assertThat(gherkin).contains("Scenario: Reject creating user with invalid data");
        assertThat(gherkin).contains("Scenario: Read user successfully");
        assertThat(gherkin).contains("Scenario: Return not found for missing user");
        assertThat(gherkin).contains("Scenario: Update user successfully");
        assertThat(gherkin).contains("Scenario: Reject updating user with invalid value");
        assertThat(gherkin).contains("Scenario: Delete user successfully");
        assertThat(gherkin).contains("Scenario: Return not found when deleting missing user");
        assertThat(gherkin).contains("Then the response status should be 201");
        assertThat(gherkin).contains("Then the response status should be 404");
        assertThat(gherkin).contains("Then the response status should be 204");
        assertThat(gherkin).contains("And I set userId to \"1\"");
    }

    @Test
    void generatesReadOnlyScenariosWhenCrudResourceIsAbsent() throws Exception {
        when(contentFetcher.fetchContent("https://github.com/example/repo", "openapi.yaml"))
                .thenReturn("""
                        openapi: 3.0.3
                        info:
                          title: Ping API
                          version: 1.0.0
                        paths:
                          /ping:
                            get:
                              summary: Ping endpoint
                              responses:
                                '200':
                                  description: OK
                        """);

        GitHubRequest request = new GitHubRequest();
        request.setRepoUrl("https://github.com/example/repo");
        request.setFilePath("openapi.yaml");

        String gherkin = service.generateGherkinFromGitHub(request);

        assertThat(gherkin).contains("Feature: API scenarios for ping");
        assertThat(gherkin).contains("Scenario: Read ping successfully");
        assertThat(gherkin).doesNotContain("Scenario: Create");
        assertThat(gherkin).doesNotContain("Scenario: Update");
        assertThat(gherkin).doesNotContain("Scenario: Delete");
    }
}
