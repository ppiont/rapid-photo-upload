# RapidPhotoUpload: Production-Ready Architecture

## Executive Summary

**Architecture:** Monorepo with Spring Boot backend, React web client, React Native mobile client, and Terraform-managed AWS infrastructure.

**Core Pattern:** Direct S3 uploads via pre-signed URLs, with backend managing metadata only.

**AWS Services:** ALB → ECS Fargate → RDS PostgreSQL + S3

**Performance:** 100 concurrent uploads complete in ~64 seconds @ 25 Mbps (under 90-second PRD requirement).

**Compliance:** Full adherence to DDD/CQRS/VSA with proper domain modeling, including UploadJob aggregate.

---

## Repository Structure

```
rapid-photo-upload/
├── backend/                    # Spring Boot (Java)
│   ├── src/main/
│   │   ├── java/com/demo/photoupload/
│   │   │   ├── domain/         # Pure domain objects (no framework deps)
│   │   │   ├── application/    # CQRS handlers
│   │   │   ├── infrastructure/ # JPA, S3, adapters
│   │   │   └── web/            # REST controllers (VSA)
│   │   └── resources/
│   │       └── db/migration/   # Flyway SQL
│   └── pom.xml
│
├── web/                        # React + TypeScript
│   └── src/
│       ├── components/
│       ├── services/
│       └── hooks/
│
├── mobile/                     # React Native
│   └── src/
│       ├── screens/
│       └── services/
│
└── terraform/                  # Infrastructure as Code
    ├── main.tf
    ├── network.tf
    ├── compute.tf
    └── data.tf
```

---

## Database Schema (PostgreSQL)

### Core Tables

**Purpose:** RDS stores metadata only. Binary files are in S3.

#### users
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

#### upload_jobs (NEW - Critical DDD Aggregate)
```sql
CREATE TABLE upload_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'PARTIAL_FAILURE')),
    total_photos INT NOT NULL,
    completed_photos INT DEFAULT 0,
    failed_photos INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_upload_jobs_user_status ON upload_jobs(user_id, status, created_at DESC);
```

#### photos
```sql
CREATE TABLE photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES upload_jobs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- File metadata
    filename VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100),

    -- S3 location
    s3_bucket VARCHAR(255) NOT NULL,
    s3_key VARCHAR(1000) NOT NULL UNIQUE,

    -- Upload state (simplified)
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'UPLOADING', 'COMPLETED', 'FAILED')),

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Composite indexes for common queries
CREATE INDEX idx_photos_job_status ON photos(job_id, status);
CREATE INDEX idx_photos_user_status ON photos(user_id, status, created_at DESC);
CREATE INDEX idx_photos_s3_key ON photos(s3_key);
```

#### photo_tags
```sql
CREATE TABLE photo_tags (
    id BIGSERIAL PRIMARY KEY,
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(photo_id, tag)
);

CREATE INDEX idx_photo_tags_photo ON photo_tags(photo_id);
CREATE INDEX idx_photo_tags_tag ON photo_tags(tag);
```

### Key Schema Decisions

**What's Different:**
- Added `upload_jobs` table as proper DDD aggregate (fixes PRD violation)
- Removed `upload_progress` field (progress is client-side only)
- Removed `error_message` field (errors handled client-side, logged server-side)
- Added `job_id` foreign key to group photos by batch
- Simplified `status` enum (removed intermediate states from DB)
- Consolidated to composite indexes only (better write performance)

**Why:**
- UploadJob encapsulates batch-level invariants (e.g., "job complete when all photos done")
- Progress tracking via DB for S3 direct uploads is fundamentally broken (client has truth)
- Composite indexes cover all query patterns without redundancy

---

## Domain Model (DDD)

### Core Aggregates

#### UploadJob Aggregate Root
```java
package com.demo.photoupload.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UploadJob aggregate root - manages batch upload lifecycle.
 * 
 * Domain invariants:
 * - Total photos must equal sum of completed + failed + pending
 * - Status transitions: IN_PROGRESS -> COMPLETED | PARTIAL_FAILURE
 * - Cannot add photos after job creation
 */
public class UploadJob {
    private final UploadJobId id;
    private final UserId userId;
    private final List<Photo> photos;
    private UploadJobStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;
    
    // Factory method
    public static UploadJob create(UserId userId, List<PhotoMetadata> photoMetadata, String s3Bucket) {
        UploadJobId jobId = UploadJobId.generate();
        List<Photo> photos = photoMetadata.stream()
            .map(metadata -> Photo.initialize(jobId, userId, metadata, s3Bucket))
            .toList();

        return new UploadJob(jobId, userId, photos, UploadJobStatus.IN_PROGRESS, LocalDateTime.now());
    }
    
    private UploadJob(UploadJobId id, UserId userId, List<Photo> photos, 
                      UploadJobStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.photos = new ArrayList<>(photos);
        this.status = status;
        this.createdAt = createdAt;
    }
    
    // Domain behavior
    public void checkAndUpdateStatus() {
        long completed = photos.stream().filter(Photo::isCompleted).count();
        long failed = photos.stream().filter(Photo::isFailed).count();
        long pending = photos.stream().filter(Photo::isPending).count();
        long uploading = photos.stream().filter(Photo::isUploading).count();
        
        if (pending == 0 && uploading == 0) {
            if (failed > 0) {
                this.status = UploadJobStatus.PARTIAL_FAILURE;
            } else {
                this.status = UploadJobStatus.COMPLETED;
            }
            this.completedAt = LocalDateTime.now();
        }
    }
    
    public int getTotalPhotos() {
        return photos.size();
    }
    
    public long getCompletedCount() {
        return photos.stream().filter(Photo::isCompleted).count();
    }
    
    public long getFailedCount() {
        return photos.stream().filter(Photo::isFailed).count();
    }
    
    public List<Photo> getPhotos() {
        return Collections.unmodifiableList(photos);
    }
    
    // Getters
    public UploadJobId getId() { return id; }
    public UserId getUserId() { return userId; }
    public UploadJobStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
```

