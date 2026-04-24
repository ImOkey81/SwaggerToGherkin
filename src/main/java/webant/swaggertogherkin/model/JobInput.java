package webant.swaggertogherkin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "job_inputs")
public class JobInput {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
