package com.demo.photoupload.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for initializing a batch upload.
 */
public record InitializeUploadRequest(
    @NotNull(message = "Photos list cannot be null")
    @NotEmpty(message = "Photos list cannot be empty")
    @Size(min = 1, max = 500, message = "Can upload between 1 and 500 photos at once")
    @Valid
    List<PhotoMetadataRequest> photos
) {
}