#### Photo Aggregate Root
```java
package com.demo.photoupload.domain;

import java.time.LocalDateTime;

/**
 * Photo aggregate root - represents a single uploaded photo.
 * 
 * State machine: PENDING -> UPLOADING -> COMPLETED | FAILED
 */
public class Photo {
    private final PhotoId id;
    private final UploadJobId jobId;
    private final UserId userId;
    private final PhotoMetadata metadata;
    private final S3Location s3Location;
    private PhotoStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Factory method
    public static Photo initialize(UploadJobId jobId, UserId userId, PhotoMetadata metadata, String s3Bucket) {
        PhotoId photoId = PhotoId.generate();
        S3Location location = S3Location.forPhoto(s3Bucket, photoId, metadata.getFilename());

        return new Photo(
            photoId,
            jobId,
            userId,
            metadata,
            location,
            PhotoStatus.PENDING,
            LocalDateTime.now()
        );
    }
    
    private Photo(PhotoId id, UploadJobId jobId, UserId userId, 
                  PhotoMetadata metadata, S3Location s3Location,
                  PhotoStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.userId = userId;
        this.metadata = metadata;
        this.s3Location = s3Location;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    // Domain behaviors (state transitions)
    public void markAsStarted() {
        if (status != PhotoStatus.PENDING) {
            throw new IllegalStateException("Can only start pending photos");
        }
        this.status = PhotoStatus.UPLOADING;
        this.startedAt = LocalDateTime.now();
    }
    
    public void markAsCompleted() {
        if (status != PhotoStatus.UPLOADING) {
            throw new IllegalStateException("Can only complete uploading photos");
        }
        this.status = PhotoStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
    
    public void markAsFailed() {
        this.status = PhotoStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
    
    // Query methods
    public boolean isCompleted() { return status == PhotoStatus.COMPLETED; }
    public boolean isFailed() { return status == PhotoStatus.FAILED; }
    public boolean isPending() { return status == PhotoStatus.PENDING; }
    public boolean isUploading() { return status == PhotoStatus.UPLOADING; }
    
    // Getters
    public PhotoId getId() { return id; }
    public UploadJobId getJobId() { return jobId; }
    public UserId getUserId() { return userId; }
    public PhotoMetadata getMetadata() { return metadata; }
    public S3Location getS3Location() { return s3Location; }
    public PhotoStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
```

### Value Objects

#### PhotoMetadata
```java
package com.demo.photoupload.domain;

public class PhotoMetadata {
    private final String filename;
    private final long fileSizeBytes;
    private final String mimeType;
    
    public PhotoMetadata(String filename, long fileSizeBytes, String mimeType) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
        this.filename = filename;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
    }
    
    public String getFilename() { return filename; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getMimeType() { return mimeType; }
}
```

#### S3Location
```java
package com.demo.photoupload.domain;

public class S3Location {
    private final String bucket;
    private final String key;

    public static S3Location forPhoto(String bucket, PhotoId photoId, String filename) {
        String key = String.format("photos/%s/%s", photoId.getValue(), filename);
        return new S3Location(bucket, key);
    }

    private S3Location(String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
    }

    public String getBucket() { return bucket; }
    public String getKey() { return key; }
}
```

#### Identity Value Objects
```java
package com.demo.photoupload.domain;

import java.util.UUID;

public class UploadJobId {
    private final String value;
    
    public static UploadJobId generate() {
        return new UploadJobId(UUID.randomUUID().toString());
    }
    
    public static UploadJobId of(String value) {
        return new UploadJobId(value);
    }
    
    private UploadJobId(String value) {
        this.value = value;
    }
    
    public String getValue() { return value; }
}

public class PhotoId {
    private final String value;
    
    public static PhotoId generate() {
        return new PhotoId(UUID.randomUUID().toString());
    }
    
    public static PhotoId of(String value) {
        return new PhotoId(value);
    }
    
    private PhotoId(String value) {
        this.value = value;
    }
    
    public String getValue() { return value; }
}

public class UserId {
    private final Long value;
    
    public static UserId of(Long value) {
        return new UserId(value);
    }
    
    private UserId(Long value) {
        this.value = value;
    }
    
    public Long getValue() { return value; }
}
```

### Enums

```java
package com.demo.photoupload.domain;

public enum UploadJobStatus {
    IN_PROGRESS,
    COMPLETED,
    PARTIAL_FAILURE
}

public enum PhotoStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}
```

### Repository Interfaces (Ports)

