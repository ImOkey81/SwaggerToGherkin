package webant.swaggertogherkin.service;

import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiTestFileGeneratorTest {

    private final OpenApiTestFileGenerator generator = new OpenApiTestFileGenerator();

    @Test
    void collectsOperationsWithPathAndQueryExamples() {
        var openApi = new OpenAPIV3Parser().readContents("""
                openapi: 3.0.3
                info:
                  title: Demo
                  version: 1.0.0
                servers:
                  - url: http://localhost:8082
                paths:
                  /pets/{petId}:
                    get:
                      operationId: getPet
                      parameters:
                        - in: path
                          name: petId
                          required: true
                          schema:
                            type: integer
                        - in: query
                          name: include
                          schema:
                            type: string
                      responses:
                        '200':
                          description: ok
                  /pets:
                    post:
                      operationId: createPet
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                      responses:
                        '201':
                          description: created
                """, null, null).getOpenAPI();

        List<OpenApiTestFileGenerator.ApiOperation> operations = OpenApiTestFileGenerator.collectOperations(openApi);

        assertThat(operations).extracting(OpenApiTestFileGenerator.ApiOperation::name)
                .containsExactly("createPet", "getPet");
        assertThat(operations).extracting(OpenApiTestFileGenerator.ApiOperation::path)
                .containsExactly("/pets", "/pets/1?include=sample");
        assertThat(operations).extracting(OpenApiTestFileGenerator.ApiOperation::successStatus)
                .containsExactly(201, 200);
    }

    @Test
    void generatesSingleJavaTestFileWithoutClientProjectScaffolding() {
        var openApi = new OpenAPIV3Parser().readContents("""
                openapi: 3.0.3
                info:
                  title: Demo
                  version: 1.0.0
                servers:
                  - url: http://localhost:8082
                paths:
                  /health:
                    get:
                      operationId: health
                      responses:
                        '200':
                          description: ok
                """, null, null).getOpenAPI();

        Map<String, String> generatedFiles = generator.generate(openApi, "java");

        assertThat(generatedFiles).containsOnlyKeys("src/test/java/generated/GeneratedApiContractTest.java");
        assertThat(generatedFiles.get("src/test/java/generated/GeneratedApiContractTest.java"))
                .contains("class GeneratedApiContractTest")
                .contains("void health() throws Exception")
                .doesNotContain("ApiClient")
                .doesNotContain("pom.xml");
    }
}
