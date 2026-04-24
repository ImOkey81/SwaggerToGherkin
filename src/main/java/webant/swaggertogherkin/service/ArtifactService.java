package webant.swaggertogherkin.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import webant.swaggertogherkin.dto.ArtifactResponse;
import webant.swaggertogherkin.exception.ApiException;
import webant.swaggertogherkin.model.Artifact;
import webant.swaggertogherkin.model.ArtifactType;
import webant.swaggertogherkin.repository.ArtifactRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ArtifactService {

    private final ArtifactRepository artifactRepository;

    public ArtifactService(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @Transactional
    public Artifact saveArtifact(UUID jobId, ArtifactType artifactType, String fileName, Path filePath, String mimeType) {
        Artifact artifact = new Artifact();
        artifact.setJobId(jobId);
        artifact.setArtifactType(artifactType);
        artifact.setFileName(fileName);
        artifact.setFilePath(filePath.toAbsolutePath().normalize().toString());
        artifact.setMimeType(mimeType);
        try {
            artifact.setSizeBytes(Files.size(filePath));
        } catch (IOException exception) {
            throw new ApiException("ARTIFACT_READ_ERROR", "Failed to read artifact size: " + filePath, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return artifactRepository.save(artifact);
    }

    @Transactional(readOnly = true)
    public List<ArtifactResponse> listArtifacts(UUID jobId) {
        return artifactRepository.findByJobIdOrderByCreatedAtAsc(jobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Artifact getArtifact(UUID jobId, UUID artifactId) {
        return artifactRepository.findByJobIdAndId(jobId, artifactId)
                .orElseThrow(() -> new ApiException("ARTIFACT_NOT_FOUND", "Artifact not found: " + artifactId, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Artifact getArchiveArtifact(UUID jobId) {
        return artifactRepository.findByJobIdAndArtifactType(jobId, ArtifactType.generated_zip)
                .orElseThrow(() -> new ApiException("ARTIFACT_NOT_FOUND", "Generated archive not found for job: " + jobId, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Artifact getGeneratedFileArtifact(UUID jobId, String fileName) {
        return artifactRepository.findByJobIdAndFileName(jobId, fileName)
                .orElseThrow(() -> new ApiException("FILE_NOT_FOUND", "Generated file not found: " + fileName, HttpStatus.NOT_FOUND));
    }

    private ArtifactResponse toResponse(Artifact artifact) {
        return new ArtifactResponse(
                artifact.getId(),
                artifact.getArtifactType().name(),
                artifact.getFileName(),
                artifact.getMimeType(),
                artifact.getSizeBytes(),
                artifact.getCreatedAt()
        );
    }
}
