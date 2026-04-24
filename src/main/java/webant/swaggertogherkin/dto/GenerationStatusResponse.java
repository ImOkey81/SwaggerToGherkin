package webant.swaggertogherkin.dto;

public record GenerationStatusResponse(
        String generationId,
        String status,
        String downloadPath,
        String message
) {
}
