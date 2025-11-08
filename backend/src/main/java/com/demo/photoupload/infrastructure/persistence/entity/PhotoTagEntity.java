package com.demo.photoupload.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for photo_tags table.
 * Maps to database schema defined in V4__create_photo_tags_table.sql
 */
@Entity
@Table(name = "photo_tags", indexes = {
    @Index(name = "idx_photo_tags_photo", columnList = "photo_id"),
    @Index(name = "idx_photo_tags_tag", columnList = "tag_name")
})
public class PhotoTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "photo_id", nullable = false)
    private UUID photoId;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // JPA requires a no-arg constructor
    protected PhotoTagEntity() {
    }

    public PhotoTagEntity(UUID photoId, String tagName) {
        this.photoId = photoId;
        this.tagName = tagName;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPhotoId() {
        return photoId;
    }

    public void setPhotoId(UUID photoId) {
        this.photoId = photoId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
