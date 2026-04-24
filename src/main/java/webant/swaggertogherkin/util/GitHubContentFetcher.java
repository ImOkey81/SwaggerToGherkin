package webant.swaggertogherkin.util;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Component
public class GitHubContentFetcher {
    private static final String GITHUB_HOST = "github.com";
    private static final String RAW_GITHUB_HOST = "raw.githubusercontent.com";

    public String fetchContent(String repoUrl, String filePath) {
        String rawUrl = resolveRawFileUrl(repoUrl, filePath);
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(rawUrl, String.class);
    }

    String resolveRawFileUrl(String repoUrl, String filePath) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }

        URI uri;
        try {
            uri = URI.create(repoUrl.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported repoUrl format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("unsupported repoUrl format");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("unsupported repoUrl format");
        }

        if (RAW_GITHUB_HOST.equalsIgnoreCase(host)) {
            return normalizeRawUrl(uri);
        }

        if (!GITHUB_HOST.equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("unsupported repoUrl format");
        }

        String normalizedPath = trimSlashes(uri.getPath());
        String[] parts = normalizedPath.isEmpty() ? new String[0] : normalizedPath.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("unsupported repoUrl format");
        }

        String owner = parts[0];
        String repo = parts[1];

        if (parts.length == 2) {
            return buildRawUrlForRepositoryRoot(owner, repo, filePath);
        }

        if (parts.length >= 5 && "blob".equalsIgnoreCase(parts[2])) {
            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + join(parts, 3);
        }

        throw new IllegalArgumentException("unsupported repoUrl format");
    }

    String buildRawUrl(String repoUrl, String filePath) {
        return resolveRawFileUrl(repoUrl, filePath);
    }

    private String normalizeRawUrl(URI uri) {
        String normalizedPath = trimSlashes(uri.getPath());
        String[] parts = normalizedPath.isEmpty() ? new String[0] : normalizedPath.split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException("unsupported repoUrl format");
        }
        return URI.create("https://" + RAW_GITHUB_HOST + "/" + join(parts, 0)).toString();
    }

    private String buildRawUrlForRepositoryRoot(String owner, String repo, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL");
        }

        String normalizedFilePath = filePath.strip()
                .replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");

        if (normalizedFilePath.isBlank()) {
            throw new IllegalArgumentException("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL");
        }

        return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/main/" + normalizedFilePath;
    }

    private String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String join(String[] parts, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
