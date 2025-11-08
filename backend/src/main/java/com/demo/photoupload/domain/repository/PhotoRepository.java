package com.demo.photoupload.domain.repository;

import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.model.UploadJobId;
import com.demo.photoupload.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Photo aggregate.
 * This is a "port" in hexagonal architecture.
 */
public interface PhotoRepository {

    /**
     * Save a new photo or update an existing one.
     */
    Photo save(Photo photo);

    /**
     * Save multiple photos in a batch (optimized for up to 500 photos).
     */
    List<Photo> saveAll(List<Photo> photos);

    /**
     * Find a photo by its ID.
     */
    Optional<Photo> findById(PhotoId id);

    /**
     * Find all photos for a specific upload job.
     */
    List<Photo> findByJobId(UploadJobId jobId);

    /**
     * Find all photos for a specific user.
     */
    List<Photo> findByUserId(UserId userId);

    /**
     * Find photos by user with pagination support.
     */
    List<Photo> findByUserIdWithPagination(UserId userId, int page, int size);

    /**
     * Count total photos for a user.
     */
    long countByUserId(UserId userId);

    /**
     * Check if a photo exists.
     */
    boolean existsById(PhotoId id);

    /**
     * Delete a photo.
     */
    void deleteById(PhotoId id);
}
