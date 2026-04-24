package webant.swaggertogherkin.dto;

import java.util.List;

public record JobListResponse(
        List<JobDetailsResponse> items,
        long total,
        int limit,
        int offset
) {
}
