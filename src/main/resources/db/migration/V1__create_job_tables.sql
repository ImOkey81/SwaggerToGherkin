CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    service_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error_message TEXT
);

CREATE TABLE job_inputs (
    job_id UUID PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    payload_json TEXT NOT NULL
);

CREATE TABLE job_results (
    job_id UUID PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    gherkin_text TEXT,
    result_json TEXT
);

CREATE TABLE artifacts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    artifact_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(1024) NOT NULL,
    file_path VARCHAR(2048) NOT NULL,
    mime_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE job_logs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    level VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_jobs_service_type ON jobs(service_type);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX idx_artifacts_job_id ON artifacts(job_id);
CREATE INDEX idx_job_logs_job_id ON job_logs(job_id);
