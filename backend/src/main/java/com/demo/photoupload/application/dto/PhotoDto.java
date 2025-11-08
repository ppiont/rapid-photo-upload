package com.demo.photoupload.application.dto;

import java.time.Instant;

/**
 * DTO for photo details with download URL.
 */
public record PhotoDto(
    String photoId,
    String filename,
    String originalFilename,
    long fileSizeBytes,
    String mimeType,
    String status,
    String downloadUrl,
    Instant createdAt,
    Instant uploadCompletedAt
) {
}
