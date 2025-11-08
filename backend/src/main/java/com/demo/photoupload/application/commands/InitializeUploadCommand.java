package com.demo.photoupload.application.commands;

import com.demo.photoupload.application.dto.PhotoMetadataDto;

import java.util.List;

/**
 * Command to initialize a batch photo upload.
 * Creates UploadJob and Photo entities, generates pre-signed S3 URLs.
 */
public record InitializeUploadCommand(
    String userId,
    List<PhotoMetadataDto> photos
) {
}
