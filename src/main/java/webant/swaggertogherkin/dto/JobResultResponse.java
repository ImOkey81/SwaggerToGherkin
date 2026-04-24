package webant.swaggertogherkin.dto;

public record JobResultResponse(
        String gherkinText,
        String resultJson
) {
}
