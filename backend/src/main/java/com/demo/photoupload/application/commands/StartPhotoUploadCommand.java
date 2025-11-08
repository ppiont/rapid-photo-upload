package com.demo.photoupload.application.commands;

/**
 * Command to mark a photo as started uploading.
 * Transition: PENDING → UPLOADING
 */
public record StartPhotoUploadCommand(
    String photoId
) {
}
