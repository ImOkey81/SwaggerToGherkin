package webant.swaggertogherkin.service;

// GherkinGeneratorService.java

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springframework.stereotype.Service;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.util.GitHubContentFetcher;


@Service
public class GherkinGeneratorService {

    private final GitHubContentFetcher contentFetcher;

    public GherkinGeneratorService(GitHubContentFetcher contentFetcher) {
        this.contentFetcher = contentFetcher;
    }

    public String generateGherkinFromGitHub(GitHubRequest request) throws Exception {
        String swaggerContent = contentFetcher.fetchContent(request.getRepoUrl(), request.getFilePath());
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(swaggerContent, null, null).getOpenAPI();
        return generateGherkinFromOpenAPI(openAPI);
    }

    private String generateGherkinFromOpenAPI(OpenAPI openAPI) {
        StringBuilder gherkinBuilder = new StringBuilder();

        gherkinBuilder.append("# Generated Gherkin tests from Swagger/OpenAPI\n\n");

        openAPI.getPaths().forEach((path, pathItem) -> {
            processOperations(gherkinBuilder, path, "GET", pathItem.getGet());
            processOperations(gherkinBuilder, path, "POST", pathItem.getPost());
            processOperations(gherkinBuilder, path, "PUT", pathItem.getPut());
            processOperations(gherkinBuilder, path, "DELETE", pathItem.getDelete());
            processOperations(gherkinBuilder, path, "PATCH", pathItem.getPatch());
        });

        return gherkinBuilder.toString();
    }

    private void processOperations(StringBuilder builder, String path, String method, Operation operation) {
        if (operation == null) return;

        String testName = operation.getSummary() != null ? operation.getSummary() : method + " " + path;

        builder.append("Feature: ").append(testName).append("\n");
        builder.append("  As an API user\n");
        builder.append("  I want to execute ").append(method).append(" ").append(path).append("\n");
        builder.append("  So that I can verify the API response\n\n");

        builder.append("  Scenario: Successful ").append(method).append(" request to ").append(path).append("\n");
        builder.append("    Given I have a valid API endpoint ").append(path).append("\n");

        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                builder.append("    And I set ").append(param.getName()).append(" to \"")
                        .append(getExampleValue(param)).append("\"\n");
            }
        }

        builder.append("    When I send a ").append(method).append(" request\n");
        builder.append("    Then the response status should be 200\n");

        if (operation.getResponses() != null && operation.getResponses().get("200") != null) {
            builder.append("    And the response should contain expected data\n");
        }

        builder.append("\n");
    }

    private String getExampleValue(Parameter param) {
        if (param.getExample() != null) return param.getExample().toString();
        if (param.getSchema() == null) return "value";

        switch (param.getSchema().getType()) {
            case "string": return "sample_value";
            case "integer": return "123";
            case "boolean": return "true";
            default: return "value";
        }
    }
}