package com.demo.photoupload.web.photos;

import com.demo.photoupload.application.dto.PhotoDto;

import java.util.List;

/**
 * Response wrapper for paginated photo lists.
 * Includes metadata to support proper infinite scrolling.
 */
public record PhotoListResponse(
    List<PhotoDto> photos,
    boolean hasMore,
    int page,
    int size,
    int totalElements
) {
}
