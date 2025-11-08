package com.demo.photoupload.infrastructure.persistence.repository;

import com.demo.photoupload.infrastructure.persistence.entity.PhotoTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PhotoTagEntity.
 */
@Repository
public interface PhotoTagJpaRepository extends JpaRepository<PhotoTagEntity, UUID> {

    /**
     * Find all tags for a specific photo.
     */
    List<PhotoTagEntity> findByPhotoId(UUID photoId);

    /**
     * Find all photos with a specific tag.
     */
    List<PhotoTagEntity> findByTagName(String tagName);

    /**
     * Delete all tags for a photo.
     */
    void deleteByPhotoId(UUID photoId);

    /**
     * Delete a specific tag from a photo.
     */
    void deleteByPhotoIdAndTagName(UUID photoId, String tagName);
}
