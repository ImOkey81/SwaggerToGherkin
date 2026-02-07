package webant.swaggertogherkin.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import webant.swaggertogherkin.dto.GitHubRequest;
import webant.swaggertogherkin.service.GherkinGeneratorService;
import webant.swaggertogherkin.service.SwaggerTestGeneratorService;

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

    @PostMapping("/generate-tests")
    public ResponseEntity<String> generateTests(@RequestBody GitHubRequest request) {
        try {
            String result = testGeneratorService.generateTestsFromGitHub(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}