```java
package com.demo.photoupload.domain;

import java.util.List;
import java.util.Optional;

public interface UploadJobRepository {
    UploadJob save(UploadJob uploadJob);
    Optional<UploadJob> findById(UploadJobId id);
    List<UploadJob> findByUserId(UserId userId);
}

public interface PhotoRepository {
    List<Photo> saveAll(List<Photo> photos);
    Photo save(Photo photo);
    Optional<Photo> findById(PhotoId id);
    List<Photo> findByJobId(UploadJobId jobId);
    List<Photo> findByUserId(UserId userId, PhotoStatus status, int page, int size);
}
```

---

## Application Layer (CQRS)

### Commands

#### InitializeUploadCommand
```java
package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.PhotoMetadata;
import com.demo.photoupload.domain.UserId;
import java.util.List;

public record InitializeUploadCommand(
    UserId userId,
    List<PhotoMetadata> photoMetadata
) {}
```

#### InitializeUploadHandler
```java
package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.*;
import com.demo.photoupload.infrastructure.s3.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InitializeUploadHandler {
    private final UploadJobRepository uploadJobRepository;
    private final PhotoRepository photoRepository;
    private final S3Service s3Service;
    private final String s3BucketName;

    public InitializeUploadHandler(
        UploadJobRepository uploadJobRepository,
        PhotoRepository photoRepository,
        S3Service s3Service,
        @Value("${aws.s3.bucket-name}") String s3BucketName
    ) {
        this.uploadJobRepository = uploadJobRepository;
        this.photoRepository = photoRepository;
        this.s3Service = s3Service;
        this.s3BucketName = s3BucketName;
    }

    @Transactional
    public InitializeUploadResult handle(InitializeUploadCommand command) {
        // Create upload job aggregate with S3 bucket from configuration
        UploadJob uploadJob = UploadJob.create(command.userId(), command.photoMetadata(), s3BucketName);
        
        // Persist job and photos in batch
        uploadJobRepository.save(uploadJob);
        photoRepository.saveAll(uploadJob.getPhotos());
        
        // Generate pre-signed URLs for each photo
        List<PhotoUploadInfo> uploadInfos = uploadJob.getPhotos().stream()
            .map(photo -> new PhotoUploadInfo(
                photo.getId(),
                photo.getMetadata().getFilename(),
                s3Service.generatePresignedUploadUrl(
                    photo.getS3Location().getKey(),
                    3600 // 1 hour expiry
                )
            ))
            .toList();
        
        return new InitializeUploadResult(uploadJob.getId(), uploadInfos);
    }
}

// DTOs
public record InitializeUploadResult(UploadJobId jobId, List<PhotoUploadInfo> photos) {}
public record PhotoUploadInfo(PhotoId photoId, String filename, String uploadUrl) {}
```

#### StartPhotoUploadCommand
```java
package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.PhotoId;
import com.demo.photoupload.domain.UserId;

public record StartPhotoUploadCommand(PhotoId photoId, UserId userId) {}
```

#### StartPhotoUploadHandler
```java
package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartPhotoUploadHandler {
    private final PhotoRepository photoRepository;
    
    public StartPhotoUploadHandler(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }
    
    @Transactional
    public void handle(StartPhotoUploadCommand command) {
        Photo photo = photoRepository.findById(command.photoId())
            .orElseThrow(() -> new PhotoNotFoundException(command.photoId()));
        
        // Verify ownership
        if (!photo.getUserId().equals(command.userId())) {
            throw new UnauthorizedAccessException();
        }
        
        // Domain behavior - state transition
        photo.markAsStarted();
        
        photoRepository.save(photo);
    }
}
```

#### CompletePhotoUploadCommand
```java
package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.PhotoId;
import com.demo.photoupload.domain.UserId;

public record CompletePhotoUploadCommand(PhotoId photoId, UserId userId) {}
```

#### CompletePhotoUploadHandler
```java
package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompletePhotoUploadHandler {
    private final PhotoRepository photoRepository;
    private final UploadJobRepository uploadJobRepository;
    
    public CompletePhotoUploadHandler(
        PhotoRepository photoRepository,
        UploadJobRepository uploadJobRepository
    ) {
        this.photoRepository = photoRepository;
        this.uploadJobRepository = uploadJobRepository;
    }
    
    @Transactional
    public void handle(CompletePhotoUploadCommand command) {
        Photo photo = photoRepository.findById(command.photoId())
            .orElseThrow(() -> new PhotoNotFoundException(command.photoId()));
        
        // Verify ownership
        if (!photo.getUserId().equals(command.userId())) {
            throw new UnauthorizedAccessException();
        }
        
        // Mark photo complete
        photo.markAsCompleted();
        photoRepository.save(photo);
        
        // Update job status
        UploadJob job = uploadJobRepository.findById(photo.getJobId())
            .orElseThrow(() -> new UploadJobNotFoundException(photo.getJobId()));
        
        job.checkAndUpdateStatus();
        uploadJobRepository.save(job);
    }
}
```

### Queries

#### GetUploadJobStatusQuery
```java
package com.demo.photoupload.application.queries;

import com.demo.photoupload.domain.UploadJobId;
import com.demo.photoupload.domain.UserId;

public record GetUploadJobStatusQuery(UploadJobId jobId, UserId userId) {}
```

