package com.demo.photoupload.infrastructure.persistence.mapper;

import com.demo.photoupload.domain.model.*;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper to convert between Photo domain object and PhotoEntity.
 * This maintains the separation between domain and infrastructure layers.
 */
@Component
public class PhotoMapper {

    /**
     * Convert PhotoEntity to Photo domain object.
     */
    public Photo toDomain(PhotoEntity entity) {
        if (entity == null) {
            return null;
        }

        // Reconstruct value objects from entity fields
        PhotoId photoId = new PhotoId(entity.getId());
        UploadJobId jobId = new UploadJobId(entity.getJobId());
        UserId userId = new UserId(entity.getUserId());

        PhotoMetadata metadata = new PhotoMetadata(
            entity.getOriginalFilename() != null ? entity.getOriginalFilename() : entity.getFilename(),
            entity.getFileSizeBytes(),
            entity.getMimeType()
        );

        S3Location s3Location = new S3Location(
            entity.getS3Bucket(),
            entity.getS3Key()
        );

        // Use reflection to set private fields (since domain constructor is private)
        // Alternative: Add a reconstruction factory method in Photo domain class
        return reconstructPhoto(
            photoId,
            jobId,
            userId,
            metadata,
            s3Location,
            entity.getFilename(),
            PhotoStatus.valueOf(entity.getStatus()),
            entity.getUploadStartedAt(),
            entity.getUploadCompletedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Convert Photo domain object to PhotoEntity.
     * Sets ID - use this when reconstructing existing entities.
     */
    public PhotoEntity toEntity(Photo photo) {
        if (photo == null) {
            return null;
        }

        PhotoEntity entity = new PhotoEntity(
            photo.getJobId().value(),
            photo.getUserId().value(),
            photo.getFilename(),
            photo.getMetadata().originalFilename(),
            photo.getMetadata().fileSizeBytes(),
            photo.getMetadata().mimeType(),
            photo.getS3Location().bucket(),
            photo.getS3Location().key()
        );

        entity.setId(photo.getId().value());
        entity.setStatus(photo.getStatus().name());
        entity.setUploadStartedAt(photo.getUploadStartedAt());
        entity.setUploadCompletedAt(photo.getUploadCompletedAt());
        entity.setCreatedAt(photo.getCreatedAt());
        entity.setUpdatedAt(photo.getUpdatedAt());

        return entity;
    }

    /**
     * Convert Photo domain object to NEW PhotoEntity.
     * IMPORTANT: We must set the ID from the domain object because the S3 key
     * was generated using this ID. If we let JPA generate a new ID, there will
     * be a mismatch between the database ID and the S3 key photoId prefix.
     */
    public PhotoEntity toNewEntity(Photo photo) {
        if (photo == null) {
            return null;
        }

        PhotoEntity entity = new PhotoEntity(
            photo.getJobId().value(),
            photo.getUserId().value(),
            photo.getFilename(),
            photo.getMetadata().originalFilename(),
            photo.getMetadata().fileSizeBytes(),
            photo.getMetadata().mimeType(),
            photo.getS3Location().bucket(),
            photo.getS3Location().key()
        );

        // CRITICAL: Set the ID from domain object to match S3 key
        entity.setId(photo.getId().value());
        entity.setStatus(photo.getStatus().name());
        // Don't set timestamps - let @PrePersist handle them

        return entity;
    }

    /**
     * Update an existing PhotoEntity from a Photo domain object.
     * Used to preserve JPA-managed state during updates.
     */
    public void updateEntity(Photo photo, PhotoEntity entity) {
        entity.setStatus(photo.getStatus().name());
        entity.setUploadStartedAt(photo.getUploadStartedAt());
        entity.setUploadCompletedAt(photo.getUploadCompletedAt());
        entity.setUpdatedAt(photo.getUpdatedAt());
    }

    /**
     * Reconstruct Photo domain object with all fields.
     * This is a workaround since Photo constructor is private.
     *
     * TODO: Consider adding a static factory method in Photo domain class like:
     * Photo.reconstruct(PhotoId, UploadJobId, UserId, PhotoMetadata, S3Location,
     *                   String filename, PhotoStatus, timestamps...)
     */
    private Photo reconstructPhoto(
        PhotoId id,
        UploadJobId jobId,
        UserId userId,
        PhotoMetadata metadata,
        S3Location s3Location,
        String filename,
        PhotoStatus status,
        java.time.Instant uploadStartedAt,
        java.time.Instant uploadCompletedAt,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
    ) {
        try {
            // Use Photo.create() to get a valid instance, then update fields
            Photo photo = Photo.create(jobId, userId, metadata, s3Location.bucket());

            // Use reflection to set the ID, s3Location, and other fields
            var idField = Photo.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(photo, id);

            // CRITICAL: Set the correct s3Location from database (not the generated one)
            var s3LocationField = Photo.class.getDeclaredField("s3Location");
            s3LocationField.setAccessible(true);
            s3LocationField.set(photo, s3Location);

            // Also set filename to match the s3Location
            var filenameField = Photo.class.getDeclaredField("filename");
            filenameField.setAccessible(true);
            filenameField.set(photo, filename);

            var statusField = Photo.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(photo, status);

            if (uploadStartedAt != null) {
                var uploadStartedField = Photo.class.getDeclaredField("uploadStartedAt");
                uploadStartedField.setAccessible(true);
                uploadStartedField.set(photo, uploadStartedAt);
            }

            if (uploadCompletedAt != null) {
                var uploadCompletedField = Photo.class.getDeclaredField("uploadCompletedAt");
                uploadCompletedField.setAccessible(true);
                uploadCompletedField.set(photo, uploadCompletedAt);
            }

            var createdAtField = Photo.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(photo, createdAt);

            var updatedAtField = Photo.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(photo, updatedAt);

            return photo;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct Photo from entity", e);
        }
    }
}
