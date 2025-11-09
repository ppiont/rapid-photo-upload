package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.PhotoDto;
import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.UserId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoTagEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler for GetPhotosQuery.
 * Returns paginated photo list with pre-signed download URLs, tags, and total count.
 */
@Service
public class GetPhotosHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetPhotosHandler.class);

    private final PhotoRepository photoRepository;
    private final PhotoTagJpaRepository photoTagRepository;
    private final S3Service s3Service;

    public GetPhotosHandler(PhotoRepository photoRepository,
                           PhotoTagJpaRepository photoTagRepository,
                           S3Service s3Service) {
        this.photoRepository = photoRepository;
        this.photoTagRepository = photoTagRepository;
        this.s3Service = s3Service;
    }

    @Transactional(readOnly = true)
    public PhotoQueryResult handle(GetPhotosQuery query) {
        UserId userId = new UserId(UUID.fromString(query.userId()));

        List<Photo> photos = photoRepository.findByUserIdWithPagination(
            userId,
            query.page(),
            query.size()
        );

        long totalCount = photoRepository.countByUserId(userId);

        logger.debug("Retrieved {} photos for user {} (page={}, size={}, total={})",
            photos.size(), query.userId(), query.page(), query.size(), totalCount);

        // Fetch all tags for these photos in one query
        List<UUID> photoIds = photos.stream()
            .map(photo -> photo.getId().value())
            .collect(Collectors.toList());

        Map<UUID, List<String>> photoTagsMap = photoTagRepository.findByPhotoIdIn(photoIds).stream()
            .collect(Collectors.groupingBy(
                PhotoTagEntity::getPhotoId,
                Collectors.mapping(PhotoTagEntity::getTagName, Collectors.toList())
            ));

        List<PhotoDto> photoDtos = photos.stream()
            .map(photo -> toPhotoDto(photo, photoTagsMap.getOrDefault(photo.getId().value(), List.of())))
            .collect(Collectors.toList());

        return new PhotoQueryResult(photoDtos, totalCount);
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
