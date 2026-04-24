package webant.swaggertogherkin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import webant.swaggertogherkin.model.JobLog;

import java.util.List;
import java.util.UUID;

public interface JobLogRepository extends JpaRepository<JobLog, UUID> {
    List<JobLog> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
