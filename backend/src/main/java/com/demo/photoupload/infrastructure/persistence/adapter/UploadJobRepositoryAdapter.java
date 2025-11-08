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
import java.util.UUID;
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
        boolean isNew = !jpaRepository.existsById(uploadJob.getId().value());

        UploadJobEntity savedJobEntity;

        if (isNew) {
            // Create new job entity (without ID - let JPA generate it)
            UploadJobEntity jobEntity = jobMapper.toNewEntity(uploadJob);
            savedJobEntity = jpaRepository.save(jobEntity);

            // Save new photos (without IDs - let JPA generate them)
            List<Photo> photos = uploadJob.getPhotos();
            if (!photos.isEmpty()) {
                UUID jobId = savedJobEntity.getId();
                List<PhotoEntity> photoEntities = photos.stream()
                    .map(photo -> {
                        PhotoEntity entity = photoMapper.toNewEntity(photo);
                        entity.setJobId(jobId); // Ensure FK is set
                        return entity;
                    })
                    .collect(Collectors.toList());
                photoJpaRepository.saveAll(photoEntities);
            }
        } else {
            // Update existing job entity
            UploadJobEntity jobEntity = jpaRepository.findById(uploadJob.getId().value()).orElseThrow();
            jobMapper.updateEntity(uploadJob, jobEntity);
            savedJobEntity = jpaRepository.save(jobEntity);

            // Update existing photos
            List<Photo> photos = uploadJob.getPhotos();
            for (Photo photo : photos) {
                Optional<PhotoEntity> existingPhoto = photoJpaRepository.findById(photo.getId().value());
                if (existingPhoto.isPresent()) {
                    photoMapper.updateEntity(photo, existingPhoto.get());
                    photoJpaRepository.save(existingPhoto.get());
                } else {
                    // New photo being added to existing job
                    PhotoEntity newPhoto = photoMapper.toNewEntity(photo);
                    newPhoto.setJobId(savedJobEntity.getId());
                    photoJpaRepository.save(newPhoto);
                }
            }
        }

        // Reconstruct domain object with saved photos
        UploadJob result = jobMapper.toDomain(savedJobEntity);
        List<Photo> savedPhotos = photoJpaRepository.findByJobId(savedJobEntity.getId()).stream()
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
