package com.demo.photoupload.application.commands;

import com.demo.photoupload.application.dto.InitializeUploadResponseDto;
import com.demo.photoupload.application.dto.PhotoUploadUrlDto;
import com.demo.photoupload.domain.model.*;
import com.demo.photoupload.domain.repository.UploadJobRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler for InitializeUploadCommand.
 * Creates UploadJob + Photos, generates pre-signed S3 URLs for client uploads.
 *
 * This is a critical path for high-volume upload flow (up to 500 photos per job).
 * Note: Photos are saved via UploadJobRepository.save() which cascades to photos.
 */
@Service
public class InitializeUploadHandler {

    private static final Logger logger = LoggerFactory.getLogger(InitializeUploadHandler.class);

    private final UploadJobRepository uploadJobRepository;
    private final S3Service s3Service;

    public InitializeUploadHandler(
        UploadJobRepository uploadJobRepository,
        S3Service s3Service
    ) {
        this.uploadJobRepository = uploadJobRepository;
        this.s3Service = s3Service;
    }

    /**
     * Handle upload initialization.
     * Creates job + photos in a single transaction, then generates pre-signed URLs.
     */
    @Transactional
    public InitializeUploadResponseDto handle(InitializeUploadCommand command) {
        logger.info("Initializing upload for user {} with {} photos",
            command.userId(), command.photos().size());

        // Convert DTOs to domain value objects
        UserId userId = new UserId(UUID.fromString(command.userId()));
        List<PhotoMetadata> photoMetadataList = command.photos().stream()
            .map(dto -> new PhotoMetadata(dto.filename(), dto.fileSizeBytes(), dto.mimeType()))
            .collect(Collectors.toList());

        // Create UploadJob aggregate with all photos
        String s3Bucket = s3Service.getBucketName();
        UploadJob uploadJob = UploadJob.create(userId, photoMetadataList, s3Bucket);

        // Save job and photos in batch (optimized for up to 500 photos)
        UploadJob savedJob = uploadJobRepository.save(uploadJob);
        logger.info("Created upload job {} with {} photos", savedJob.getId(), savedJob.getTotalPhotos());

        // Generate pre-signed upload URLs for each photo
        List<PhotoUploadUrlDto> photoUploadUrls = savedJob.getPhotos().stream()
            .map(photo -> {
                String uploadUrl = s3Service.generatePresignedUploadUrl(photo.getS3Location());
                return new PhotoUploadUrlDto(
                    photo.getId().value().toString(),
                    photo.getFilename(),
                    uploadUrl
                );
            })
            .collect(Collectors.toList());

        logger.info("Generated {} pre-signed upload URLs for job {}", photoUploadUrls.size(), savedJob.getId());

        return new InitializeUploadResponseDto(
            savedJob.getId().value().toString(),
            savedJob.getTotalPhotos(),
            photoUploadUrls
        );
    }
}
