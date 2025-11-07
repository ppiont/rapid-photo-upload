package com.demo.photoupload.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * UploadJob aggregate root.
 * Represents a batch upload session containing multiple photos.
 *
 * Domain Invariants:
 * - Total photos count must equal the number of photos in the collection
 * - completed_photos + failed_photos ≤ total_photos
 * - Status is derived from photo states (not set directly)
 * - Job can only complete when all photos reach terminal state
 */
public class UploadJob {
    private final UploadJobId id;
    private final UserId userId;
    private final List<Photo> photos;

    private UploadJobStatus status;
    private int totalPhotos;
    private int completedPhotos;
    private int failedPhotos;

    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    // Private constructor to enforce factory methods
    private UploadJob(UploadJobId id, UserId userId) {
        this.id = Objects.requireNonNull(id, "UploadJob ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.photos = new ArrayList<>();
        this.status = UploadJobStatus.IN_PROGRESS;
        this.totalPhotos = 0;
        this.completedPhotos = 0;
        this.failedPhotos = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Factory method to create a new UploadJob with photos.
     */
    public static UploadJob create(UserId userId, List<PhotoMetadata> photoMetadataList, String s3Bucket) {
        Objects.requireNonNull(photoMetadataList, "Photo metadata list cannot be null");
        if (photoMetadataList.isEmpty()) {
            throw new IllegalArgumentException("Cannot create job with zero photos");
        }

        UploadJobId jobId = UploadJobId.generate();
        UploadJob job = new UploadJob(jobId, userId);

        // Create photo entities for each metadata
        for (PhotoMetadata metadata : photoMetadataList) {
            Photo photo = Photo.create(jobId, userId, metadata, s3Bucket);
            job.photos.add(photo);
        }

        job.totalPhotos = job.photos.size();
        return job;
    }

    /**
     * Update job status based on photo states.
     * This method should be called after any photo status change.
     *
     * Business rules:
     * - If all photos COMPLETED → COMPLETED
     * - If all photos FAILED → FAILED
     * - If mix of COMPLETED and FAILED → PARTIAL_FAILURE
     * - Otherwise → IN_PROGRESS
     */
    public void checkAndUpdateStatus() {
        long completed = photos.stream().filter(Photo::isCompleted).count();
        long failed = photos.stream().filter(Photo::isFailed).count();
        long terminal = completed + failed;

        this.completedPhotos = (int) completed;
        this.failedPhotos = (int) failed;

        // Determine new status
        UploadJobStatus newStatus;
        if (terminal == totalPhotos) {
            // All photos in terminal state
            if (completed == totalPhotos) {
                newStatus = UploadJobStatus.COMPLETED;
                this.completedAt = Instant.now();
            } else if (failed == totalPhotos) {
                newStatus = UploadJobStatus.FAILED;
                this.completedAt = Instant.now();
            } else {
                newStatus = UploadJobStatus.PARTIAL_FAILURE;
                this.completedAt = Instant.now();
            }
        } else {
            newStatus = UploadJobStatus.IN_PROGRESS;
        }

        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Add a photo to this job (for reconstruction from persistence).
     * WARNING: Only use during entity reconstruction, not for domain logic.
     */
    public void addPhoto(Photo photo) {
        if (!photo.getJobId().equals(this.id)) {
            throw new IllegalArgumentException("Photo does not belong to this job");
        }
        this.photos.add(photo);
    }

    // Getters
    public UploadJobId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public List<Photo> getPhotos() {
        return Collections.unmodifiableList(photos);
    }

    public UploadJobStatus getStatus() {
        return status;
    }

    public int getTotalPhotos() {
        return totalPhotos;
    }

    public int getCompletedPhotos() {
        return completedPhotos;
    }

    public int getFailedPhotos() {
        return failedPhotos;
    }

    public int getPendingPhotos() {
        return totalPhotos - completedPhotos - failedPhotos;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isCompleted() {
        return status.isTerminal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadJob that = (UploadJob) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UploadJob{" +
            "id=" + id +
            ", status=" + status +
            ", totalPhotos=" + totalPhotos +
            ", completedPhotos=" + completedPhotos +
            ", failedPhotos=" + failedPhotos +
            '}';
    }
}
