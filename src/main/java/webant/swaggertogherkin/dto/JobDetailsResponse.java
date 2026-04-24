package webant.swaggertogherkin.dto;

import java.time.Instant;
import java.util.UUID;

public record JobDetailsResponse(
        UUID jobId,
        String serviceType,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {
}
