package webant.swaggertogherkin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "job_results")
public class JobResult {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID jobId;

    @Column(columnDefinition = "TEXT")
    private String gherkinText;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getGherkinText() {
        return gherkinText;
    }

    public void setGherkinText(String gherkinText) {
        this.gherkinText = gherkinText;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }
}
