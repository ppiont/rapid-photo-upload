package com.demo.photoupload.application.commands;

/**
 * Command to delete a photo.
 */
public record DeletePhotoCommand(
    String photoId,
    String userId
) {
}
