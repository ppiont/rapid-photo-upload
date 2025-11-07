-- V2: Create upload_jobs table for batch upload tracking
-- Upload jobs represent a batch upload session with multiple photos

CREATE TABLE upload_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    total_photos INTEGER NOT NULL DEFAULT 0,
    completed_photos INTEGER NOT NULL DEFAULT 0,
    failed_photos INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'PARTIAL_FAILURE', 'FAILED')),
    CONSTRAINT chk_total_photos CHECK (total_photos >= 0),
    CONSTRAINT chk_completed_photos CHECK (completed_photos >= 0 AND completed_photos <= total_photos),
    CONSTRAINT chk_failed_photos CHECK (failed_photos >= 0 AND failed_photos <= total_photos),
    CONSTRAINT chk_photo_counts CHECK (completed_photos + failed_photos <= total_photos)
);

-- Indexes for performance
CREATE INDEX idx_upload_jobs_user_status ON upload_jobs(user_id, status, created_at DESC);
CREATE INDEX idx_upload_jobs_created_at ON upload_jobs(created_at DESC);
CREATE INDEX idx_upload_jobs_status ON upload_jobs(status);

-- Comments for documentation
COMMENT ON TABLE upload_jobs IS 'Batch upload sessions tracking multiple photo uploads';
COMMENT ON COLUMN upload_jobs.status IS 'Job status: IN_PROGRESS, COMPLETED, PARTIAL_FAILURE, FAILED';
COMMENT ON COLUMN upload_jobs.total_photos IS 'Total number of photos in this upload batch';
COMMENT ON COLUMN upload_jobs.completed_photos IS 'Number of successfully uploaded photos';
COMMENT ON COLUMN upload_jobs.failed_photos IS 'Number of failed photo uploads';
