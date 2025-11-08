package com.demo.photoupload.application.commands;

import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoTagEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handler for adding tags to a photo.
 *
 * Business Rules:
 * - Photo must exist and belong to the user
 * - Duplicate tags are ignored
 * - Tag names are trimmed and validated
 * - Empty or blank tags are rejected
 */
@Service
public class AddPhotoTagsHandler {
    private static final Logger log = LoggerFactory.getLogger(AddPhotoTagsHandler.class);
    private static final int MAX_TAG_LENGTH = 100;

    private final PhotoJpaRepository photoRepository;
    private final PhotoTagJpaRepository photoTagRepository;

    public AddPhotoTagsHandler(PhotoJpaRepository photoRepository,
                               PhotoTagJpaRepository photoTagRepository) {
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
    }

    @Transactional
    public void handle(AddPhotoTagsCommand command) {
        log.info("Adding tags to photo: photoId={}, userId={}, tags={}",
            command.photoId(), command.userId(), command.tags());

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

        // Find photo and verify ownership
        PhotoEntity photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + photoId));

        if (!photo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Photo does not belong to user");
        }

        // Get existing tags for this photo
        List<PhotoTagEntity> existingTags = photoTagRepository.findByPhotoId(photoId);
        List<String> existingTagNames = existingTags.stream()
            .map(PhotoTagEntity::getTagName)
            .toList();

        // Validate and filter tags
        List<String> validTags = new ArrayList<>();
        for (String tag : command.tags()) {
            String trimmedTag = tag.trim();

            // Skip empty or blank tags
            if (trimmedTag.isEmpty()) {
                log.warn("Skipping empty tag for photo: {}", photoId);
                continue;
            }

            // Validate tag length
            if (trimmedTag.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException(
                    String.format("Tag exceeds maximum length of %d characters: %s",
                        MAX_TAG_LENGTH, trimmedTag));
            }

            // Skip duplicate tags
            if (existingTagNames.contains(trimmedTag)) {
                log.debug("Skipping duplicate tag '{}' for photo: {}", trimmedTag, photoId);
                continue;
            }

            validTags.add(trimmedTag);
        }

        // Create new tag entities
        List<PhotoTagEntity> newTags = validTags.stream()
            .map(tagName -> new PhotoTagEntity(photoId, tagName))
            .toList();

        // Save all new tags
        if (!newTags.isEmpty()) {
            photoTagRepository.saveAll(newTags);
            log.info("Added {} new tags to photo: {}", newTags.size(), photoId);
        } else {
            log.info("No new tags to add for photo: {}", photoId);
        }
    }
}