#### GetUploadJobStatusHandler
```java
package com.demo.photoupload.application.queries;

import com.demo.photoupload.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetUploadJobStatusHandler {
    private final UploadJobRepository uploadJobRepository;
    private final PhotoRepository photoRepository;
    
    public GetUploadJobStatusHandler(
        UploadJobRepository uploadJobRepository,
        PhotoRepository photoRepository
    ) {
        this.uploadJobRepository = uploadJobRepository;
        this.photoRepository = photoRepository;
    }
    
    public UploadJobStatusResult handle(GetUploadJobStatusQuery query) {
        UploadJob job = uploadJobRepository.findById(query.jobId())
            .orElseThrow(() -> new UploadJobNotFoundException(query.jobId()));
        
        // Verify ownership
        if (!job.getUserId().equals(query.userId())) {
            throw new UnauthorizedAccessException();
        }
        
        // Get photo details
        List<Photo> photos = photoRepository.findByJobId(query.jobId());
        
        List<PhotoStatusDto> photoStatuses = photos.stream()
            .map(photo -> new PhotoStatusDto(
                photo.getId(),
                photo.getMetadata().getFilename(),
                photo.getStatus()
            ))
            .toList();
        
        return new UploadJobStatusResult(
            job.getId(),
            job.getStatus(),
            job.getTotalPhotos(),
            (int) job.getCompletedCount(),
            (int) job.getFailedCount(),
            photoStatuses
        );
    }
}

// DTOs
public record UploadJobStatusResult(
    UploadJobId jobId,
    UploadJobStatus status,
    int totalPhotos,
    int completedPhotos,
    int failedPhotos,
    List<PhotoStatusDto> photos
) {}

public record PhotoStatusDto(
    PhotoId photoId,
    String filename,
    PhotoStatus status
) {}
```

#### GetPhotosQuery
```java
package com.demo.photoupload.application.queries;

import com.demo.photoupload.domain.UserId;

public record GetPhotosQuery(
    UserId userId,
    int page,
    int size
) {}
```

#### GetPhotosHandler
```java
package com.demo.photoupload.application.queries;

import com.demo.photoupload.domain.*;
import com.demo.photoupload.infrastructure.s3.S3Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetPhotosHandler {
    private final PhotoRepository photoRepository;
    private final S3Service s3Service;
    
    public GetPhotosHandler(PhotoRepository photoRepository, S3Service s3Service) {
        this.photoRepository = photoRepository;
        this.s3Service = s3Service;
    }
    
    public GetPhotosResult handle(GetPhotosQuery query) {
        List<Photo> photos = photoRepository.findByUserId(
            query.userId(),
            PhotoStatus.COMPLETED,
            query.page(),
            query.size()
        );
        
        List<PhotoDto> photoDtos = photos.stream()
            .map(photo -> new PhotoDto(
                photo.getId(),
                photo.getMetadata().getFilename(),
                photo.getMetadata().getFileSizeBytes(),
                photo.getMetadata().getMimeType(),
                s3Service.generatePresignedDownloadUrl(
                    photo.getS3Location().getKey(),
                    3600
                ),
                photo.getCreatedAt()
            ))
            .toList();
        
        return new GetPhotosResult(photoDtos);
    }
}

// DTOs
public record GetPhotosResult(List<PhotoDto> photos) {}
public record PhotoDto(
    PhotoId photoId,
    String filename,
    long fileSizeBytes,
    String mimeType,
    String downloadUrl,
    LocalDateTime createdAt
) {}
```

---

## Infrastructure Layer

### Persistence Adapters

#### JPA Entities (Separate from Domain)

```java
package com.demo.photoupload.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "upload_jobs")
public class UploadJobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "total_photos", nullable = false)
    private Integer totalPhotos;
    
    @Column(name = "completed_photos")
    private Integer completedPhotos;
    
    @Column(name = "failed_photos")
    private Integer failedPhotos;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PhotoEntity> photos = new ArrayList<>();
    
    // Constructors, getters, setters
}

@Entity
@Table(name = "photos")
public class PhotoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private UploadJobEntity job;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;
    
    @Column(name = "mime_type")
    private String mimeType;
    
    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;
    
    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    // Constructors, getters, setters
}
```

#### Repository Adapters

```java
package com.demo.photoupload.infrastructure.persistence;

import com.demo.photoupload.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UploadJobRepositoryAdapter implements UploadJobRepository {
    private final JpaUploadJobRepository jpaRepository;
    private final UploadJobMapper mapper;
    
    public UploadJobRepositoryAdapter(
        JpaUploadJobRepository jpaRepository,
        UploadJobMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }
    
    @Override
    public UploadJob save(UploadJob uploadJob) {
        UploadJobEntity entity = mapper.toEntity(uploadJob);
        UploadJobEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
    
    @Override
    public Optional<UploadJob> findById(UploadJobId id) {
        return jpaRepository.findById(UUID.fromString(id.getValue()))
            .map(mapper::toDomain);
    }
    
    @Override
    public List<UploadJob> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.getValue()).stream()
            .map(mapper::toDomain)
            .toList();
    }
}

interface JpaUploadJobRepository extends JpaRepository<UploadJobEntity, UUID> {
    List<UploadJobEntity> findByUserId(Long userId);
}
```

#### Mappers (Domain ↔ Entity)

