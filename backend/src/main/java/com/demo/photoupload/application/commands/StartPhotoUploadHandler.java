package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handler for StartPhotoUploadCommand.
 * Marks photo as UPLOADING (called before client uploads to S3).
 */
@Service
public class StartPhotoUploadHandler {

    private static final Logger logger = LoggerFactory.getLogger(StartPhotoUploadHandler.class);

    private final PhotoRepository photoRepository;

    public StartPhotoUploadHandler(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    @Transactional
    public void handle(StartPhotoUploadCommand command) {
        PhotoId photoId = new PhotoId(UUID.fromString(command.photoId()));

        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + command.photoId()));

        photo.markAsStarted();
        photoRepository.save(photo);

        logger.info("Photo {} marked as UPLOADING", command.photoId());
    }
}
