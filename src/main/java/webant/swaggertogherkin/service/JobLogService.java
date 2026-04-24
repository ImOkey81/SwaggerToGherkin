package webant.swaggertogherkin.service;

import org.springframework.stereotype.Service;
import webant.swaggertogherkin.model.JobLog;
import webant.swaggertogherkin.model.JobLogLevel;
import webant.swaggertogherkin.repository.JobLogRepository;

import java.util.UUID;

@Service
public class JobLogService {

    private final JobLogRepository jobLogRepository;

    public JobLogService(JobLogRepository jobLogRepository) {
        this.jobLogRepository = jobLogRepository;
    }

    public void log(UUID jobId, JobLogLevel level, String message) {
        JobLog log = new JobLog();
        log.setJobId(jobId);
        log.setLevel(level);
        log.setMessage(message);
        jobLogRepository.save(log);
    }
}