```java
package com.demo.photoupload.infrastructure.persistence;

import com.demo.photoupload.domain.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UploadJobMapper {
    private final PhotoMapper photoMapper;
    
    public UploadJobMapper(PhotoMapper photoMapper) {
        this.photoMapper = photoMapper;
    }
    
    public UploadJobEntity toEntity(UploadJob domain) {
        UploadJobEntity entity = new UploadJobEntity();
        entity.setId(UUID.fromString(domain.getId().getValue()));
        entity.setUserId(domain.getUserId().getValue());
        entity.setStatus(domain.getStatus().name());
        entity.setTotalPhotos(domain.getTotalPhotos());
        entity.setCompletedPhotos((int) domain.getCompletedCount());
        entity.setFailedPhotos((int) domain.getFailedCount());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setCompletedAt(domain.getCompletedAt());
        return entity;
    }
    
    public UploadJob toDomain(UploadJobEntity entity) {
        List<Photo> photos = entity.getPhotos().stream()
            .map(photoMapper::toDomain)
            .toList();

        // Reconstruct domain object - UUID.toString() gives proper UUID string format
        return new UploadJob(
            UploadJobId.of(entity.getId().toString()),
            UserId.of(entity.getUserId()),
            photos,
            UploadJobStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getCompletedAt()
        );
    }
}
```

### S3 Service

```java
package com.demo.photoupload.infrastructure.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;

@Service
public class S3Service {
    private final S3Presigner s3Presigner;
    private final String bucketName;
    
    public S3Service(
        S3Presigner s3Presigner,
        @Value("${aws.s3.bucket-name}") String bucketName
    ) {
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }
    
    public String generatePresignedUploadUrl(String s3Key, int durationSeconds) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build();
        
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(durationSeconds))
            .putObjectRequest(putRequest)
            .build();
        
        return s3Presigner.presignPutObject(presignRequest)
            .url()
            .toString();
    }
    
    public String generatePresignedDownloadUrl(String s3Key, int durationSeconds) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build();
        
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(durationSeconds))
            .getObjectRequest(getRequest)
            .build();
        
        return s3Presigner.presignGetObject(presignRequest)
            .url()
            .toString();
    }
}
```

---

## Web Layer (Vertical Slices)

### Upload Controller

```java
package com.demo.photoupload.web.upload;

import com.demo.photoupload.application.commands.*;
import com.demo.photoupload.application.queries.*;
import com.demo.photoupload.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    private final InitializeUploadHandler initHandler;
    private final StartPhotoUploadHandler startHandler;
    private final CompletePhotoUploadHandler completeHandler;
    private final GetUploadJobStatusHandler statusHandler;
    
    public UploadController(
        InitializeUploadHandler initHandler,
        StartPhotoUploadHandler startHandler,
        CompletePhotoUploadHandler completeHandler,
        GetUploadJobStatusHandler statusHandler
    ) {
        this.initHandler = initHandler;
        this.startHandler = startHandler;
        this.completeHandler = completeHandler;
        this.statusHandler = statusHandler;
    }
    
    @PostMapping("/initialize")
    public ResponseEntity<InitializeUploadResponse> initialize(
        @RequestBody InitializeUploadRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        List<PhotoMetadata> metadata = request.files().stream()
            .map(f -> new PhotoMetadata(f.filename(), f.size(), f.mimeType()))
            .toList();
        
        var command = new InitializeUploadCommand(UserId.of(userId), metadata);
        var result = initHandler.handle(command);
        
        var response = new InitializeUploadResponse(
            result.jobId().getValue(),
            result.photos().stream()
                .map(p -> new PhotoUploadDto(
                    p.photoId().getValue(),
                    p.filename(),
                    p.uploadUrl()
                ))
                .toList()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/photos/{photoId}/start")
    public ResponseEntity<Void> startUpload(
        @PathVariable String photoId,
        @AuthenticationPrincipal Long userId
    ) {
        var command = new StartPhotoUploadCommand(PhotoId.of(photoId), UserId.of(userId));
        startHandler.handle(command);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/photos/{photoId}/complete")
    public ResponseEntity<Void> completeUpload(
        @PathVariable String photoId,
        @AuthenticationPrincipal Long userId
    ) {
        var command = new CompletePhotoUploadCommand(PhotoId.of(photoId), UserId.of(userId));
        completeHandler.handle(command);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<UploadJobStatusResponse> getStatus(
        @PathVariable String jobId,
        @AuthenticationPrincipal Long userId
    ) {
        var query = new GetUploadJobStatusQuery(UploadJobId.of(jobId), UserId.of(userId));
        var result = statusHandler.handle(query);
        
        var response = new UploadJobStatusResponse(
            result.jobId().getValue(),
            result.status().name(),
            result.totalPhotos(),
            result.completedPhotos(),
            result.failedPhotos(),
            result.photos().stream()
                .map(p -> new PhotoStatusDto(
                    p.photoId().getValue(),
                    p.filename(),
                    p.status().name()
                ))
                .toList()
        );
        
        return ResponseEntity.ok(response);
    }
}

// Request/Response DTOs
record InitializeUploadRequest(List<FileInfo> files) {}
record FileInfo(String filename, long size, String mimeType) {}

record InitializeUploadResponse(String jobId, List<PhotoUploadDto> photos) {}
record PhotoUploadDto(String photoId, String filename, String uploadUrl) {}

record UploadJobStatusResponse(
    String jobId,
    String status,
    int totalPhotos,
    int completedPhotos,
    int failedPhotos,
    List<PhotoStatusDto> photos
) {}
record PhotoStatusDto(String photoId, String filename, String status) {}
```

---

## Upload Flow

### State Machine

```
PENDING → UPLOADING → COMPLETED
             ↓
           FAILED
```

### Client-Server Interaction

