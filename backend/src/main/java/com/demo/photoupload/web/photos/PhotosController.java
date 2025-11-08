package com.demo.photoupload.web.photos;

import com.demo.photoupload.application.commands.AddPhotoTagsCommand;
import com.demo.photoupload.application.commands.AddPhotoTagsHandler;
import com.demo.photoupload.application.commands.DeletePhotoCommand;
import com.demo.photoupload.application.commands.DeletePhotoHandler;
import com.demo.photoupload.application.commands.RemovePhotoTagCommand;
import com.demo.photoupload.application.commands.RemovePhotoTagHandler;
import com.demo.photoupload.application.dto.PhotoDto;
import com.demo.photoupload.application.queries.GetPhotoByIdHandler;
import com.demo.photoupload.application.queries.GetPhotoByIdQuery;
import com.demo.photoupload.application.queries.GetPhotosHandler;
import com.demo.photoupload.application.queries.GetPhotosQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for photo gallery operations.
 * Implements vertical slice for photo viewing feature.
 */
@RestController
@RequestMapping("/api/photos")
@CrossOrigin(origins = "*") // TODO: Restrict in production
public class PhotosController {

    private static final Logger logger = LoggerFactory.getLogger(PhotosController.class);

    private final GetPhotosHandler getPhotosHandler;
    private final GetPhotoByIdHandler getPhotoByIdHandler;
    private final AddPhotoTagsHandler addPhotoTagsHandler;
    private final RemovePhotoTagHandler removePhotoTagHandler;
    private final DeletePhotoHandler deletePhotoHandler;

    public PhotosController(
        GetPhotosHandler getPhotosHandler,
        GetPhotoByIdHandler getPhotoByIdHandler,
        AddPhotoTagsHandler addPhotoTagsHandler,
        RemovePhotoTagHandler removePhotoTagHandler,
        DeletePhotoHandler deletePhotoHandler
    ) {
        this.getPhotosHandler = getPhotosHandler;
        this.getPhotoByIdHandler = getPhotoByIdHandler;
        this.addPhotoTagsHandler = addPhotoTagsHandler;
        this.removePhotoTagHandler = removePhotoTagHandler;
        this.deletePhotoHandler = deletePhotoHandler;
    }

    /**
     * Get paginated photos for a user.
     * Returns photos with pre-signed download URLs and pagination metadata.
     *
     * GET /api/photos?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<PhotoListResponse> getPhotos(
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        logger.info("Getting photos for user {}: page={}, size={}", userId, page, size);

        var result = getPhotosHandler.handle(new GetPhotosQuery(userId, page, size));

        // Calculate if there are more pages
        long totalPages = (result.totalCount() + size - 1) / size; // Ceiling division
        boolean hasMore = page < (totalPages - 1);

        PhotoListResponse response = new PhotoListResponse(
            result.photos(),
            hasMore,
            page,
            size,
            (int) result.totalCount()
        );

        logger.info("Returning {} photos for user {} (total={}, hasMore={})",
            result.photos().size(), userId, result.totalCount(), hasMore);

        return ResponseEntity.ok(response);
    }

    /**
     * Get a single photo by ID.
     * Returns photo details with pre-signed download URL.
     *
     * GET /api/photos/{photoId}
     */
    @GetMapping("/{photoId}")
    public ResponseEntity<PhotoDto> getPhotoById(@PathVariable String photoId) {
        logger.info("Getting photo: {}", photoId);

        PhotoDto photo = getPhotoByIdHandler.handle(new GetPhotoByIdQuery(photoId));

        return ResponseEntity.ok(photo);
    }

    /**
     * Add tags to a photo.
     * Tags are case-sensitive and duplicates are ignored.
     *
     * POST /api/photos/{photoId}/tags
     * Body: { "tags": ["vacation", "beach", "sunset"] }
     */
    @PostMapping("/{photoId}/tags")
    public ResponseEntity<Void> addTags(
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
        @PathVariable String photoId,
        @RequestBody AddTagsRequest request
    ) {
        logger.info("Adding tags to photo {}: tags={}", photoId, request.tags());

        addPhotoTagsHandler.handle(new AddPhotoTagsCommand(photoId, userId, request.tags()));

        logger.info("Successfully added tags to photo: {}", photoId);

        return ResponseEntity.ok().build();
    }

    /**
     * Remove a tag from a photo.
     * Idempotent - no error if tag doesn't exist.
     *
     * DELETE /api/photos/{photoId}/tags/{tagName}
     */
    @DeleteMapping("/{photoId}/tags/{tagName}")
    public ResponseEntity<Void> removeTag(
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
        @PathVariable String photoId,
        @PathVariable String tagName
    ) {
        logger.info("Removing tag from photo {}: tag={}", photoId, tagName);

        removePhotoTagHandler.handle(new RemovePhotoTagCommand(photoId, userId, tagName));

        logger.info("Successfully removed tag from photo: {}", photoId);

        return ResponseEntity.ok().build();
    }

    /**
     * Delete a photo.
     * Deletes both the S3 object and database metadata.
     *
     * DELETE /api/photos/{photoId}
     */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
        @PathVariable String photoId
    ) {
        logger.info("Deleting photo: photoId={}, userId={}", photoId, userId);

        deletePhotoHandler.handle(new DeletePhotoCommand(photoId, userId));

        logger.info("Successfully deleted photo: {}", photoId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Request DTO for adding tags.
     */
    public record AddTagsRequest(List<String> tags) {
    }
}
