package com.demo.photoupload.application.commands;

import java.util.List;

/**
 * Command to add tags to a photo.
 */
public record AddPhotoTagsCommand(
    String photoId,
    String userId,
    List<String> tags
) {
}