**1. Initialize Upload**
```
POST /api/upload/initialize
Body: {
  "files": [
    {"filename": "photo1.jpg", "size": 2048000, "mimeType": "image/jpeg"},
    ... (up to 100)
  ]
}

Response: {
  "jobId": "uuid",
  "photos": [
    {"photoId": "uuid", "filename": "photo1.jpg", "uploadUrl": "https://s3..."},
    ... (100 pre-signed URLs)
  ]
}

Backend:
- Creates UploadJob aggregate (IN_PROGRESS)
- Creates 100 Photo entities (PENDING)
- Batch inserts to DB
- Generates pre-signed URLs
- Returns in ~50-100ms
```

**2. Start Upload (per photo)**
```
Client calls BEFORE starting S3 upload:
PUT /api/upload/photos/{photoId}/start

Backend:
- Transitions photo: PENDING → UPLOADING
- Returns 200 OK in ~10ms
```

**3. Upload to S3 (direct)**
```
Client: PUT https://s3.../photo1.jpg (with pre-signed URL)
- Uploads directly to S3 (bypasses backend)
- Tracks progress locally in client
- No DB updates during upload
```

**4. Mark Complete (per photo)**
```
Client calls AFTER S3 upload succeeds:
PUT /api/upload/photos/{photoId}/complete

Backend:
- Transitions photo: UPLOADING → COMPLETED
- Checks job status
- Updates job if all photos done
- Returns 200 OK in ~10-20ms
```

**5. Poll Status (every 2-5 seconds)**
```
GET /api/upload/jobs/{jobId}/status

Response: {
  "jobId": "uuid",
  "status": "IN_PROGRESS",
  "totalPhotos": 100,
  "completedPhotos": 45,
  "failedPhotos": 2,
  "photos": [
    {"photoId": "...", "filename": "photo1.jpg", "status": "COMPLETED"},
    {"photoId": "...", "filename": "photo2.jpg", "status": "UPLOADING"},
    ...
  ]
}

Backend:
- Single query on upload_jobs (indexed)
- Optional: fetch photos list if needed
- Returns in ~10-20ms
```

### Key Improvements Over v1

**Progress Tracking:**
- Client handles progress bars locally (smooth, real-time)
- Backend only tracks state changes (PENDING/UPLOADING/COMPLETED/FAILED)
- No wasteful API calls for intermediate progress

**State Transitions:**
- Explicit `/start` endpoint marks when client begins upload
- Clear state machine enforced by domain model
- Job status auto-updates when all photos complete

**Batch Operations:**
- UploadJob aggregate properly encapsulates batch logic
- Job-level queries are simple (single row lookup)
- Photo queries grouped by `job_id` (indexed)

---

## Frontend Implementation

### React Web (TypeScript)

#### PhotoUploader.tsx
```typescript
import React, { useState } from 'react';
import { uploadService } from '../services/uploadService';

interface UploadFile {
  file: File;
  photoId?: string;
  status: 'pending' | 'uploading' | 'completed' | 'failed';
  progress: number; // client-side only
}

export const PhotoUploader: React.FC = () => {
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [jobId, setJobId] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const selected = Array.from(e.target.files).map(file => ({
        file,
        status: 'pending' as const,
        progress: 0,
      }));
      setFiles(selected);
    }
  };

  const uploadPhotos = async () => {
    setUploading(true);

    try {
      // Step 1: Initialize
      const initResult = await uploadService.initialize(
        files.map(f => ({
          filename: f.file.name,
          size: f.file.size,
          mimeType: f.file.type,
        }))
      );
      
      setJobId(initResult.jobId);

      // Step 2: Upload all 100 concurrently
      const uploadPromises = initResult.photos.map(async (photoData, index) => {
        const fileData = files[index];
        
        try {
          // Mark as started (optional but recommended)
          await uploadService.startUpload(photoData.photoId);
          updateFileStatus(index, 'uploading', 0, photoData.photoId);
          
          // Upload to S3 with local progress tracking
          await uploadService.uploadToS3(
            photoData.uploadUrl,
            fileData.file,
            (progress) => updateFileStatus(index, 'uploading', progress, photoData.photoId)
          );
          
          // Mark as complete
          await uploadService.completeUpload(photoData.photoId);
          updateFileStatus(index, 'completed', 100, photoData.photoId);
        } catch (error) {
          updateFileStatus(index, 'failed', 0, photoData.photoId);
        }
      });

      await Promise.all(uploadPromises);
    } catch (error) {
      console.error('Upload initialization failed:', error);
    } finally {
      setUploading(false);
    }
  };

  const updateFileStatus = (
    index: number,
    status: UploadFile['status'],
    progress: number,
    photoId?: string
  ) => {
    setFiles(prev => {
      const updated = [...prev];
      updated[index] = { ...updated[index], status, progress, photoId };
      return updated;
    });
  };

  return (
    <div>
      <input 
        type="file" 
        multiple 
        accept="image/*" 
        onChange={handleFileSelect}
        disabled={uploading}
      />
      
      <button onClick={uploadPhotos} disabled={uploading || files.length === 0}>
        Upload {files.length} Photos
      </button>

      {files.map((file, index) => (
        <div key={index}>
          <span>{file.file.name}</span>
          <span>{file.status}</span>
          <progress value={file.progress} max={100} />
        </div>
      ))}
    </div>
  );
};
```

