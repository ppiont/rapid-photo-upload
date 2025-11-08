package com.demo.photoupload.infrastructure.persistence.repository;

import com.demo.photoupload.infrastructure.persistence.entity.UploadJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for UploadJobEntity.
 */
@Repository
public interface UploadJobJpaRepository extends JpaRepository<UploadJobEntity, UUID> {

    /**
     * Find all upload jobs for a user, ordered by creation date (newest first).
     */
    List<UploadJobEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find all upload jobs for a user with a specific status.
     */
    List<UploadJobEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    /**
     * Find upload job with photos eagerly loaded (to avoid N+1 query problem).
     */
    @Query("SELECT j FROM UploadJobEntity j LEFT JOIN FETCH j.photos WHERE j.id = :jobId")
    UploadJobEntity findByIdWithPhotos(@Param("jobId") UUID jobId);
}
