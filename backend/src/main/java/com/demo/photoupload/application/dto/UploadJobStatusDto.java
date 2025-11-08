package com.demo.photoupload.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for upload job status (used for polling).
 */
public record UploadJobStatusDto(
    String jobId,
    String userId,
    String status,
    int totalPhotos,
    int completedPhotos,
    int failedPhotos,
    int pendingPhotos,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    List<PhotoStatusDto> photos
) {
}