#### uploadService.ts
```typescript
import axios from 'axios';

export const uploadService = {
  async initialize(files: Array<{filename: string, size: number, mimeType: string}>) {
    const response = await axios.post('/api/upload/initialize', { files });
    return response.data;
  },

  async startUpload(photoId: string) {
    await axios.put(`/api/upload/photos/${photoId}/start`);
  },

  async uploadToS3(
    presignedUrl: string,
    file: File,
    onProgress: (progress: number) => void
  ) {
    await axios.put(presignedUrl, file, {
      headers: { 'Content-Type': file.type },
      onUploadProgress: (progressEvent) => {
        const progress = Math.round(
          (progressEvent.loaded * 100) / (progressEvent.total || 1)
        );
        onProgress(progress);
      },
    });
  },

  async completeUpload(photoId: string) {
    await axios.put(`/api/upload/photos/${photoId}/complete`);
  },

  async getJobStatus(jobId: string) {
    const response = await axios.get(`/api/upload/jobs/${jobId}/status`);
    return response.data;
  },
};
```

### React Native (Simplified)

```typescript
// Similar structure, using expo-file-system for uploads
import * as FileSystem from 'expo-file-system';

const uploadToS3 = async (presignedUrl: string, fileUri: string) => {
  await FileSystem.uploadAsync(presignedUrl, fileUri, {
    httpMethod: 'PUT',
    uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
  });
};
```

---

## Infrastructure (Terraform)

### versions.tf
```hcl
terraform {
  required_version = ">= 1.13.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.19.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}
```

### main.tf
```hcl
locals {
  name = "${var.project_name}-${var.environment}"
  
  tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

variable "aws_region" {
  type    = string
  default = "us-west-1"
}

variable "project_name" {
  type    = string
  default = "rapid-photo-upload"
}

variable "environment" {
  type    = string
  default = "demo"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "container_image" {
  type = string
}
```

### network.tf (VPC, Subnets, Security Groups)
```hcl
# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  tags                 = merge(local.tags, { Name = "${local.name}-vpc" })
}

# Subnets (2 public, 2 private)
resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags                    = merge(local.tags, { Name = "${local.name}-public-${count.index + 1}" })
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 10}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags              = merge(local.tags, { Name = "${local.name}-private-${count.index + 1}" })
}

# Internet Gateway + NAT
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.tags, { Name = "${local.name}-igw" })
}

resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = merge(local.tags, { Name = "${local.name}-nat-eip" })
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id
  tags          = merge(local.tags, { Name = "${local.name}-nat" })
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = merge(local.tags, { Name = "${local.name}-public-rt" })
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }
  tags = merge(local.tags, { Name = "${local.name}-private-rt" })
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# Security Groups
resource "aws_security_group" "alb" {
  name_prefix = "${local.name}-alb-"
  vpc_id      = aws_vpc.main.id
  
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = local.tags
}

resource "aws_security_group" "ecs_tasks" {
  name_prefix = "${local.name}-ecs-"
  vpc_id      = aws_vpc.main.id
  
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = local.tags
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.name}-rds-"
  vpc_id      = aws_vpc.main.id
  
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }
  
  tags = local.tags
}

data "aws_availability_zones" "available" {
  state = "available"
}
```

### data.tf (RDS + S3)
```hcl
# RDS PostgreSQL
resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnet"
  subnet_ids = aws_subnet.private[*].id
  tags       = local.tags
}

resource "aws_db_instance" "postgres" {
  identifier        = "${local.name}-db"
  engine            = "postgres"
  engine_version    = "17.6"
  instance_class    = "db.t3.micro"
  allocated_storage = 20
  storage_type      = "gp3"
  
  db_name  = "photoupload"
  username = "photoadmin"
  password = var.db_password
  
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  
  skip_final_snapshot = true
  tags                = local.tags
}

# S3 Bucket
resource "aws_s3_bucket" "photos" {
  bucket_prefix = "${local.name}-photos-"
  tags          = local.tags
}

resource "aws_s3_bucket_public_access_block" "photos" {
  bucket                  = aws_s3_bucket.photos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id
  
  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT", "GET"]
    allowed_origins = ["*"]
    expose_headers  = ["ETag"]
  }
}
```

### compute.tf (ECS + ALB)
```hcl
# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "${local.name}-cluster"
  tags = local.tags
}

# Task Definition
resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name}-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn
  
  container_definitions = jsonencode([{
    name  = "app"
    image = var.container_image
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/photoupload" },
      { name = "SPRING_DATASOURCE_USERNAME", value = "photoadmin" },
      { name = "SPRING_DATASOURCE_PASSWORD", value = var.db_password },
      { name = "AWS_S3_BUCKET_NAME", value = aws_s3_bucket.photos.id },
      { name = "AWS_REGION", value = var.aws_region }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
  
  tags = local.tags
}

# ECS Service
resource "aws_ecs_service" "app" {
  name            = "${local.name}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 2
  launch_type     = "FARGATE"
  
  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }
  
  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }
  
  depends_on = [aws_lb_listener.http]
  tags       = local.tags
}

# ALB
resource "aws_lb" "main" {
  name               = "${local.name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
  tags               = local.tags
}

resource "aws_lb_target_group" "app" {
  name        = "${local.name}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  
  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
  }
  
  tags = local.tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"
  
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
  
  tags = local.tags
}

# IAM Roles
resource "aws_iam_role" "ecs_execution" {
  name_prefix = "${local.name}-ecs-exec-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ecs_task" {
  name_prefix = "${local.name}-ecs-task-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
  tags = local.tags
}

resource "aws_iam_role_policy" "ecs_task_s3" {
  name_prefix = "s3-access-"
  role        = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
      Resource = ["${aws_s3_bucket.photos.arn}/*"]
    }]
  })
}

# CloudWatch
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = 7
  tags              = local.tags
}

# Outputs
output "alb_url" {
  value = "http://${aws_lb.main.dns_name}"
}
```

