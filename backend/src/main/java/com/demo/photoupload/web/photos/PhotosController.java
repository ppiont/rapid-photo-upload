package com.demo.photoupload.web.photos;

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

    public PhotosController(
        GetPhotosHandler getPhotosHandler,
        GetPhotoByIdHandler getPhotoByIdHandler
    ) {
        this.getPhotosHandler = getPhotosHandler;
        this.getPhotoByIdHandler = getPhotoByIdHandler;
    }

    /**
     * Get paginated photos for a user.
     * Returns photos with pre-signed download URLs.
     *
     * GET /api/photos?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<List<PhotoDto>> getPhotos(
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        logger.info("Getting photos for user {}: page={}, size={}", userId, page, size);

        List<PhotoDto> photos = getPhotosHandler.handle(new GetPhotosQuery(userId, page, size));

        logger.info("Returning {} photos for user {}", photos.size(), userId);

        return ResponseEntity.ok(photos);
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
}
