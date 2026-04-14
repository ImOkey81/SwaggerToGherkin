package webant.swaggertogherkin.dto;

public record TestGenerationResponse(
        String message,
        String generationId,
        String downloadPath,
        String status
) {
}
