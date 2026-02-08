package webant.swaggertogherkin.dto;

public class TestGenerationResponse {
    private String message;
    private String generationId;
    private String downloadPath;

    public TestGenerationResponse(String message, String generationId, String downloadPath) {
        this.message = message;
        this.generationId = generationId;
        this.downloadPath = downloadPath;
    }

    public String getMessage() {
        return message;
    }

    public String getGenerationId() {
        return generationId;
    }

    public String getDownloadPath() {
        return downloadPath;
    }
}
