package webant.swaggertogherkin.service;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class OpenApiTestFileGenerator {

    Map<String, String> generate(OpenAPI openAPI, String language) {
        List<ApiOperation> operations = collectOperations(openAPI);
        String baseUrl = defaultBaseUrl(openAPI);

        return switch (language) {
            case "java" -> Map.of("src/test/java/generated/GeneratedApiContractTest.java", generateJavaTest(baseUrl, operations));
            case "kotlin" -> Map.of("src/test/kotlin/generated/GeneratedApiContractTest.kt", generateKotlinTest(baseUrl, operations));
            case "python" -> Map.of("tests/test_api_contract.py", generatePythonTest(baseUrl, operations));
            case "csharp" -> Map.of("tests/GeneratedApiContractTests.cs", generateCsharpTest(baseUrl, operations));
            case "go" -> Map.of("tests/api_contract_test.go", generateGoTest(baseUrl, operations));
            case "php" -> Map.of("tests/GeneratedApiContractTest.php", generatePhpTest(baseUrl, operations));
            case "ruby" -> Map.of("test/api_contract_test.rb", generateRubyTest(baseUrl, operations));
            case "typescript-fetch" -> Map.of("tests/api-contract.test.ts", generateTypescriptTest(baseUrl, operations));
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    static List<ApiOperation> collectOperations(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new IllegalArgumentException("OpenAPI specification does not contain any paths");
        }

        List<ApiOperation> operations = new ArrayList<>();
        Map<String, Integer> seenNames = new LinkedHashMap<>();

        openAPI.getPaths().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(pathEntry -> {
                    PathItem pathItem = pathEntry.getValue();
                    if (pathItem == null || pathItem.readOperationsMap() == null) {
                        return;
                    }

                    pathItem.readOperationsMap().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                            .forEach(operationEntry -> operations.add(buildOperation(
                                    pathEntry.getKey(),
                                    pathItem,
                                    operationEntry.getKey(),
                                    operationEntry.getValue(),
                                    seenNames
                            )));
                });

        if (operations.isEmpty()) {
            throw new IllegalArgumentException("OpenAPI specification does not contain any operations");
        }

        return operations;
    }

    private static ApiOperation buildOperation(
            String path,
            PathItem pathItem,
            PathItem.HttpMethod httpMethod,
            Operation operation,
            Map<String, Integer> seenNames
    ) {
        List<Parameter> parameters = mergeParameters(pathItem.getParameters(), operation == null ? null : operation.getParameters());
        String pathWithExamples = applyPathParameters(path, parameters);
        String pathWithQuery = appendQueryParameters(pathWithExamples, parameters);
        String baseName = sanitizeIdentifier(operationName(operation, httpMethod, path));
        int sequence = seenNames.merge(baseName, 1, Integer::sum);
        String uniqueName = sequence == 1 ? baseName : baseName + sequence;

        RequestBody requestBody = operation == null ? null : operation.getRequestBody();
        String requestContentType = detectRequestContentType(requestBody);

        return new ApiOperation(
                uniqueName,
                operation == null ? null : operation.getSummary(),
                httpMethod.name(),
                pathWithQuery,
                firstSuccessStatus(operation, 200),
                requestContentType,
                requestBody != null
        );
    }

    private static List<Parameter> mergeParameters(List<Parameter> pathParameters, List<Parameter> operationParameters) {
        List<Parameter> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addParameters(merged, seen, pathParameters);
        addParameters(merged, seen, operationParameters);
        return merged;
    }

    private static void addParameters(List<Parameter> merged, Set<String> seen, List<Parameter> parameters) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            if (parameter == null || parameter.getName() == null) {
                continue;
            }
            String key = parameter.getIn() + ":" + parameter.getName();
            if (seen.add(key)) {
                merged.add(parameter);
            }
        }
    }

    private static String applyPathParameters(String path, List<Parameter> parameters) {
        String resolvedPath = path;
        for (Parameter parameter : parameters) {
            if (!"path".equalsIgnoreCase(parameter.getIn()) || parameter.getName() == null) {
                continue;
            }
            resolvedPath = resolvedPath.replace("{" + parameter.getName() + "}", exampleValue(parameter));
        }
        return resolvedPath;
    }

    private static String appendQueryParameters(String path, List<Parameter> parameters) {
        List<String> queryParts = new ArrayList<>();
        for (Parameter parameter : parameters) {
            if (!"query".equalsIgnoreCase(parameter.getIn()) || parameter.getName() == null) {
                continue;
            }
            queryParts.add(parameter.getName() + "=" + exampleValue(parameter));
        }
        if (queryParts.isEmpty()) {
            return path;
        }
        return path + "?" + String.join("&", queryParts);
    }

    private static String exampleValue(Parameter parameter) {
        if (parameter.getExample() != null) {
            return parameter.getExample().toString();
        }
        if (parameter.getSchema() == null || parameter.getSchema().getType() == null) {
            return "sample";
        }
        return switch (parameter.getSchema().getType()) {
            case "integer", "number" -> "1";
            case "boolean" -> "true";
            default -> "sample";
        };
    }

    private static int firstSuccessStatus(Operation operation, int fallback) {
        if (operation == null || operation.getResponses() == null || operation.getResponses().isEmpty()) {
            return fallback;
        }
        return operation.getResponses().keySet().stream()
                .filter(Objects::nonNull)
                .filter(code -> code.startsWith("2"))
                .map(code -> {
                    try {
                        return Integer.parseInt(code);
                    } catch (NumberFormatException exception) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    private static String detectRequestContentType(RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            return null;
        }

        Content content = requestBody.getContent();
        if (content.containsKey("application/json")) {
            return "application/json";
        }
        for (Map.Entry<String, MediaType> entry : content.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String operationName(Operation operation, PathItem.HttpMethod method, String path) {
        if (operation != null && operation.getOperationId() != null && !operation.getOperationId().isBlank()) {
            return operation.getOperationId();
        }
        return method.name().toLowerCase(Locale.ROOT) + "_" + path.replace('/', '_').replace('{', '_').replace('}', '_');
    }

    private static String sanitizeIdentifier(String rawValue) {
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = false;
        for (char current : rawValue.toCharArray()) {
            if (Character.isLetterOrDigit(current)) {
                if (builder.isEmpty()) {
                    builder.append(Character.toLowerCase(current));
                } else if (capitalizeNext) {
                    builder.append(Character.toUpperCase(current));
                    capitalizeNext = false;
                } else {
                    builder.append(current);
                }
            } else {
                capitalizeNext = true;
            }
        }
        if (builder.isEmpty()) {
            return "generatedOperation";
        }
        if (Character.isDigit(builder.charAt(0))) {
            builder.insert(0, 'o');
        }
        return builder.toString();
    }

    private static String defaultBaseUrl(OpenAPI openAPI) {
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty() && openAPI.getServers().getFirst().getUrl() != null) {
            return openAPI.getServers().getFirst().getUrl();
        }
        return "http://localhost:8080";
    }

    private String generateJavaTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("package generated;\n\n");
        builder.append("import org.junit.jupiter.api.Test;\n\n");
        builder.append("import java.net.URI;\n");
        builder.append("import java.net.http.HttpClient;\n");
        builder.append("import java.net.http.HttpRequest;\n");
        builder.append("import java.net.http.HttpResponse;\n\n");
        builder.append("import static org.junit.jupiter.api.Assertions.assertEquals;\n\n");
        builder.append("class GeneratedApiContractTest {\n\n");
        builder.append("    private static final String BASE_URL = System.getenv().getOrDefault(\"API_BASE_URL\", \"").append(javaString(baseUrl)).append("\");\n");
        builder.append("    private final HttpClient client = HttpClient.newHttpClient();\n\n");
        for (ApiOperation operation : operations) {
            builder.append("    @Test\n");
            builder.append("    void ").append(operation.name()).append("() throws Exception {\n");
            appendJavaOperation(builder, operation);
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private void appendJavaOperation(StringBuilder builder, ApiOperation operation) {
        builder.append("        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(BASE_URL + \"")
                .append(javaString(operation.path())).append("\"))\n");
        builder.append("                .header(\"Accept\", \"application/json\");\n");
        if (operation.hasRequestBody()) {
            builder.append("        String requestBody = \"{}\"; // TODO replace with a valid request payload and auth headers if required\n");
            if (operation.requestContentType() != null) {
                builder.append("        requestBuilder.header(\"Content-Type\", \"").append(javaString(operation.requestContentType())).append("\");\n");
            }
        }
        builder.append("        HttpRequest request = requestBuilder.")
                .append(javaHttpBuilder(operation.method()))
                .append(";\n");
        builder.append("        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());\n");
        builder.append("        assertEquals(").append(operation.successStatus()).append(", response.statusCode());\n");
    }

    private String generateKotlinTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("package generated\n\n");
        builder.append("import org.junit.jupiter.api.Test\n");
        builder.append("import java.net.URI\n");
        builder.append("import java.net.http.HttpClient\n");
        builder.append("import java.net.http.HttpRequest\n");
        builder.append("import java.net.http.HttpResponse\n");
        builder.append("import kotlin.test.assertEquals\n\n");
        builder.append("class GeneratedApiContractTest {\n");
        builder.append("    private val client = HttpClient.newHttpClient()\n");
        builder.append("    private val baseUrl = System.getenv(\"API_BASE_URL\") ?: \"").append(kotlinString(baseUrl)).append("\"\n\n");
        for (ApiOperation operation : operations) {
            builder.append("    @Test\n");
            builder.append("    fun ").append(operation.name()).append("() {\n");
            appendKotlinOperation(builder, operation);
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private void appendKotlinOperation(StringBuilder builder, ApiOperation operation) {
        builder.append("        val requestBuilder = HttpRequest.newBuilder(URI.create(baseUrl + \"")
                .append(kotlinString(operation.path())).append("\"))\n");
        builder.append("            .header(\"Accept\", \"application/json\")\n");
        if (operation.hasRequestBody()) {
            builder.append("        val requestBody = \"{}\" // TODO replace with a valid request payload and auth headers if required\n");
            if (operation.requestContentType() != null) {
                builder.append("        requestBuilder.header(\"Content-Type\", \"").append(kotlinString(operation.requestContentType())).append("\")\n");
            }
        }
        builder.append("        val request = requestBuilder.")
                .append(kotlinRequestBuilder(operation.method()))
                .append("\n");
        builder.append("        val response = client.send(request, HttpResponse.BodyHandlers.ofString())\n");
        builder.append("        assertEquals(").append(operation.successStatus()).append(", response.statusCode())\n");
    }

    private String generatePythonTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("import json\n");
        builder.append("import os\n");
        builder.append("import unittest\n");
        builder.append("import urllib.request\n\n\n");
        builder.append("class ApiContractTest(unittest.TestCase):\n");
        builder.append("    base_url = os.getenv(\"API_BASE_URL\", \"").append(pythonString(baseUrl)).append("\")\n\n");
        for (ApiOperation operation : operations) {
            builder.append("    def test_").append(operation.name()).append("(self):\n");
            appendPythonOperation(builder, operation);
            builder.append("\n");
        }
        builder.append("if __name__ == \"__main__\":\n");
        builder.append("    unittest.main()\n");
        return builder.toString();
    }

    private void appendPythonOperation(StringBuilder builder, ApiOperation operation) {
        if (operation.hasRequestBody()) {
            builder.append("        body = json.dumps({}).encode(\"utf-8\")  # TODO replace with a valid request payload and auth headers if required\n");
        }
        builder.append("        request = urllib.request.Request(\n");
        builder.append("            self.base_url + \"").append(pythonString(operation.path())).append("\",\n");
        if (operation.hasRequestBody()) {
            builder.append("            data=body,\n");
        }
        builder.append("            method=\"").append(operation.method()).append("\",\n");
        builder.append("            headers={\"Accept\": \"application/json\"");
        if (operation.requestContentType() != null) {
            builder.append(", \"Content-Type\": \"").append(pythonString(operation.requestContentType())).append("\"");
        }
        builder.append("}\n");
        builder.append("        )\n");
        builder.append("        with urllib.request.urlopen(request) as response:\n");
        builder.append("            self.assertEqual(").append(operation.successStatus()).append(", response.status)\n");
    }

    private String generateCsharpTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("using System;\n");
        builder.append("using System.Net.Http;\n");
        builder.append("using System.Text;\n");
        builder.append("using System.Threading.Tasks;\n");
        builder.append("using Xunit;\n\n");
        builder.append("namespace GeneratedTests;\n\n");
        builder.append("public class GeneratedApiContractTests\n{\n");
        builder.append("    private static readonly HttpClient Client = new();\n");
        builder.append("    private static readonly string BaseUrl = Environment.GetEnvironmentVariable(\"API_BASE_URL\") ?? \"")
                .append(csharpString(baseUrl)).append("\";\n\n");
        for (ApiOperation operation : operations) {
            builder.append("    [Fact]\n");
            builder.append("    public async Task ").append(capitalize(operation.name())).append("()\n    {\n");
            appendCsharpOperation(builder, operation);
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private void appendCsharpOperation(StringBuilder builder, ApiOperation operation) {
        builder.append("        using var request = new HttpRequestMessage(HttpMethod.")
                .append(csharpHttpMethod(operation.method())).append(", BaseUrl + \"")
                .append(csharpString(operation.path())).append("\");\n");
        builder.append("        request.Headers.Add(\"Accept\", \"application/json\");\n");
        if (operation.hasRequestBody()) {
            builder.append("        request.Content = new StringContent(\"{}\", Encoding.UTF8, \"")
                    .append(csharpString(operation.requestContentType() == null ? "application/json" : operation.requestContentType()))
                    .append("\"); // TODO replace with a valid request payload and auth headers if required\n");
        }
        builder.append("        using var response = await Client.SendAsync(request);\n");
        builder.append("        Assert.Equal(").append(operation.successStatus()).append(", (int)response.StatusCode);\n");
    }

    private String generateGoTest(String baseUrl, List<ApiOperation> operations) {
        boolean hasBodyOperation = operations.stream().anyMatch(ApiOperation::hasRequestBody);
        StringBuilder builder = new StringBuilder();
        builder.append("package tests\n\n");
        builder.append("import (\n");
        builder.append("    \"net/http\"\n");
        builder.append("    \"os\"\n");
        if (hasBodyOperation) {
            builder.append("    \"strings\"\n");
        }
        builder.append("    \"testing\"\n");
        builder.append(")\n\n");
        builder.append("var baseURL = envOrDefault(\"API_BASE_URL\", \"").append(goString(baseUrl)).append("\")\n\n");
        builder.append("func envOrDefault(key string, fallback string) string {\n");
        builder.append("    if value := os.Getenv(key); value != \"\" {\n");
        builder.append("        return value\n");
        builder.append("    }\n");
        builder.append("    return fallback\n");
        builder.append("}\n\n");
        for (ApiOperation operation : operations) {
            builder.append("func Test").append(capitalize(operation.name())).append("(t *testing.T) {\n");
            appendGoOperation(builder, operation);
            builder.append("}\n\n");
        }
        return builder.toString();
    }

    private void appendGoOperation(StringBuilder builder, ApiOperation operation) {
        if (operation.hasRequestBody()) {
            builder.append("    body := strings.NewReader(\"{}\") // TODO replace with a valid request payload and auth headers if required\n");
            builder.append("    req, err := http.NewRequest(\"").append(operation.method()).append("\", baseURL+\"")
                    .append(goString(operation.path())).append("\", body)\n");
        } else {
            builder.append("    req, err := http.NewRequest(\"").append(operation.method()).append("\", baseURL+\"")
                    .append(goString(operation.path())).append("\", nil)\n");
        }
        builder.append("    if err != nil {\n");
        builder.append("        t.Fatalf(\"build request: %v\", err)\n");
        builder.append("    }\n");
        builder.append("    req.Header.Set(\"Accept\", \"application/json\")\n");
        if (operation.requestContentType() != null) {
            builder.append("    req.Header.Set(\"Content-Type\", \"").append(goString(operation.requestContentType())).append("\")\n");
        }
        builder.append("    resp, err := http.DefaultClient.Do(req)\n");
        builder.append("    if err != nil {\n");
        builder.append("        t.Fatalf(\"send request: %v\", err)\n");
        builder.append("    }\n");
        builder.append("    defer resp.Body.Close()\n");
        builder.append("    if resp.StatusCode != ").append(operation.successStatus()).append(" {\n");
        builder.append("        t.Fatalf(\"expected ").append(operation.successStatus()).append(", got %d\", resp.StatusCode)\n");
        builder.append("    }\n");
    }

    private String generatePhpTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?php\n\n");
        builder.append("use PHPUnit\\Framework\\TestCase;\n\n");
        builder.append("final class GeneratedApiContractTest extends TestCase\n{\n");
        builder.append("    private string $baseUrl;\n\n");
        builder.append("    protected function setUp(): void\n    {\n");
        builder.append("        $this->baseUrl = getenv('API_BASE_URL') ?: '").append(phpString(baseUrl)).append("';\n");
        builder.append("    }\n\n");
        for (ApiOperation operation : operations) {
            builder.append("    public function test").append(capitalize(operation.name())).append("(): void\n    {\n");
            appendPhpOperation(builder, operation);
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private void appendPhpOperation(StringBuilder builder, ApiOperation operation) {
        builder.append("        $ch = curl_init($this->baseUrl . '").append(phpString(operation.path())).append("');\n");
        builder.append("        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);\n");
        builder.append("        curl_setopt($ch, CURLOPT_CUSTOMREQUEST, '").append(operation.method()).append("');\n");
        builder.append("        curl_setopt($ch, CURLOPT_HTTPHEADER, ['Accept: application/json");
        if (operation.requestContentType() != null) {
            builder.append("', 'Content-Type: ").append(phpString(operation.requestContentType()));
        }
        builder.append("']);\n");
        if (operation.hasRequestBody()) {
            builder.append("        curl_setopt($ch, CURLOPT_POSTFIELDS, '{}'); // TODO replace with a valid request payload and auth headers if required\n");
        }
        builder.append("        curl_exec($ch);\n");
        builder.append("        $statusCode = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);\n");
        builder.append("        curl_close($ch);\n");
        builder.append("        $this->assertSame(").append(operation.successStatus()).append(", $statusCode);\n");
    }

    private String generateRubyTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("require \"minitest/autorun\"\n");
        builder.append("require \"net/http\"\n");
        builder.append("require \"uri\"\n\n");
        builder.append("class ApiContractTest < Minitest::Test\n");
        builder.append("  BASE_URL = ENV.fetch(\"API_BASE_URL\", \"").append(rubyString(baseUrl)).append("\")\n\n");
        for (ApiOperation operation : operations) {
            builder.append("  def test_").append(operation.name()).append("\n");
            appendRubyOperation(builder, operation);
            builder.append("  end\n\n");
        }
        builder.append("end\n");
        return builder.toString();
    }

    private void appendRubyOperation(StringBuilder builder, ApiOperation operation) {
        builder.append("    uri = URI.parse(BASE_URL + \"").append(rubyString(operation.path())).append("\")\n");
        builder.append("    request = Net::HTTP::").append(rubyHttpClass(operation.method())).append(".new(uri)\n");
        builder.append("    request[\"Accept\"] = \"application/json\"\n");
        if (operation.requestContentType() != null) {
            builder.append("    request[\"Content-Type\"] = \"").append(rubyString(operation.requestContentType())).append("\"\n");
        }
        if (operation.hasRequestBody()) {
            builder.append("    request.body = \"{}\" # TODO replace with a valid request payload and auth headers if required\n");
        }
        builder.append("    response = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == \"https\") do |http|\n");
        builder.append("      http.request(request)\n");
        builder.append("    end\n");
        builder.append("    assert_equal ").append(operation.successStatus()).append(", response.code.to_i\n");
    }

    private String generateTypescriptTest(String baseUrl, List<ApiOperation> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("import test from \"node:test\";\n");
        builder.append("import assert from \"node:assert/strict\";\n\n");
        builder.append("const baseUrl = process.env.API_BASE_URL ?? \"").append(tsString(baseUrl)).append("\";\n\n");
        for (ApiOperation operation : operations) {
            builder.append("test(\"").append(tsString(operation.name())).append("\", async () => {\n");
            appendTypescriptOperation(builder, operation);
            builder.append("});\n\n");
        }
        return builder.toString();
    }

    private void appendTypescriptOperation(StringBuilder builder, ApiOperation operation) {
        if (operation.hasRequestBody()) {
            builder.append("  const body = JSON.stringify({}); // TODO replace with a valid request payload and auth headers if required\n");
        }
        builder.append("  const response = await fetch(baseUrl + \"").append(tsString(operation.path())).append("\", {\n");
        builder.append("    method: \"").append(operation.method()).append("\",\n");
        builder.append("    headers: {\n");
        builder.append("      \"Accept\": \"application/json\"");
        if (operation.requestContentType() != null) {
            builder.append(",\n      \"Content-Type\": \"").append(tsString(operation.requestContentType())).append("\"");
        }
        builder.append("\n    }");
        if (operation.hasRequestBody()) {
            builder.append(",\n    body");
        }
        builder.append("\n  });\n");
        builder.append("  assert.equal(response.status, ").append(operation.successStatus()).append(");\n");
    }

    private String javaHttpBuilder(String method) {
        return switch (method) {
            case "GET" -> "GET().build()";
            case "DELETE" -> "DELETE().build()";
            default -> "method(\"" + method + "\", HttpRequest.BodyPublishers.ofString(requestBody)).build()";
        };
    }

    private String kotlinRequestBuilder(String method) {
        return switch (method) {
            case "GET" -> "GET().build()";
            case "DELETE" -> "DELETE().build()";
            default -> "method(\"" + method + "\", HttpRequest.BodyPublishers.ofString(requestBody)).build()";
        };
    }

    private String csharpHttpMethod(String method) {
        return switch (method) {
            case "GET" -> "Get";
            case "POST" -> "Post";
            case "PUT" -> "Put";
            case "DELETE" -> "Delete";
            case "PATCH" -> "Patch";
            case "HEAD" -> "Head";
            case "OPTIONS" -> "Options";
            default -> method.substring(0, 1).toUpperCase(Locale.ROOT) + method.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private String rubyHttpClass(String method) {
        return switch (method) {
            case "GET" -> "Get";
            case "POST" -> "Post";
            case "PUT" -> "Put";
            case "DELETE" -> "Delete";
            case "PATCH" -> "Patch";
            case "HEAD" -> "Head";
            case "OPTIONS" -> "Options";
            default -> method.substring(0, 1).toUpperCase(Locale.ROOT) + method.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private String javaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String kotlinString(String value) {
        return javaString(value);
    }

    private String pythonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String csharpString(String value) {
        return value.replace("\"", "\"\"");
    }

    private String goString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String phpString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String rubyString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String tsString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    record ApiOperation(
            String name,
            String summary,
            String method,
            String path,
            int successStatus,
            String requestContentType,
            boolean hasRequestBody
    ) {
    }
}
