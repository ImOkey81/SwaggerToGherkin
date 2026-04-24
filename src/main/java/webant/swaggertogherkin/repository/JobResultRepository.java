package webant.swaggertogherkin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import webant.swaggertogherkin.model.JobResult;

import java.util.UUID;

public interface JobResultRepository extends JpaRepository<JobResult, UUID> {
}
