package com.demo.photoupload.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for photo details with download URL and tags.
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
    Instant uploadCompletedAt,
    List<String> tags
) {
}
