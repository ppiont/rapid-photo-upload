package com.demo.photoupload.application.dto;

import java.util.List;

/**
 * Response DTO for upload initialization.
 * Contains job ID and list of photos with their upload URLs.
 */
public record InitializeUploadResponseDto(
    String jobId,
    int totalPhotos,
    List<PhotoUploadUrlDto> photos
) {
}
