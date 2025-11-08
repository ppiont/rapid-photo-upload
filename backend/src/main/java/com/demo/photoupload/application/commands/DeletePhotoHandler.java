package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handler for deleting a photo.
 *
 * Business Rules:
 * - Photo must exist and belong to the user
 * - Deletes photo from S3 bucket
 * - Deletes photo metadata from database
 * - Cascades delete to photo tags
 */
@Service
public class DeletePhotoHandler {
    private static final Logger log = LoggerFactory.getLogger(DeletePhotoHandler.class);

    private final PhotoJpaRepository photoJpaRepository;
    private final PhotoRepository photoRepository;
    private final PhotoTagJpaRepository photoTagRepository;
    private final S3Service s3Service;

    public DeletePhotoHandler(
        PhotoJpaRepository photoJpaRepository,
        PhotoRepository photoRepository,
        PhotoTagJpaRepository photoTagRepository,
        S3Service s3Service
    ) {
        this.photoJpaRepository = photoJpaRepository;
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
        this.s3Service = s3Service;
    }

    @Transactional
    public void handle(DeletePhotoCommand command) {
        log.info("Deleting photo: photoId={}, userId={}", command.photoId(), command.userId());

        // Validate and parse photo ID
        UUID photoIdUuid;
        try {
            photoIdUuid = UUID.fromString(command.photoId());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid photo ID format: " + command.photoId());
        }

        // Validate and parse user ID
        UUID userId;
        try {
            userId = UUID.fromString(command.userId());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format: " + command.userId());
        }

        // Find photo and verify ownership
        PhotoEntity photo = photoJpaRepository.findById(photoIdUuid)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoIdUuid));

        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }

        // Delete from S3 if photo has been uploaded
        if (photo.getS3Key() != null && !photo.getS3Key().isEmpty()) {
            try {
                s3Service.deleteObject(photo.getS3Key());
                log.info("Deleted photo from S3: {}", photo.getS3Key());
            } catch (Exception e) {
                log.error("Failed to delete photo from S3: {}", photo.getS3Key(), e);
                // Continue with database deletion even if S3 deletion fails
            }
        }

        // Delete photo tags (cascade)
        photoTagRepository.deleteByPhotoId(photoIdUuid);
        log.info("Deleted tags for photo: {}", photoIdUuid);

        // Delete photo from database using domain repository
        photoRepository.deleteById(new PhotoId(photoIdUuid));

        log.info("Successfully deleted photo: {}", photoIdUuid);
    }
}
