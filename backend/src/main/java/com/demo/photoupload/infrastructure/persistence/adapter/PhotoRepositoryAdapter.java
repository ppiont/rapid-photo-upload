package com.demo.photoupload.infrastructure.persistence.adapter;

import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.model.UploadJobId;
import com.demo.photoupload.domain.model.UserId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.mapper.PhotoMapper;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter that implements PhotoRepository (domain port) using JPA.
 * This is the "adapter" in hexagonal architecture.
 */
@Repository
public class PhotoRepositoryAdapter implements PhotoRepository {

    private final PhotoJpaRepository jpaRepository;
    private final PhotoMapper mapper;

    public PhotoRepositoryAdapter(PhotoJpaRepository jpaRepository, PhotoMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Photo save(Photo photo) {
        // Check if entity exists to decide between insert and update
        Optional<PhotoEntity> existing = jpaRepository.findById(photo.getId().value());

        PhotoEntity entity;
        if (existing.isPresent()) {
            // Update existing entity to preserve JPA-managed state
            entity = existing.get();
            mapper.updateEntity(photo, entity);
        } else {
            // Create new entity
            entity = mapper.toEntity(photo);
        }

        PhotoEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<Photo> saveAll(List<Photo> photos) {
        List<PhotoEntity> entities = photos.stream()
            .map(mapper::toEntity)
            .collect(Collectors.toList());

        List<PhotoEntity> saved = jpaRepository.saveAll(entities);

        return saved.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Photo> findById(PhotoId id) {
        return jpaRepository.findById(id.value())
            .map(mapper::toDomain);
    }

    @Override
    public List<Photo> findByJobId(UploadJobId jobId) {
        return jpaRepository.findByJobId(jobId.value()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Photo> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.value()).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Photo> findByUserIdWithPagination(UserId userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value(), pageRequest).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countByUserId(UserId userId) {
        return jpaRepository.countByUserId(userId.value());
    }

    @Override
    public boolean existsById(PhotoId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public void deleteById(PhotoId id) {
        jpaRepository.deleteById(id.value());
    }
}
