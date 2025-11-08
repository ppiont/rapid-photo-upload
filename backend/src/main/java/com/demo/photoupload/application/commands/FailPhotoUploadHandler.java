package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.model.UploadJob;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.domain.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handler for FailPhotoUploadCommand.
 * Marks photo as FAILED and updates parent UploadJob status.
 */
@Service
public class FailPhotoUploadHandler {

    private static final Logger logger = LoggerFactory.getLogger(FailPhotoUploadHandler.class);

    private final PhotoRepository photoRepository;
    private final UploadJobRepository uploadJobRepository;

    public FailPhotoUploadHandler(
        PhotoRepository photoRepository,
        UploadJobRepository uploadJobRepository
    ) {
        this.photoRepository = photoRepository;
        this.uploadJobRepository = uploadJobRepository;
    }

    @Transactional
    public void handle(FailPhotoUploadCommand command) {
        PhotoId photoId = new PhotoId(UUID.fromString(command.photoId()));

        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + command.photoId()));

        // Mark photo as failed
        photo.markAsFailed();
        photoRepository.save(photo);

        logger.warn("Photo {} marked as FAILED", command.photoId());

        // Update parent job status
        UploadJob uploadJob = uploadJobRepository.findById(photo.getJobId())
            .orElseThrow(() -> new IllegalArgumentException("Upload job not found: " + photo.getJobId()));

        uploadJob.checkAndUpdateStatus();
        uploadJobRepository.save(uploadJob);

        logger.info("Upload job {} updated: status={}, failed={}/{}",
            uploadJob.getId(), uploadJob.getStatus(),
            uploadJob.getFailedPhotos(), uploadJob.getTotalPhotos());
    }
}
