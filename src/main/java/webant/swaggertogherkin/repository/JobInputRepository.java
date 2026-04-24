package webant.swaggertogherkin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import webant.swaggertogherkin.model.JobInput;

import java.util.UUID;

public interface JobInputRepository extends JpaRepository<JobInput, UUID> {
}
