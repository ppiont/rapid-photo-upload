package com.demo.photoupload.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for upload_jobs table.
 * Maps to database schema defined in V2__create_upload_jobs_table.sql
 */
@Entity
@Table(name = "upload_jobs", indexes = {
    @Index(name = "idx_upload_jobs_user_status", columnList = "user_id, status, created_at")
})
public class UploadJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "total_photos", nullable = false)
    private Integer totalPhotos;

    @Column(name = "completed_photos", nullable = false)
    private Integer completedPhotos;

    @Column(name = "failed_photos", nullable = false)
    private Integer failedPhotos;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // One-to-many relationship with photos
    @OneToMany(mappedBy = "uploadJob", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PhotoEntity> photos = new ArrayList<>();

    // JPA requires a no-arg constructor
    protected UploadJobEntity() {
    }

    public UploadJobEntity(UUID userId) {
        this.userId = userId;
        this.status = "IN_PROGRESS";
        this.totalPhotos = 0;
        this.completedPhotos = 0;
        this.failedPhotos = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalPhotos() {
        return totalPhotos;
    }

    public void setTotalPhotos(Integer totalPhotos) {
        this.totalPhotos = totalPhotos;
    }

    public Integer getCompletedPhotos() {
        return completedPhotos;
    }

    public void setCompletedPhotos(Integer completedPhotos) {
        this.completedPhotos = completedPhotos;
    }

    public Integer getFailedPhotos() {
        return failedPhotos;
    }

    public void setFailedPhotos(Integer failedPhotos) {
        this.failedPhotos = failedPhotos;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<PhotoEntity> getPhotos() {
        return photos;
    }

    public void setPhotos(List<PhotoEntity> photos) {
        this.photos = photos;
    }

    public void addPhoto(PhotoEntity photo) {
        photos.add(photo);
        photo.setUploadJob(this);
    }

    public void removePhoto(PhotoEntity photo) {
        photos.remove(photo);
        photo.setUploadJob(null);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
