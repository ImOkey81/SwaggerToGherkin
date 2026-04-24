package webant.swaggertogherkin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import webant.swaggertogherkin.dto.JobDetailsResponse;
import webant.swaggertogherkin.dto.JobListResponse;
import webant.swaggertogherkin.dto.JobResultResponse;
import webant.swaggertogherkin.exception.ApiException;
import webant.swaggertogherkin.model.Job;
import webant.swaggertogherkin.model.JobInput;
import webant.swaggertogherkin.model.JobLogLevel;
import webant.swaggertogherkin.model.JobResult;
import webant.swaggertogherkin.model.JobServiceType;
import webant.swaggertogherkin.model.JobStatus;
import webant.swaggertogherkin.repository.JobInputRepository;
import webant.swaggertogherkin.repository.JobRepository;
import webant.swaggertogherkin.repository.JobResultRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobInputRepository jobInputRepository;
    private final JobResultRepository jobResultRepository;
    private final JobLogService jobLogService;
    private final ObjectMapper objectMapper;

    public JobService(JobRepository jobRepository,
                      JobInputRepository jobInputRepository,
                      JobResultRepository jobResultRepository,
                      JobLogService jobLogService,
                      ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobInputRepository = jobInputRepository;
        this.jobResultRepository = jobResultRepository;
        this.jobLogService = jobLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Job createJob(JobServiceType serviceType, String title, Map<String, Object> payload) {
        Job job = new Job();
        job.setServiceType(serviceType);
        job.setStatus(JobStatus.pending);
        job.setTitle(title);
        Job savedJob = jobRepository.save(job);

        JobInput jobInput = new JobInput();
        jobInput.setJobId(savedJob.getId());
        jobInput.setPayloadJson(writeJson(payload));
        jobInputRepository.save(jobInput);

        jobLogService.log(savedJob.getId(), JobLogLevel.INFO, "job created");
        return savedJob;
    }

    @Transactional
    public Job markProcessing(UUID jobId) {
        Job job = getJobEntity(jobId);
        if (job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }
        job.setStatus(JobStatus.processing);
        job.setErrorMessage(null);
        Job savedJob = jobRepository.save(job);
        jobLogService.log(jobId, JobLogLevel.INFO, "generation started");
        return savedJob;
    }

    @Transactional
    public Job markDone(UUID jobId) {
        Job job = getJobEntity(jobId);
        job.setStatus(JobStatus.done);
        job.setFinishedAt(Instant.now());
        job.setErrorMessage(null);
        Job savedJob = jobRepository.save(job);
        jobLogService.log(jobId, JobLogLevel.INFO, "generation finished");
        return savedJob;
    }

    @Transactional
    public Job markFailed(UUID jobId, String errorMessage) {
        Job job = getJobEntity(jobId);
        if (job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }
        job.setStatus(JobStatus.failed);
        job.setFinishedAt(Instant.now());
        job.setErrorMessage(errorMessage);
        Job savedJob = jobRepository.save(job);
        jobLogService.log(jobId, JobLogLevel.ERROR, "generation failed: " + errorMessage);
        return savedJob;
    }

    @Transactional
    public void saveGherkinResult(UUID jobId, String gherkinText) {
        JobResult jobResult = jobResultRepository.findById(jobId).orElseGet(JobResult::new);
        jobResult.setJobId(jobId);
        jobResult.setGherkinText(gherkinText);
        jobResultRepository.save(jobResult);
    }

    @Transactional
    public void saveTestResult(UUID jobId, Map<String, Object> resultPayload) {
        JobResult jobResult = jobResultRepository.findById(jobId).orElseGet(JobResult::new);
        jobResult.setJobId(jobId);
        jobResult.setResultJson(writeJson(resultPayload));
        jobResultRepository.save(jobResult);
    }

    @Transactional(readOnly = true)
    public Job getJobEntity(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException("JOB_NOT_FOUND", "Job not found: " + jobId, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public JobDetailsResponse getJob(UUID jobId) {
        return toDetailsResponse(getJobEntity(jobId));
    }

    @Transactional(readOnly = true)
    public JobResultResponse getJobResult(UUID jobId) {
        getJobEntity(jobId);
        JobResult result = jobResultRepository.findById(jobId).orElse(null);
        return new JobResultResponse(
                result == null ? null : result.getGherkinText(),
                result == null ? null : result.getResultJson()
        );
    }

    @Transactional(readOnly = true)
    public JobListResponse listJobs(JobServiceType serviceType, JobStatus status, int limit, int offset) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        int safeOffset = Math.max(offset, 0);

        Specification<Job> specification = Specification.where(null);
        if (serviceType != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("serviceType"), serviceType));
        }
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        List<JobDetailsResponse> items = jobRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .skip(safeOffset)
                .limit(safeLimit)
                .map(this::toDetailsResponse)
                .toList();
        long total = jobRepository.count(specification);

        return new JobListResponse(
                items,
                total,
                safeLimit,
                safeOffset
        );
    }

    private JobDetailsResponse toDetailsResponse(Job job) {
        return new JobDetailsResponse(
                job.getId(),
                job.getServiceType().name(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage()
        );
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException("JSON_SERIALIZATION_ERROR", "Failed to serialize payload", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
