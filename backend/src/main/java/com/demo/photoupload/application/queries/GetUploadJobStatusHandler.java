package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.PhotoStatusDto;
import com.demo.photoupload.application.dto.UploadJobStatusDto;
import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.UploadJob;
import com.demo.photoupload.domain.model.UploadJobId;
import com.demo.photoupload.domain.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler for GetUploadJobStatusQuery.
 * Returns job status with all photos (for client polling).
 */
@Service
public class GetUploadJobStatusHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetUploadJobStatusHandler.class);

    private final UploadJobRepository uploadJobRepository;

    public GetUploadJobStatusHandler(UploadJobRepository uploadJobRepository) {
        this.uploadJobRepository = uploadJobRepository;
    }

    @Transactional(readOnly = true)
    public UploadJobStatusDto handle(GetUploadJobStatusQuery query) {
        UploadJobId jobId = new UploadJobId(UUID.fromString(query.jobId()));

        UploadJob uploadJob = uploadJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Upload job not found: " + query.jobId()));

        // Convert photos to DTOs
        List<PhotoStatusDto> photoDtos = uploadJob.getPhotos().stream()
            .map(this::toPhotoStatusDto)
            .collect(Collectors.toList());

        logger.debug("Retrieved upload job status: {} (status={}, {}/{} completed)",
            query.jobId(), uploadJob.getStatus(),
            uploadJob.getCompletedPhotos(), uploadJob.getTotalPhotos());

        return new UploadJobStatusDto(
            uploadJob.getId().value().toString(),
            uploadJob.getUserId().value().toString(),
            uploadJob.getStatus().name(),
            uploadJob.getTotalPhotos(),
            uploadJob.getCompletedPhotos(),
            uploadJob.getFailedPhotos(),
            uploadJob.getPendingPhotos(),
            uploadJob.getCreatedAt(),
            uploadJob.getUpdatedAt(),
            uploadJob.getCompletedAt(),
            photoDtos
        );
    }

    private PhotoStatusDto toPhotoStatusDto(Photo photo) {
        return new PhotoStatusDto(
            photo.getId().value().toString(),
            photo.getFilename(),
            photo.getStatus().name(),
            photo.getMetadata().fileSizeBytes(),
            photo.getCreatedAt(),
            photo.getUploadStartedAt(),
            photo.getUploadCompletedAt()
        );
    }
}
