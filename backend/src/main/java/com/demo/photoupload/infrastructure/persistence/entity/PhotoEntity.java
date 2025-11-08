package com.demo.photoupload.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for photos table.
 * Maps to database schema defined in V3__create_photos_table.sql
 */
@Entity
@Table(name = "photos", indexes = {
    @Index(name = "idx_photos_job_status", columnList = "job_id, status"),
    @Index(name = "idx_photos_user_status", columnList = "user_id, status, created_at"),
    @Index(name = "idx_photos_s3_key", columnList = "s3_key", unique = true)
})
public class PhotoEntity {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "s3_bucket", nullable = false, length = 255)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 1024)
    private String s3Key;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "upload_started_at")
    private Instant uploadStartedAt;

    @Column(name = "upload_completed_at")
    private Instant uploadCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Many-to-one relationship with upload job
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", insertable = false, updatable = false)
    private UploadJobEntity uploadJob;

    // JPA requires a no-arg constructor
    protected PhotoEntity() {
    }

    public PhotoEntity(UUID jobId, UUID userId, String filename, String originalFilename,
                       Long fileSizeBytes, String mimeType, String s3Bucket, String s3Key) {
        this.jobId = jobId;
        this.userId = userId;
        this.filename = filename;
        this.originalFilename = originalFilename;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
        this.s3Bucket = s3Bucket;
        this.s3Key = s3Key;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUploadStartedAt() {
        return uploadStartedAt;
    }

    public void setUploadStartedAt(Instant uploadStartedAt) {
        this.uploadStartedAt = uploadStartedAt;
    }

    public Instant getUploadCompletedAt() {
        return uploadCompletedAt;
    }

    public void setUploadCompletedAt(Instant uploadCompletedAt) {
        this.uploadCompletedAt = uploadCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UploadJobEntity getUploadJob() {
        return uploadJob;
    }

    public void setUploadJob(UploadJobEntity uploadJob) {
        this.uploadJob = uploadJob;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
