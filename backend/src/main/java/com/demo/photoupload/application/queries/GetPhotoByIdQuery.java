package com.demo.photoupload.application.queries;

/**
 * Query to get a single photo by ID with download URL.
 */
public record GetPhotoByIdQuery(
    String photoId
) {
}
