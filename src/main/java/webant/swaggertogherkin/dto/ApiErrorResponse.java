package webant.swaggertogherkin.dto;

public record ApiErrorResponse(boolean success, ApiErrorDetails error) {
}
