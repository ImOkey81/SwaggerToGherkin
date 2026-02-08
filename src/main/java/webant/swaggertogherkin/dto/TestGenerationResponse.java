package webant.swaggertogherkin.dto;

public class TestGenerationResponse {
    private String message;
    private String id;
    private String generationId;
    private String downloadPath;

    public TestGenerationResponse(String message, String generationId, String downloadPath) {
        this.message = message;
        this.id = generationId;
        this.generationId = generationId;
        this.downloadPath = downloadPath;
    }

    public String getMessage() {
        return message;
    }

    public String getId() {
        return id;
    }

    public String getGenerationId() {
        return generationId;
    }

    public String getDownloadPath() {
        return downloadPath;
    }
}
