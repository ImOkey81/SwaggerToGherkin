package webant.swaggertogherkin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GitHubRequest {
    @JsonProperty(required = true)
    private String repoUrl;
    private String filePath;
    private String language;

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
