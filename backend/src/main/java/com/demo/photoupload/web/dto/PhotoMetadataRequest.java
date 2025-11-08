package com.demo.photoupload.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for photo metadata in upload initialization.
 */
public record PhotoMetadataRequest(
    @NotBlank(message = "Filename cannot be blank")
    @Size(max = 500, message = "Filename too long")
    String filename,

    @Min(value = 1, message = "File size must be positive")
    long fileSizeBytes,

    @NotBlank(message = "MIME type cannot be blank")
    @Pattern(regexp = "image/(jpeg|jpg|png|gif|webp|heic|heif)", message = "Invalid image MIME type")
    String mimeType
) {
}
