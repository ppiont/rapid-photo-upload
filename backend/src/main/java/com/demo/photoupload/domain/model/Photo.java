package com.demo.photoupload.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Photo aggregate root.
 * Represents an individual photo upload with its lifecycle and S3 location.
 *
 * Domain Invariants:
 * - Photo must have valid metadata
 * - Status transitions must follow the state machine
 * - S3 location is set only when created
 * - Timestamps reflect actual state transitions
 */
public class Photo {
    private final PhotoId id;
    private final UploadJobId jobId;
    private final UserId userId;
    private final PhotoMetadata metadata;
    private final S3Location s3Location;
    private final String filename;

    private PhotoStatus status;
    private Instant uploadStartedAt;
    private Instant uploadCompletedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    // Private constructor to enforce factory methods
    private Photo(
        PhotoId id,
        UploadJobId jobId,
        UserId userId,
        PhotoMetadata metadata,
        S3Location s3Location,
        String filename
    ) {
        this.id = Objects.requireNonNull(id, "Photo ID cannot be null");
        this.jobId = Objects.requireNonNull(jobId, "Job ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "Photo metadata cannot be null");
        this.s3Location = Objects.requireNonNull(s3Location, "S3 location cannot be null");
        this.filename = Objects.requireNonNull(filename, "Filename cannot be null");

        this.status = PhotoStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Factory method to create a new Photo in PENDING status.
     */
    public static Photo create(
        UploadJobId jobId,
        UserId userId,
        PhotoMetadata metadata,
        String s3Bucket
    ) {
        PhotoId photoId = PhotoId.generate();
        String filename = metadata.generateS3Filename(photoId);
        S3Location s3Location = S3Location.forPhoto(s3Bucket, filename);

        return new Photo(photoId, jobId, userId, metadata, s3Location, filename);
    }

    /**
     * Mark photo as started uploading.
     * Transition: PENDING → UPLOADING
     */
    public void markAsStarted() {
        if (!status.canTransitionTo(PhotoStatus.UPLOADING)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to UPLOADING", status)
            );
        }

        this.status = PhotoStatus.UPLOADING;
        this.uploadStartedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark photo as completed.
     * Transition: UPLOADING → COMPLETED
     */
    public void markAsCompleted() {
        if (!status.canTransitionTo(PhotoStatus.COMPLETED)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to COMPLETED", status)
            );
        }

        this.status = PhotoStatus.COMPLETED;
        this.uploadCompletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark photo as failed.
     * Transition: Any → FAILED
     */
    public void markAsFailed() {
        if (!status.canTransitionTo(PhotoStatus.FAILED)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to FAILED", status)
            );
        }

        this.status = PhotoStatus.FAILED;
        this.uploadCompletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters (no setters to maintain immutability and encapsulation)
    public PhotoId getId() {
        return id;
    }

    public UploadJobId getJobId() {
        return jobId;
    }

    public UserId getUserId() {
        return userId;
    }

    public PhotoMetadata getMetadata() {
        return metadata;
    }

    public S3Location getS3Location() {
        return s3Location;
    }

    public String getFilename() {
        return filename;
    }

    public PhotoStatus getStatus() {
        return status;
    }

    public Instant getUploadStartedAt() {
        return uploadStartedAt;
    }

    public Instant getUploadCompletedAt() {
        return uploadCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCompleted() {
        return status == PhotoStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == PhotoStatus.FAILED;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Photo photo = (Photo) o;
        return Objects.equals(id, photo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Photo{" +
            "id=" + id +
            ", status=" + status +
            ", filename='" + filename + '\'' +
            '}';
    }
}
