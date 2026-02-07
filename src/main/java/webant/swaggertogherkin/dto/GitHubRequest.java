package webant.swaggertogherkin.dto;
// GitHubRequest.java

public class GitHubRequest {
    private String repoUrl;
    private String filePath;
    private String language; // java, python, etc. for test generation

    // Getters and Setters
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
