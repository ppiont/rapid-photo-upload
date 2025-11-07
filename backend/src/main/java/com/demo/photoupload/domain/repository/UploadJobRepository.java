package com.demo.photoupload.domain.repository;

import com.demo.photoupload.domain.model.UploadJob;
import com.demo.photoupload.domain.model.UploadJobId;
import com.demo.photoupload.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UploadJob aggregate.
 * This is a "port" in hexagonal architecture - defines what the domain needs.
 * Infrastructure layer provides the actual implementation ("adapter").
 */
public interface UploadJobRepository {

    /**
     * Save a new upload job or update an existing one.
     */
    UploadJob save(UploadJob uploadJob);

    /**
     * Find an upload job by its ID.
     */
    Optional<UploadJob> findById(UploadJobId id);

    /**
     * Find all upload jobs for a specific user, ordered by creation date (newest first).
     */
    List<UploadJob> findByUserId(UserId userId);

    /**
     * Find all upload jobs for a user with a specific status.
     */
    List<UploadJob> findByUserIdAndStatus(UserId userId, com.demo.photoupload.domain.model.UploadJobStatus status);

    /**
     * Check if a job exists.
     */
    boolean existsById(UploadJobId id);

    /**
     * Delete an upload job (cascade deletes photos).
     */
    void deleteById(UploadJobId id);
}
