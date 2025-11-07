package com.demo.photoupload.domain.model;

import java.util.Objects;

/**
 * Value Object representing photo file metadata.
 * Contains immutable information about the photo file.
 */
public record PhotoMetadata(
    String originalFilename,
    long fileSizeBytes,
    String mimeType
) {

    public PhotoMetadata {
        Objects.requireNonNull(originalFilename, "Original filename cannot be null");
        Objects.requireNonNull(mimeType, "MIME type cannot be null");

        if (originalFilename.isBlank()) {
            throw new IllegalArgumentException("Original filename cannot be blank");
        }

        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }

        if (mimeType.isBlank()) {
            throw new IllegalArgumentException("MIME type cannot be blank");
        }

        if (!isValidImageMimeType(mimeType)) {
            throw new IllegalArgumentException("Invalid image MIME type: " + mimeType);
        }
    }

    private static boolean isValidImageMimeType(String mimeType) {
        return mimeType.equals("image/jpeg") ||
               mimeType.equals("image/png") ||
               mimeType.equals("image/gif") ||
               mimeType.equals("image/webp") ||
               mimeType.equals("image/heic");
    }

    /**
     * Generate a unique S3-safe filename from the original filename.
     * Format: {uuid}-{sanitized-original-name}
     */
    public String generateS3Filename(PhotoId photoId) {
        String sanitized = originalFilename
            .replaceAll("[^a-zA-Z0-9._-]", "_")
            .toLowerCase();
        return photoId.value() + "-" + sanitized;
    }
}
