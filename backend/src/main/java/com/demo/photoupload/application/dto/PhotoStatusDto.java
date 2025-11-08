package com.demo.photoupload.application.dto;

import java.time.Instant;

/**
 * DTO for photo status in job status queries.
 */
public record PhotoStatusDto(
    String photoId,
    String filename,
    String status,
    long fileSizeBytes,
    Instant createdAt,
    Instant uploadStartedAt,
    Instant uploadCompletedAt
) {
}
