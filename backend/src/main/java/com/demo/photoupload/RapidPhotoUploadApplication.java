package com.demo.photoupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for RapidPhotoUpload.
 *
 * Architecture:
 * - Domain: Pure Java domain logic (DDD aggregates, value objects, enums)
 * - Application: CQRS command/query handlers with @Transactional
 * - Infrastructure: JPA persistence adapters + AWS S3 service
 * - Web: REST controllers following Vertical Slice Architecture
 *
 * Key Performance Features:
 * - Direct S3 uploads via pre-signed URLs (no backend bottleneck)
 * - Batch operations for 100-photo uploads
 * - Client-side progress tracking
 * - Optimized for <90 second upload time for 200MB (100 photos @ 2MB each)
 */
@SpringBootApplication
public class RapidPhotoUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(RapidPhotoUploadApplication.class, args);
    }
}
