package com.demo.photoupload.application.dto;

/**
 * DTO containing photo ID, filename, and pre-signed upload URL.
 * Returned to client after upload initialization.
 */
public record PhotoUploadUrlDto(
    String photoId,
    String filename,
    String uploadUrl
) {
}
