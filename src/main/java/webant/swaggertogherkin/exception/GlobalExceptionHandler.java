package webant.swaggertogherkin.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import webant.swaggertogherkin.dto.ApiErrorDetails;
import webant.swaggertogherkin.dto.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(false, new ApiErrorDetails(exception.getCode(), exception.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(false, new ApiErrorDetails("INVALID_INPUT", exception.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(false, new ApiErrorDetails("INVALID_INPUT", "Invalid JSON request body")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ApiErrorResponse(false, new ApiErrorDetails("INVALID_INPUT", "Content-Type must be application/json")));
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<ApiErrorResponse> handleRemoteNotFound(HttpClientErrorException.NotFound exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(false, new ApiErrorDetails("INVALID_INPUT", "Swagger/OpenAPI file not found")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(new ApiErrorResponse(false, new ApiErrorDetails("INTERNAL_ERROR", exception.getMessage())));
    }
}
