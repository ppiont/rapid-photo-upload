package com.demo.photoupload.infrastructure.persistence.mapper;

import com.demo.photoupload.domain.model.*;
import com.demo.photoupload.infrastructure.persistence.entity.UploadJobEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper to convert between UploadJob domain object and UploadJobEntity.
 */
@Component
public class UploadJobMapper {

    /**
     * Convert UploadJobEntity to UploadJob domain object.
     */
    public UploadJob toDomain(UploadJobEntity entity) {
        if (entity == null) {
            return null;
        }

        UploadJobId jobId = new UploadJobId(entity.getId());
        UserId userId = new UserId(entity.getUserId());

        // Reconstruct UploadJob using reflection (similar to PhotoMapper)
        return reconstructUploadJob(
            jobId,
            userId,
            UploadJobStatus.valueOf(entity.getStatus()),
            entity.getTotalPhotos(),
            entity.getCompletedPhotos(),
            entity.getFailedPhotos(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCompletedAt()
        );
    }

    /**
     * Convert UploadJob domain object to UploadJobEntity.
     */
    public UploadJobEntity toEntity(UploadJob uploadJob) {
        if (uploadJob == null) {
            return null;
        }

        UploadJobEntity entity = new UploadJobEntity(uploadJob.getUserId().value());
        entity.setId(uploadJob.getId().value());
        entity.setStatus(uploadJob.getStatus().name());
        entity.setTotalPhotos(uploadJob.getTotalPhotos());
        entity.setCompletedPhotos(uploadJob.getCompletedPhotos());
        entity.setFailedPhotos(uploadJob.getFailedPhotos());
        entity.setCreatedAt(uploadJob.getCreatedAt());
        entity.setUpdatedAt(uploadJob.getUpdatedAt());
        entity.setCompletedAt(uploadJob.getCompletedAt());

        return entity;
    }

    /**
     * Update an existing UploadJobEntity from an UploadJob domain object.
     * Used to preserve JPA-managed state during updates.
     */
    public void updateEntity(UploadJob uploadJob, UploadJobEntity entity) {
        entity.setStatus(uploadJob.getStatus().name());
        entity.setTotalPhotos(uploadJob.getTotalPhotos());
        entity.setCompletedPhotos(uploadJob.getCompletedPhotos());
        entity.setFailedPhotos(uploadJob.getFailedPhotos());
        entity.setUpdatedAt(uploadJob.getUpdatedAt());
        entity.setCompletedAt(uploadJob.getCompletedAt());
    }

    /**
     * Reconstruct UploadJob domain object with all fields.
     *
     * TODO: Consider adding a static factory method in UploadJob domain class like:
     * UploadJob.reconstruct(UploadJobId, UserId, UploadJobStatus, counts, timestamps...)
     */
    private UploadJob reconstructUploadJob(
        UploadJobId id,
        UserId userId,
        UploadJobStatus status,
        int totalPhotos,
        int completedPhotos,
        int failedPhotos,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        java.time.Instant completedAt
    ) {
        try {
            // Create a minimal UploadJob instance
            var constructor = UploadJob.class.getDeclaredConstructor(UploadJobId.class, UserId.class);
            constructor.setAccessible(true);
            UploadJob uploadJob = constructor.newInstance(id, userId);

            // Set all fields using reflection
            var statusField = UploadJob.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(uploadJob, status);

            var totalPhotosField = UploadJob.class.getDeclaredField("totalPhotos");
            totalPhotosField.setAccessible(true);
            totalPhotosField.set(uploadJob, totalPhotos);

            var completedPhotosField = UploadJob.class.getDeclaredField("completedPhotos");
            completedPhotosField.setAccessible(true);
            completedPhotosField.set(uploadJob, completedPhotos);

            var failedPhotosField = UploadJob.class.getDeclaredField("failedPhotos");
            failedPhotosField.setAccessible(true);
            failedPhotosField.set(uploadJob, failedPhotos);

            var createdAtField = UploadJob.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(uploadJob, createdAt);

            var updatedAtField = UploadJob.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(uploadJob, updatedAt);

            if (completedAt != null) {
                var completedAtField = UploadJob.class.getDeclaredField("completedAt");
                completedAtField.setAccessible(true);
                completedAtField.set(uploadJob, completedAt);
            }

            return uploadJob;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct UploadJob from entity", e);
        }
    }
}
