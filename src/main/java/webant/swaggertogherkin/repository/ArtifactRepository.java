package webant.swaggertogherkin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import webant.swaggertogherkin.model.Artifact;
import webant.swaggertogherkin.model.ArtifactType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    List<Artifact> findByJobIdOrderByCreatedAtAsc(UUID jobId);
    Optional<Artifact> findByJobIdAndId(UUID jobId, UUID id);
    Optional<Artifact> findByJobIdAndArtifactType(UUID jobId, ArtifactType artifactType);
    Optional<Artifact> findByJobIdAndFileName(UUID jobId, String fileName);
}
