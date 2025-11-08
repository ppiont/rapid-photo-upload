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
 * Handler for CompletePhotoUploadCommand.
 * Marks photo as COMPLETED and updates parent UploadJob status.
 */
@Service
public class CompletePhotoUploadHandler {

    private static final Logger logger = LoggerFactory.getLogger(CompletePhotoUploadHandler.class);

    private final PhotoRepository photoRepository;
    private final UploadJobRepository uploadJobRepository;

    public CompletePhotoUploadHandler(
        PhotoRepository photoRepository,
        UploadJobRepository uploadJobRepository
    ) {
        this.photoRepository = photoRepository;
        this.uploadJobRepository = uploadJobRepository;
    }

    @Transactional
    public void handle(CompletePhotoUploadCommand command) {
        PhotoId photoId = new PhotoId(UUID.fromString(command.photoId()));

        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + command.photoId()));

        // Mark photo as completed
        photo.markAsCompleted();
        photoRepository.save(photo);

        logger.info("Photo {} marked as COMPLETED", command.photoId());

        // Update parent job status
        UploadJob uploadJob = uploadJobRepository.findById(photo.getJobId())
            .orElseThrow(() -> new IllegalArgumentException("Upload job not found: " + photo.getJobId()));

        uploadJob.checkAndUpdateStatus();
        uploadJobRepository.save(uploadJob);

        logger.info("Upload job {} updated: status={}, completed={}/{}",
            uploadJob.getId(), uploadJob.getStatus(),
            uploadJob.getCompletedPhotos(), uploadJob.getTotalPhotos());
    }
}