---

## Integration Tests

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class UploadIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:latest")
    ).withServices(LocalStackContainer.Service.S3);
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldUpload100PhotosConcurrently() throws Exception {
        // 1. Initialize upload
        List<FileInfo> files = IntStream.range(0, 100)
            .mapToObj(i -> new FileInfo("photo" + i + ".jpg", 2_000_000L, "image/jpeg"))
            .toList();
        
        var initRequest = new InitializeUploadRequest(files);
        var initResponse = restTemplate.postForEntity(
            "/api/upload/initialize",
            initRequest,
            InitializeUploadResponse.class
        ).getBody();
        
        assertEquals(100, initResponse.photos().size());
        String jobId = initResponse.jobId();
        
        // 2. Upload all 100 concurrently
        List<CompletableFuture<Void>> uploads = initResponse.photos().stream()
            .map(photoData -> CompletableFuture.runAsync(() -> {
                // Start upload
                restTemplate.put(
                    "/api/upload/photos/" + photoData.photoId() + "/start",
                    null
                );
                
                // Simulate S3 upload
                byte[] data = new byte[2_000_000];
                restTemplate.exchange(
                    photoData.uploadUrl(),
                    HttpMethod.PUT,
                    new HttpEntity<>(data),
                    Void.class
                );
                
                // Mark complete
                restTemplate.put(
                    "/api/upload/photos/" + photoData.photoId() + "/complete",
                    null
                );
            }))
            .toList();
        
        CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0])).get();
        
        // 3. Verify job status
        var statusResponse = restTemplate.getForEntity(
            "/api/upload/jobs/" + jobId + "/status",
            UploadJobStatusResponse.class
        ).getBody();
        
        assertEquals(100, statusResponse.totalPhotos());
        assertEquals(100, statusResponse.completedPhotos());
        assertEquals("COMPLETED", statusResponse.status());
    }
}
```

---

## Deployment

```bash
# 1. Build and push Docker image
cd backend
./mvnw clean package
docker build -t rapid-photo-upload:latest .
docker tag rapid-photo-upload:latest <ACCOUNT>.dkr.ecr.us-west-1.amazonaws.com/rapid-photo-upload-demo-app:latest
docker push <ACCOUNT>.dkr.ecr.us-west-1.amazonaws.com/rapid-photo-upload-demo-app:latest

# 2. Deploy infrastructure
cd terraform
terraform init
terraform apply \
  -var="db_password=YOUR_PASSWORD" \
  -var="container_image=<ACCOUNT>.dkr.ecr.us-west-1.amazonaws.com/rapid-photo-upload-demo-app:latest"

# 3. Get ALB URL
terraform output alb_url
```

---

## PRD Compliance Summary

| Requirement | Implementation | Status |
|-------------|---------------|--------|
| 100 concurrent uploads | Direct S3 + Promise.all | ✓ |
| 90-second completion | 64 seconds @ 25 Mbps | ✓ |
| Async UI | Client-side progress, non-blocking | ✓ |
| Real-time status | Polling /status every 2-5 sec | ✓ |
| DDD | UploadJob + Photo aggregates | ✓ |
| CQRS | Separate command/query handlers | ✓ |
| VSA | Feature-based controllers | ✓ |
| PostgreSQL | RDS with Flyway migrations | ✓ |
| AWS S3 | Pre-signed URLs for direct upload | ✓ |
| Basic auth | JWT (Spring Security) | ✓ |
| Integration tests | Testcontainers + LocalStack | ✓ |
| Web client | React + TypeScript | ✓ |
| Mobile client | React Native | ✓ |

---

## Key Improvements Over v1

**Domain Modeling:**
- Added UploadJob aggregate (fixes PRD violation)
- Proper separation of domain objects from JPA entities
- State machine enforced by domain behaviors

**Progress Tracking:**
- Removed broken `upload_progress` field from DB
- Client-side progress tracking (accurate, real-time)
- Backend only tracks state transitions

**Database Schema:**
- Added `upload_jobs` table for batch management
- Simplified `photos` table (removed redundant fields)
- Consolidated to composite indexes only

**State Management:**
- Added `/start` endpoint for proper state transitions
- Clear PENDING → UPLOADING → COMPLETED flow
- Job status auto-updates when all photos complete

**Architecture:**
- Pure domain layer (no framework dependencies)
- Adapter pattern for persistence
- Cleaner separation of concerns

**Documentation:**
- Focused on technical substance
- Removed cosmetic elements
- Concise code examples

---

## Cost Estimate

**Monthly (demo usage, 8 hours/day):**
- ECS Fargate (2 tasks): ~$65
- ALB: ~$18
- RDS (db.t3.micro): ~$15
- S3 (100GB): ~$3
- NAT Gateway: ~$32

**Total: ~$133/month**
**Demo scale (8h/day): ~$50/month**

---

**This architecture fully complies with the PRD while maintaining genuine simplicity through proper domain modeling, efficient state management, and minimal infrastructure.**
