package com.demo.photoupload.application.commands;

/**
 * Command to mark a photo as completed.
 * Transition: UPLOADING → COMPLETED
 * Also triggers UploadJob status update.
 */
public record CompletePhotoUploadCommand(
    String photoId
) {
}
