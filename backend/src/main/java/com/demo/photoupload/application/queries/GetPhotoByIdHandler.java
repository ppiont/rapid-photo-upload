package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.PhotoDto;
import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler for GetPhotoByIdQuery.
 * Returns single photo details with pre-signed download URL and tags.
 */
@Service
public class GetPhotoByIdHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetPhotoByIdHandler.class);

    private final PhotoRepository photoRepository;
    private final PhotoTagJpaRepository photoTagRepository;
    private final S3Service s3Service;

    public GetPhotoByIdHandler(PhotoRepository photoRepository,
                              PhotoTagJpaRepository photoTagRepository,
                              S3Service s3Service) {
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
        this.s3Service = s3Service;
    }

    @Transactional(readOnly = true)
    public PhotoDto handle(GetPhotoByIdQuery query) {
        PhotoId photoId = new PhotoId(UUID.fromString(query.photoId()));

        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("Photo not found: " + query.photoId()));

        logger.debug("Retrieved photo: {}", query.photoId());

        // Fetch tags for this photo
        List<String> tags = photoTagRepository.findByPhotoId(photo.getId().value()).stream()
            .map(tag -> tag.getTagName())
            .collect(Collectors.toList());

        return toPhotoDto(photo, tags);
    }

    private PhotoDto toPhotoDto(Photo photo, List<String> tags) {
        // Generate pre-signed download URL (1 hour expiry)
        String downloadUrl = null;
        if (photo.isCompleted()) {
            downloadUrl = s3Service.generatePresignedDownloadUrl(photo.getS3Location());
        }

        return new PhotoDto(
            photo.getId().value().toString(),
            photo.getFilename(),
            photo.getMetadata().originalFilename(),
            photo.getMetadata().fileSizeBytes(),
            photo.getMetadata().mimeType(),
            photo.getStatus().name(),
            downloadUrl,
            photo.getCreatedAt(),
            photo.getUploadCompletedAt(),
            tags
        );
    }
}
