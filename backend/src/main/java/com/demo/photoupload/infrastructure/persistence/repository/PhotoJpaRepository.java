package com.demo.photoupload.infrastructure.persistence.repository;

import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PhotoEntity.
 */
@Repository
public interface PhotoJpaRepository extends JpaRepository<PhotoEntity, UUID> {

    /**
     * Find all photos for a specific upload job.
     */
    List<PhotoEntity> findByJobId(UUID jobId);

    /**
     * Find all photos for a specific user.
     */
    List<PhotoEntity> findByUserId(UUID userId);

    /**
     * Find photos for a user with pagination.
     */
    List<PhotoEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Count total photos for a user.
     */
    long countByUserId(UUID userId);

    /**
     * Find photos by user and status.
     */
    List<PhotoEntity> findByUserIdAndStatus(UUID userId, String status);

    /**
     * Check if photo exists by S3 key (for uniqueness check).
     */
    boolean existsByS3Key(String s3Key);
}
