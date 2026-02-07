package webant.swaggertogherkin.util;


import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GitHubContentFetcher {

    public String fetchContent(String repoUrl, String filePath) {
        String rawUrl = convertToRawUrl(repoUrl);
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(rawUrl, String.class);
    }

    private String convertToRawUrl(String repoUrl) {
        return repoUrl.replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/");
    }
}