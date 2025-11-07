-- V3: Create photos table for individual photo tracking
-- Photos represent individual files in an upload job with their S3 location

CREATE TABLE photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES upload_jobs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Photo metadata
    filename VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,

    -- S3 location
    s3_bucket VARCHAR(255) NOT NULL,
    s3_key VARCHAR(1024) NOT NULL,

    -- Upload status and lifecycle
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    upload_started_at TIMESTAMP,
    upload_completed_at TIMESTAMP,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'UPLOADING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_file_size CHECK (file_size_bytes > 0),
    CONSTRAINT chk_s3_key_unique UNIQUE (s3_key)
);

-- Indexes for performance
CREATE INDEX idx_photos_job_status ON photos(job_id, status);
CREATE INDEX idx_photos_user_status ON photos(user_id, status, created_at DESC);
CREATE INDEX idx_photos_s3_key ON photos(s3_key);
CREATE INDEX idx_photos_created_at ON photos(created_at DESC);

-- Comments for documentation
COMMENT ON TABLE photos IS 'Individual photos in upload jobs with S3 storage locations';
COMMENT ON COLUMN photos.status IS 'Photo upload status: PENDING, UPLOADING, COMPLETED, FAILED';
COMMENT ON COLUMN photos.filename IS 'Generated unique filename in S3';
COMMENT ON COLUMN photos.original_filename IS 'Original filename from client upload';
COMMENT ON COLUMN photos.s3_key IS 'Full S3 object key (path) - must be unique';
COMMENT ON COLUMN photos.file_size_bytes IS 'File size in bytes for validation and billing';
