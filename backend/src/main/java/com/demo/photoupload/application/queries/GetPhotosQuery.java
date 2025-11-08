package com.demo.photoupload.application.queries;

/**
 * Query to get paginated photos for a user with download URLs.
 */
public record GetPhotosQuery(
    String userId,
    int page,
    int size
) {
}
