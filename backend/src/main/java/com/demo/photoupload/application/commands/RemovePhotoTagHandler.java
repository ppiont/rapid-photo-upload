package com.demo.photoupload.application.commands;

import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handler for removing a tag from a photo.
 *
 * Business Rules:
 * - Photo must exist and belong to the user
 * - Tag name is case-sensitive
 * - No error if tag doesn't exist (idempotent)
 */
@Service
public class RemovePhotoTagHandler {
    private static final Logger log = LoggerFactory.getLogger(RemovePhotoTagHandler.class);

    private final PhotoJpaRepository photoRepository;
    private final PhotoTagJpaRepository photoTagRepository;

    public RemovePhotoTagHandler(PhotoJpaRepository photoRepository,
                                 PhotoTagJpaRepository photoTagRepository) {
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
    }

    @Transactional
    public void handle(RemovePhotoTagCommand command) {
        log.info("Removing tag from photo: photoId={}, userId={}, tag={}",
            command.photoId(), command.userId(), command.tagName());

        // Validate and parse photo ID
        UUID photoId;
        try {
            photoId = UUID.fromString(command.photoId());
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

        // Validate tag name
        if (command.tagName() == null || command.tagName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }

        // Find photo and verify ownership
        PhotoEntity photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));

        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }

        // Delete the tag (idempotent - no error if tag doesn't exist)
        String tagName = command.tagName().trim();
        photoTagRepository.deleteByPhotoIdAndTagName(photoId, tagName);

        log.info("Removed tag '{}' from photo: {}", tagName, photoId);
    }
}
