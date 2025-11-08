package com.demo.photoupload.infrastructure.persistence.adapter;

import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.UploadJob;
import com.demo.photoupload.domain.model.UploadJobId;
import com.demo.photoupload.domain.model.UploadJobStatus;
import com.demo.photoupload.domain.model.UserId;
import com.demo.photoupload.domain.repository.UploadJobRepository;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.entity.UploadJobEntity;
import com.demo.photoupload.infrastructure.persistence.mapper.PhotoMapper;
import com.demo.photoupload.infrastructure.persistence.mapper.UploadJobMapper;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.UploadJobJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter that implements UploadJobRepository (domain port) using JPA.
 * This is the "adapter" in hexagonal architecture.
 */
@Repository
public class UploadJobRepositoryAdapter implements UploadJobRepository {

    private final UploadJobJpaRepository jpaRepository;
    private final PhotoJpaRepository photoJpaRepository;
    private final UploadJobMapper jobMapper;
    private final PhotoMapper photoMapper;

    public UploadJobRepositoryAdapter(
        UploadJobJpaRepository jpaRepository,
        PhotoJpaRepository photoJpaRepository,
        UploadJobMapper jobMapper,
        PhotoMapper photoMapper
    ) {
        this.jpaRepository = jpaRepository;
        this.photoJpaRepository = photoJpaRepository;
        this.jobMapper = jobMapper;
        this.photoMapper = photoMapper;
    }

    @Override
    public UploadJob save(UploadJob uploadJob) {
        // Save the job entity first
        Optional<UploadJobEntity> existing = jpaRepository.findById(uploadJob.getId().value());

        UploadJobEntity jobEntity;
        if (existing.isPresent()) {
            // Update existing entity to preserve JPA-managed state
            jobEntity = existing.get();
            jobMapper.updateEntity(uploadJob, jobEntity);
        } else {
            // Create new entity
            jobEntity = jobMapper.toEntity(uploadJob);
        }

        UploadJobEntity savedJob = jpaRepository.save(jobEntity);

        // Save all photos that belong to this job
        List<Photo> photos = uploadJob.getPhotos();
        if (!photos.isEmpty()) {
            List<PhotoEntity> photoEntities = photos.stream()
                .map(photoMapper::toEntity)
                .collect(Collectors.toList());
            photoJpaRepository.saveAll(photoEntities);
        }

        // Reconstruct domain object
        UploadJob result = jobMapper.toDomain(savedJob);

        // Add photos back to the reconstructed job
        List<Photo> savedPhotos = photoJpaRepository.findByJobId(savedJob.getId()).stream()
            .map(photoMapper::toDomain)
            .collect(Collectors.toList());

        for (Photo photo : savedPhotos) {
            result.addPhoto(photo);
        }

        return result;
    }

    @Override
    public Optional<UploadJob> findById(UploadJobId id) {
        Optional<UploadJobEntity> jobEntity = jpaRepository.findById(id.value());

        return jobEntity.map(entity -> {
            UploadJob uploadJob = jobMapper.toDomain(entity);

            // Load photos for this job
            List<Photo> photos = photoJpaRepository.findByJobId(entity.getId()).stream()
                .map(photoMapper::toDomain)
                .collect(Collectors.toList());

            // Add photos to the job
            for (Photo photo : photos) {
                uploadJob.addPhoto(photo);
            }

            return uploadJob;
        });
    }

    @Override
    public List<UploadJob> findByUserId(UserId userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId.value()).stream()
            .map(entity -> {
                UploadJob uploadJob = jobMapper.toDomain(entity);

                // Load photos for each job
                List<Photo> photos = photoJpaRepository.findByJobId(entity.getId()).stream()
                    .map(photoMapper::toDomain)
                    .collect(Collectors.toList());

                for (Photo photo : photos) {
                    uploadJob.addPhoto(photo);
                }

                return uploadJob;
            })
            .collect(Collectors.toList());
    }

    @Override
    public List<UploadJob> findByUserIdAndStatus(UserId userId, UploadJobStatus status) {
        return jpaRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId.value(), status.name()).stream()
            .map(entity -> {
                UploadJob uploadJob = jobMapper.toDomain(entity);

                // Load photos for each job
                List<Photo> photos = photoJpaRepository.findByJobId(entity.getId()).stream()
                    .map(photoMapper::toDomain)
                    .collect(Collectors.toList());

                for (Photo photo : photos) {
                    uploadJob.addPhoto(photo);
                }

                return uploadJob;
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(UploadJobId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public void deleteById(UploadJobId id) {
        jpaRepository.deleteById(id.value());
    }
}
