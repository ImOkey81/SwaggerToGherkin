package webant.swaggertogherkin.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubContentFetcherTest {

    private final GitHubContentFetcher fetcher = new GitHubContentFetcher();

    @Test
    void buildRawUrlUsesRawUrlAsIs() {
        String rawUrl = fetcher.resolveRawFileUrl(
                "https://raw.githubusercontent.com/user/repo/main/openapi.yaml",
                null
        );

        assertThat(rawUrl).isEqualTo("https://raw.githubusercontent.com/user/repo/main/openapi.yaml");
    }

    @Test
    void buildRawUrlConvertsBlobUrlToRawUrl() {
        String rawUrl = fetcher.resolveRawFileUrl(
                "https://github.com/user/repo/blob/main/openapi.yaml",
                null
        );

        assertThat(rawUrl).isEqualTo("https://raw.githubusercontent.com/user/repo/main/openapi.yaml");
    }

    @Test
    void buildRawUrlBuildsRawUrlFromRepositoryRootAndFilePath() {
        String rawUrl = fetcher.resolveRawFileUrl(
                "https://github.com/user/repo",
                "specs/openapi.yaml"
        );

        assertThat(rawUrl).isEqualTo("https://raw.githubusercontent.com/user/repo/main/specs/openapi.yaml");
    }

    @Test
    void buildRawUrlRejectsRepositoryRootWithoutFilePath() {
        assertThatThrownBy(() -> fetcher.resolveRawFileUrl("https://github.com/user/repo", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repoUrl points to repository root. Provide filePath or pass direct GitHub file URL");
    }

    @Test
    void buildRawUrlRejectsUnsupportedUrlFormat() {
        assertThatThrownBy(() -> fetcher.resolveRawFileUrl("https://example.com/user/repo/openapi.yaml", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported repoUrl format");
    }
}
