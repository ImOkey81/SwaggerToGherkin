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
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.dto.TestGenerationResponse;
import webant.swaggertogherkin.service.GherkinGeneratorService;
import webant.swaggertogherkin.service.SwaggerTestGeneratorService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class GherkinGeneratorController {

    private final GherkinGeneratorService gherkinService;
    private final SwaggerTestGeneratorService testGeneratorService;

    public GherkinGeneratorController(GherkinGeneratorService gherkinService,
                                      SwaggerTestGeneratorService testGeneratorService) {
        this.gherkinService = gherkinService;
        this.testGeneratorService = testGeneratorService;
    }

    @PostMapping("/generate-gherkin")
    public ResponseEntity<String> generateGherkin(@RequestBody GitHubRequest request) {
        try {
            String gherkin = gherkinService.generateGherkinFromGitHub(request);
            return ResponseEntity.ok(gherkin);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping(value = "/generate-tests", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TestGenerationResponse> generateTests(@RequestBody GitHubRequest request) {
        try {
            String generationId = testGeneratorService.generateTestsFromGitHub(request);
            TestGenerationResponse response = new TestGenerationResponse(
                    "Tests generated successfully",
                    generationId,
                    "/generated-tests/" + generationId
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new TestGenerationResponse("Error: " + e.getMessage(), null, null));
        }
    }

    @GetMapping("/generated-tests/{generationId}")
    public ResponseEntity<byte[]> downloadGeneratedTests(@PathVariable String generationId) {
        try {
            byte[] archive = testGeneratorService.getGeneratedTestsArchiveById(generationId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename("generated-tests.zip").build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(archive);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/generated-tests/{generationId}/files")
    public ResponseEntity<List<String>> listGeneratedFiles(@PathVariable String generationId) {
        try {
            return ResponseEntity.ok(testGeneratorService.getGeneratedFilesById(generationId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/generated-tests/{generationId}/file")
    public ResponseEntity<byte[]> getGeneratedFile(
            @PathVariable String generationId,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/generated-tests")
    public ResponseEntity<byte[]> downloadGeneratedTests(@RequestParam String outputDir) {
        try {
            byte[] archive = testGeneratorService.getGeneratedTestsArchive(outputDir);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename("generated-tests.zip").build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(archive);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
