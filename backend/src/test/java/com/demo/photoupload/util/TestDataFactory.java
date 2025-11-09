package com.demo.photoupload.util;

import com.demo.photoupload.domain.model.*;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.entity.UploadJobEntity;
import com.demo.photoupload.infrastructure.persistence.entity.UserEntity;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory for creating test data objects.
 * <p>
 * Provides convenient methods to create domain objects, entities, and DTOs
 * with sensible defaults for testing.
 */
public class TestDataFactory {

    private static final String TEST_BUCKET = "test-bucket";

    /**
     * Create a test UserId.
     */
    public static UserId userId() {
        return new UserId(UUID.randomUUID());
    }

    /**
     * Create a test UserId with specific UUID.
     */
    public static UserId userId(UUID uuid) {
        return new UserId(uuid);
    }

    /**
     * Create a test PhotoId.
     */
    public static PhotoId photoId() {
        return new PhotoId(UUID.randomUUID());
    }

    /**
     * Create a test PhotoId with specific UUID.
     */
    public static PhotoId photoId(UUID uuid) {
        return new PhotoId(uuid);
    }

    /**
     * Create a test UploadJobId.
     */
    public static UploadJobId uploadJobId() {
        return new UploadJobId(UUID.randomUUID());
    }

    /**
     * Create a test UploadJobId with specific UUID.
     */
    public static UploadJobId uploadJobId(UUID uuid) {
        return new UploadJobId(uuid);
    }

    /**
     * Create a test PhotoMetadata.
     */
    public static PhotoMetadata photoMetadata() {
        return photoMetadata("test-photo.jpg", 2_000_000L, "image/jpeg");
    }

    /**
     * Create a test PhotoMetadata with specific values.
     */
    public static PhotoMetadata photoMetadata(String filename, long fileSizeBytes, String mimeType) {
        return new PhotoMetadata(filename, fileSizeBytes, mimeType);
    }

    /**
     * Create a test S3Location.
     */
    public static S3Location s3Location() {
        return s3Location("test-bucket", "photos/" + UUID.randomUUID() + ".jpg");
    }

    /**
     * Create a test S3Location with specific bucket and key.
     */
    public static S3Location s3Location(String bucket, String key) {
        return new S3Location(bucket, key);
    }

    /**
     * Create a test Photo in PENDING status.
     */
    public static Photo photo() {
        return Photo.create(uploadJobId(), userId(), photoMetadata(), TEST_BUCKET);
    }

    /**
     * Create a test Photo with specific values.
     */
    public static Photo photo(UploadJobId jobId, UserId userId, PhotoMetadata metadata, String s3Bucket) {
        return Photo.create(jobId, userId, metadata, s3Bucket);
    }

    /**
     * Create a test UploadJob.
     */
    public static UploadJob uploadJob() {
        return uploadJob(userId(), 5);
    }

    /**
     * Create a test UploadJob with specific number of photos.
     */
    public static UploadJob uploadJob(int photoCount) {
        return uploadJob(userId(), photoCount);
    }

    /**
     * Create a test UploadJob with specific values.
     */
    public static UploadJob uploadJob(UserId userId, int photoCount) {
        List<PhotoMetadata> photoMetadataList = new ArrayList<>();
        for (int i = 0; i < photoCount; i++) {
            photoMetadataList.add(photoMetadata("photo-" + i + ".jpg", 2_000_000L, "image/jpeg"));
        }
        return UploadJob.create(userId, photoMetadataList, "test-bucket");
    }

    /**
     * Create a test UserEntity.
     */
    public static UserEntity userEntity() {
        return userEntity("test@example.com", "John Doe");
    }

    /**
     * Create a test UserEntity with specific values.
     * Uses the public constructor and then sets the ID via reflection for testing purposes.
     */
    public static UserEntity userEntity(String email, String fullName) {
        return new UserEntity(email, "$2a$10$test.hashed.password", fullName);
    }

    /**
     * Create a test UserEntity with specific ID (for testing purposes).
     */
    public static UserEntity userEntityWithId(UUID id, String email, String fullName) {
        UserEntity user = new UserEntity(email, "$2a$10$test.hashed.password", fullName);
        setField(user, "id", id);
        return user;
    }

    /**
     * Create a test PhotoEntity.
     * Note: PhotoEntity doesn't have a public all-args constructor, so we use setters.
     */
    public static PhotoEntity photoEntity() {
        return photoEntity(UUID.randomUUID(), UUID.randomUUID());
    }

    /**
     * Create a test PhotoEntity with specific IDs.
     */
    public static PhotoEntity photoEntity(UUID photoId, UUID userId) {
        return photoEntity(photoId, userId, UUID.randomUUID());
    }

    /**
     * Create a test PhotoEntity with specific IDs and job ID.
     */
    public static PhotoEntity photoEntity(UUID photoId, UUID userId, UUID jobId) {
        PhotoEntity photo = createPhotoEntityViaReflection();
        photo.setId(photoId);
        photo.setJobId(jobId);
        photo.setUserId(userId);
        photo.setFilename(photoId + ".jpg");
        photo.setOriginalFilename("test-photo.jpg");
        photo.setFileSizeBytes(2_000_000L);
        photo.setMimeType("image/jpeg");
        photo.setS3Bucket("test-bucket");
        photo.setS3Key("photos/" + photoId + ".jpg");
        photo.setStatus("PENDING");
        photo.setCreatedAt(Instant.now());
        return photo;
    }

    /**
     * Create a test UploadJobEntity.
     */
    public static UploadJobEntity uploadJobEntity() {
        return uploadJobEntity(UUID.randomUUID(), UUID.randomUUID());
    }

    /**
     * Create a test UploadJobEntity with specific IDs.
     */
    public static UploadJobEntity uploadJobEntity(UUID jobId, UUID userId) {
        UploadJobEntity job = createUploadJobEntityViaReflection();
        job.setId(jobId);
        job.setUserId(userId);
        job.setStatus("IN_PROGRESS");
        job.setTotalPhotos(5);
        job.setCompletedPhotos(0);
        job.setFailedPhotos(0);
        job.setCreatedAt(Instant.now());
        return job;
    }

    /**
     * Helper method to create PhotoEntity via reflection (for protected constructor).
     */
    private static PhotoEntity createPhotoEntityViaReflection() {
        try {
            return PhotoEntity.class.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PhotoEntity via reflection", e);
        }
    }

    /**
     * Helper method to create UploadJobEntity via reflection (for protected constructor).
     */
    private static UploadJobEntity createUploadJobEntityViaReflection() {
        try {
            return UploadJobEntity.class.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create UploadJobEntity via reflection", e);
        }
    }

    /**
     * Helper method to set a private field via reflection (for test data setup).
     */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    /**
     * Create a list of test PhotoMetadata objects.
     */
    public static List<PhotoMetadata> photoMetadataList(int count) {
        List<PhotoMetadata> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(photoMetadata("photo-" + i + ".jpg", 2_000_000L, "image/jpeg"));
        }
        return list;
    }

    /**
     * Create a list of test Photo objects.
     */
    public static List<Photo> photoList(int count) {
        List<Photo> list = new ArrayList<>();
        UserId userId = userId();
        UploadJobId jobId = uploadJobId();
        for (int i = 0; i < count; i++) {
            PhotoMetadata metadata = photoMetadata("photo-" + i + ".jpg", 2_000_000L, "image/jpeg");
            list.add(photo(jobId, userId, metadata, TEST_BUCKET));
        }
        return list;
    }
}
