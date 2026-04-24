package webant.swaggertogherkin.dto;

import java.time.Instant;
import java.util.UUID;

public record ArtifactResponse(
        UUID id,
        String artifactType,
        String fileName,
        String mimeType,
        long sizeBytes,
        Instant createdAt
) {
}
