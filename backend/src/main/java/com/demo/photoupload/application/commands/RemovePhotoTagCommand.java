package com.demo.photoupload.application.commands;

/**
 * Command to remove a tag from a photo.
 */
public record RemovePhotoTagCommand(
    String photoId,
    String userId,
    String tagName
) {
}
