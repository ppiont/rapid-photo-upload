package com.demo.photoupload.application.dto;

/**
 * DTO for photo metadata used in upload initialization.
 */
public record PhotoMetadataDto(
    String filename,
    long fileSizeBytes,
    String mimeType
) {
}
