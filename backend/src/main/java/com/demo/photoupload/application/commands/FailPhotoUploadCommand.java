package com.demo.photoupload.application.commands;

/**
 * Command to mark a photo as failed.
 * Transition: Any → FAILED
 * Also triggers UploadJob status update.
 */
public record FailPhotoUploadCommand(
    String photoId
) {
}
