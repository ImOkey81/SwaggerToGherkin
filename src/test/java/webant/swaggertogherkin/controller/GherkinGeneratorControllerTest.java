package webant.swaggertogherkin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import webant.swaggertogherkin.service.GherkinGeneratorService;
import webant.swaggertogherkin.service.SwaggerTestGeneratorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GherkinGeneratorController.class)
class GherkinGeneratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GherkinGeneratorService gherkinGeneratorService;

    @MockBean
    private SwaggerTestGeneratorService swaggerTestGeneratorService;

    @Test
    void generateTestsReturnsJsonWithGenerationId() throws Exception {
        when(swaggerTestGeneratorService.generateTestsFromGitHub(any())).thenReturn("abc-123");

        mockMvc.perform(post("/generate-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"repoUrl\": \"https://github.com/ImOkey81/SwaggerToGherkin/blob/main/swagger.yaml\",
                                  \"filePath\": \"swagger.yaml\",
                                  \"language\": \"java\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Tests generated successfully"))
                .andExpect(jsonPath("$.generationId").value("abc-123"))
                .andExpect(jsonPath("$.downloadPath").value("/generated-tests/abc-123"));
    }
}
