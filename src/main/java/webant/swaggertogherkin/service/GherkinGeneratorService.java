package webant.swaggertogherkin.service;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.stereotype.Service;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.util.GitHubContentFetcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class GherkinGeneratorService {

    private static final Pattern PATH_PARAMETER_PATTERN = Pattern.compile("\\{[^/]+}");

    private final GitHubContentFetcher contentFetcher;

    public GherkinGeneratorService(GitHubContentFetcher contentFetcher) {
        this.contentFetcher = contentFetcher;
    }

    public String generateGherkinFromGitHub(GitHubRequest request) throws Exception {
        validateRequest(request);
        String swaggerContent = contentFetcher.fetchContent(request.getRepoUrl(), request.getFilePath());
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(swaggerContent, null, null).getOpenAPI();
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new IllegalArgumentException("OpenAPI specification does not contain any paths");
        }
        return generateGherkinFromOpenAPI(openAPI);
    }

    private void validateRequest(GitHubRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }
    }

    private String generateGherkinFromOpenAPI(OpenAPI openAPI) {
        CrudResource resource = findBestCrudResource(openAPI)
                .orElseThrow(() -> new IllegalArgumentException("CRUD resource was not found in the OpenAPI specification"));

        StringBuilder builder = new StringBuilder();
        builder.append("Feature: CRUD scenarios for ").append(resource.displayName()).append('\n');
        builder.append("  As an API user\n");
        builder.append("  I want to validate create, read, update and delete operations for ")
                .append(resource.displayName()).append('\n');
        builder.append("  So that I can verify expected behaviour and error handling\n\n");

        appendCreateScenario(builder, resource);
        appendReadScenario(builder, resource);
        appendUpdateScenario(builder, resource);
        appendDeleteScenario(builder, resource);

        return builder.toString().trim();
    }

    private Optional<CrudResource> findBestCrudResource(OpenAPI openAPI) {
        Map<String, CrudResource> resources = new LinkedHashMap<>();

        openAPI.getPaths().forEach((path, pathItem) -> {
            if (pathItem == null) {
                return;
            }

            String resourceKey = extractResourceKey(path);
            CrudResource resource = resources.computeIfAbsent(resourceKey, CrudResource::new);

            resource.register(OperationKind.CREATE, path, "POST", pathItem.getPost());
            resource.register(OperationKind.READ, path, "GET", chooseReadOperation(path, pathItem));
            resource.register(OperationKind.UPDATE, path, chooseUpdateMethod(pathItem), chooseUpdateOperation(pathItem));
            resource.register(OperationKind.DELETE, path, "DELETE", pathItem.getDelete());
        });

        return resources.values().stream()
                .filter(CrudResource::hasCrudCoverage)
                .max(Comparator.comparingInt(CrudResource::score));
    }

    private Operation chooseReadOperation(String path, PathItem pathItem) {
        if (pathItem.getGet() == null) {
            return null;
        }
        if (hasPathParameter(path)) {
            return pathItem.getGet();
        }
        return pathItem.getGet();
    }

    private Operation chooseUpdateOperation(PathItem pathItem) {
        if (pathItem.getPut() != null) {
            return pathItem.getPut();
        }
        return pathItem.getPatch();
    }

    private String chooseUpdateMethod(PathItem pathItem) {
        if (pathItem.getPut() != null) {
            return "PUT";
        }
        if (pathItem.getPatch() != null) {
            return "PATCH";
        }
        return null;
    }

    private void appendCreateScenario(StringBuilder builder, CrudResource resource) {
        OperationDetails create = resource.operation(OperationKind.CREATE);
        if (create == null) {
            return;
        }

        builder.append("  Scenario: Create ").append(resource.singularName()).append(" successfully\n");
        builder.append("    Given the API endpoint ").append(create.path()).append(" is available\n");
        appendParameterSteps(builder, create.operation());
        builder.append("    And I prepare a valid ").append(resource.singularName()).append(" payload\n");
        builder.append("    When I send a ").append(create.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(successStatus(create.operation(), 201)).append('\n');
        builder.append("    And the response should contain the created ").append(resource.singularName()).append(" data\n\n");

        builder.append("  Scenario: Reject creating ").append(resource.singularName()).append(" with invalid data\n");
        builder.append("    Given the API endpoint ").append(create.path()).append(" is available\n");
        builder.append("    And I prepare an invalid ").append(resource.singularName()).append(" payload\n");
        builder.append("    When I send a ").append(create.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(clientErrorStatus(create.operation(), 400)).append('\n');
        builder.append("    And the response should describe the validation error\n\n");
    }

    private void appendReadScenario(StringBuilder builder, CrudResource resource) {
        OperationDetails read = resource.operation(OperationKind.READ);
        if (read == null) {
            return;
        }

        builder.append("  Scenario: Read ").append(resource.singularName()).append(" successfully\n");
        builder.append("    Given the API endpoint ").append(read.path()).append(" is available\n");
        appendParameterSteps(builder, read.operation());
        builder.append("    And an existing ").append(resource.singularName()).append(" record is present\n");
        builder.append("    When I send a ").append(read.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(successStatus(read.operation(), 200)).append('\n');
        builder.append("    And the response should contain the requested ").append(resource.singularName()).append(" data\n\n");

        builder.append("  Scenario: Return not found for missing ").append(resource.singularName()).append('\n');
        builder.append("    Given the API endpoint ").append(read.path()).append(" is available\n");
        builder.append("    And I use a non-existing ").append(resource.identifierName()).append('\n');
        builder.append("    When I send a ").append(read.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(notFoundStatus(read.operation(), 404)).append('\n');
        builder.append("    And the response should explain that the ").append(resource.singularName()).append(" was not found\n\n");
    }

    private void appendUpdateScenario(StringBuilder builder, CrudResource resource) {
        OperationDetails update = resource.operation(OperationKind.UPDATE);
        if (update == null) {
            return;
        }

        builder.append("  Scenario: Update ").append(resource.singularName()).append(" successfully\n");
        builder.append("    Given the API endpoint ").append(update.path()).append(" is available\n");
        appendParameterSteps(builder, update.operation());
        builder.append("    And I prepare a valid update payload for ").append(resource.singularName()).append('\n');
        builder.append("    When I send a ").append(update.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(successStatus(update.operation(), 200)).append('\n');
        builder.append("    And the response should contain the updated ").append(resource.singularName()).append(" data\n\n");

        builder.append("  Scenario: Reject updating ").append(resource.singularName()).append(" with invalid value\n");
        builder.append("    Given the API endpoint ").append(update.path()).append(" is available\n");
        appendParameterSteps(builder, update.operation());
        builder.append("    And I prepare an invalid update payload for ").append(resource.singularName()).append('\n');
        builder.append("    When I send a ").append(update.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(clientErrorStatus(update.operation(), 400)).append('\n');
        builder.append("    And the response should describe the invalid field value\n\n");
    }

    private void appendDeleteScenario(StringBuilder builder, CrudResource resource) {
        OperationDetails delete = resource.operation(OperationKind.DELETE);
        if (delete == null) {
            return;
        }

        builder.append("  Scenario: Delete ").append(resource.singularName()).append(" successfully\n");
        builder.append("    Given the API endpoint ").append(delete.path()).append(" is available\n");
        appendParameterSteps(builder, delete.operation());
        builder.append("    And an existing ").append(resource.singularName()).append(" record is present\n");
        builder.append("    When I send a ").append(delete.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(successStatus(delete.operation(), 200)).append('\n');
        builder.append("    And the ").append(resource.singularName()).append(" should no longer be available for reading\n\n");

        builder.append("  Scenario: Return not found when deleting missing ").append(resource.singularName()).append('\n');
        builder.append("    Given the API endpoint ").append(delete.path()).append(" is available\n");
        builder.append("    And I use a non-existing ").append(resource.identifierName()).append('\n');
        builder.append("    When I send a ").append(delete.method()).append(" request\n");
        builder.append("    Then the response status should be ").append(notFoundStatus(delete.operation(), 404)).append('\n');
        builder.append("    And the response should explain that the ").append(resource.singularName()).append(" was not found\n\n");
    }

    private void appendParameterSteps(StringBuilder builder, Operation operation) {
        if (operation == null || operation.getParameters() == null) {
            return;
        }

        for (Parameter parameter : operation.getParameters()) {
            if (parameter == null || parameter.getName() == null) {
                continue;
            }
            builder.append("    And I set ").append(parameter.getName()).append(" to \"")
                    .append(exampleValue(parameter)).append("\"\n");
        }
    }

    private String exampleValue(Parameter parameter) {
        if (parameter.getExample() != null) {
            return parameter.getExample().toString();
        }
        if (parameter.getSchema() == null || parameter.getSchema().getType() == null) {
            return "value";
        }

        return switch (parameter.getSchema().getType()) {
            case "integer", "number" -> "1";
            case "boolean" -> "true";
            default -> "sample-value";
        };
    }

    private int successStatus(Operation operation, int fallback) {
        return firstMatchingStatus(operation, code -> code.startsWith("2"), fallback);
    }

    private int clientErrorStatus(Operation operation, int fallback) {
        return firstMatchingStatus(operation, code -> code.startsWith("4"), fallback);
    }

    private int notFoundStatus(Operation operation, int fallback) {
        if (operation != null && operation.getResponses() != null && operation.getResponses().containsKey("404")) {
            return 404;
        }
        return clientErrorStatus(operation, fallback);
    }

    private int firstMatchingStatus(Operation operation, java.util.function.Predicate<String> matcher, int fallback) {
        if (operation == null || operation.getResponses() == null) {
            return fallback;
        }

        return operation.getResponses().keySet().stream()
                .filter(Objects::nonNull)
                .filter(matcher)
                .map(code -> {
                    try {
                        return Integer.parseInt(code);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    private String extractResourceKey(String path) {
        if (path == null || path.isBlank()) {
            return "resource";
        }

        String sanitized = PATH_PARAMETER_PATTERN.matcher(path).replaceAll("");
        String[] parts = sanitized.split("/");
        for (String part : parts) {
            if (!part.isBlank()) {
                return part.toLowerCase(Locale.ROOT);
            }
        }
        return "resource";
    }

    private boolean hasPathParameter(String path) {
        return path != null && PATH_PARAMETER_PATTERN.matcher(path).find();
    }

    private enum OperationKind {
        CREATE,
        READ,
        UPDATE,
        DELETE
    }

    private record OperationDetails(String path, String method, Operation operation) {
    }

    private static final class CrudResource {
        private final String resourceKey;
        private final Map<OperationKind, List<OperationDetails>> candidates = new LinkedHashMap<>();

        private CrudResource(String resourceKey) {
            this.resourceKey = resourceKey;
        }

        private void register(OperationKind kind, String path, String method, Operation operation) {
            if (operation == null || method == null) {
                return;
            }
            candidates.computeIfAbsent(kind, key -> new ArrayList<>())
                    .add(new OperationDetails(path, method, operation));
        }

        private OperationDetails operation(OperationKind kind) {
            List<OperationDetails> operations = candidates.get(kind);
            if (operations == null || operations.isEmpty()) {
                return null;
            }

            if (kind == OperationKind.READ || kind == OperationKind.UPDATE || kind == OperationKind.DELETE) {
                return operations.stream()
                        .filter(operation -> PATH_PARAMETER_PATTERN.matcher(operation.path()).find())
                        .findFirst()
                        .orElse(operations.getFirst());
            }

            return operations.stream()
                    .filter(operation -> !PATH_PARAMETER_PATTERN.matcher(operation.path()).find())
                    .findFirst()
                    .orElse(operations.getFirst());
        }

        private boolean hasCrudCoverage() {
            return operation(OperationKind.CREATE) != null
                    && operation(OperationKind.READ) != null
                    && operation(OperationKind.UPDATE) != null
                    && operation(OperationKind.DELETE) != null;
        }

        private int score() {
            return (operation(OperationKind.CREATE) != null ? 1 : 0)
                    + (operation(OperationKind.READ) != null ? 1 : 0)
                    + (operation(OperationKind.UPDATE) != null ? 1 : 0)
                    + (operation(OperationKind.DELETE) != null ? 1 : 0);
        }

        private String displayName() {
            return resourceKey.replace('-', ' ');
        }

        private String singularName() {
            if (resourceKey.endsWith("ies") && resourceKey.length() > 3) {
                return resourceKey.substring(0, resourceKey.length() - 3) + "y";
            }
            if (resourceKey.endsWith("s") && resourceKey.length() > 1) {
                return resourceKey.substring(0, resourceKey.length() - 1);
            }
            return resourceKey;
        }

        private String identifierName() {
            OperationDetails details = operation(OperationKind.READ);
            if (details != null && details.operation() != null && details.operation().getParameters() != null) {
                return details.operation().getParameters().stream()
                        .filter(parameter -> "path".equalsIgnoreCase(parameter.getIn()))
                        .map(Parameter::getName)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(singularName() + "Id");
            }
            return singularName() + "Id";
        }
    }
}
