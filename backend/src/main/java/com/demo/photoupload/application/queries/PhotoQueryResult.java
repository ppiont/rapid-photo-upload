package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.PhotoDto;

import java.util.List;

/**
 * Result object for GetPhotosHandler.
 * Contains photos and total count for pagination.
 */
public record PhotoQueryResult(
    List<PhotoDto> photos,
    long totalCount
) {
}
