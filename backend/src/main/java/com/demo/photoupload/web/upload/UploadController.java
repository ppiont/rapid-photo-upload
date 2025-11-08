package com.demo.photoupload.web.upload;

import com.demo.photoupload.application.commands.*;
import com.demo.photoupload.application.dto.InitializeUploadResponseDto;
import com.demo.photoupload.application.dto.PhotoMetadataDto;
import com.demo.photoupload.application.dto.UploadJobStatusDto;
import com.demo.photoupload.application.queries.GetUploadJobStatusHandler;
import com.demo.photoupload.application.queries.GetUploadJobStatusQuery;
import com.demo.photoupload.web.dto.InitializeUploadRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * REST controller for photo upload operations.
 * Implements vertical slice for upload feature.
 */
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*") // TODO: Restrict in production
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private final InitializeUploadHandler initializeUploadHandler;
    private final StartPhotoUploadHandler startPhotoUploadHandler;
    private final CompletePhotoUploadHandler completePhotoUploadHandler;
    private final FailPhotoUploadHandler failPhotoUploadHandler;
    private final GetUploadJobStatusHandler getUploadJobStatusHandler;

    public UploadController(
        InitializeUploadHandler initializeUploadHandler,
        StartPhotoUploadHandler startPhotoUploadHandler,
        CompletePhotoUploadHandler completePhotoUploadHandler,
        FailPhotoUploadHandler failPhotoUploadHandler,
        GetUploadJobStatusHandler getUploadJobStatusHandler
    ) {
        this.initializeUploadHandler = initializeUploadHandler;
        this.startPhotoUploadHandler = startPhotoUploadHandler;
        this.completePhotoUploadHandler = completePhotoUploadHandler;
        this.failPhotoUploadHandler = failPhotoUploadHandler;
        this.getUploadJobStatusHandler = getUploadJobStatusHandler;
    }

    /**
     * Initialize a batch upload.
     * Creates UploadJob + Photos, returns pre-signed S3 upload URLs.
     *
     * POST /api/upload/initialize
     */
    @PostMapping("/initialize")
    public ResponseEntity<InitializeUploadResponseDto> initializeUpload(
        @Valid @RequestBody InitializeUploadRequest request,
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId
    ) {
        logger.info("Initializing upload for user {} with {} photos", userId, request.photos().size());

        var command = new InitializeUploadCommand(
            userId,
            request.photos().stream()
                .map(p -> new PhotoMetadataDto(p.filename(), p.fileSizeBytes(), p.mimeType()))
                .collect(Collectors.toList())
        );

        InitializeUploadResponseDto response = initializeUploadHandler.handle(command);

        logger.info("Upload initialized: jobId={}, photos={}", response.jobId(), response.totalPhotos());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Mark a photo as started uploading.
     * Transition: PENDING → UPLOADING
     *
     * PUT /api/upload/photos/{photoId}/start
     */
    @PutMapping("/photos/{photoId}/start")
    public ResponseEntity<Void> startPhotoUpload(@PathVariable String photoId) {
        logger.info("Starting photo upload: {}", photoId);

        startPhotoUploadHandler.handle(new StartPhotoUploadCommand(photoId));

        return ResponseEntity.ok().build();
    }

    /**
     * Mark a photo as completed.
     * Transition: UPLOADING → COMPLETED
     * Also updates parent UploadJob status.
     *
     * PUT /api/upload/photos/{photoId}/complete
     */
    @PutMapping("/photos/{photoId}/complete")
    public ResponseEntity<Void> completePhotoUpload(@PathVariable String photoId) {
        logger.info("Completing photo upload: {}", photoId);

        completePhotoUploadHandler.handle(new CompletePhotoUploadCommand(photoId));

        return ResponseEntity.ok().build();
    }

    /**
     * Mark a photo as failed.
     * Transition: Any → FAILED
     * Also updates parent UploadJob status.
     *
     * PUT /api/upload/photos/{photoId}/fail
     */
    @PutMapping("/photos/{photoId}/fail")
    public ResponseEntity<Void> failPhotoUpload(@PathVariable String photoId) {
        logger.warn("Marking photo as failed: {}", photoId);

        failPhotoUploadHandler.handle(new FailPhotoUploadCommand(photoId));

        return ResponseEntity.ok().build();
    }

    /**
     * Get upload job status (for polling).
     * Returns job status with all photo statuses.
     *
     * GET /api/upload/jobs/{jobId}/status
     */
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<UploadJobStatusDto> getUploadJobStatus(@PathVariable String jobId) {
        logger.debug("Getting upload job status: {}", jobId);

        UploadJobStatusDto status = getUploadJobStatusHandler.handle(new GetUploadJobStatusQuery(jobId));

        return ResponseEntity.ok(status);
    }
}
