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
 * - Batch operations for high-volume uploads (up to 500 photos)
 * - Client-side progress tracking
 * - Optimized for large batch uploads (500 photos @ 2MB each = 1GB)
 */
@SpringBootApplication
public class RapidPhotoUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(RapidPhotoUploadApplication.class, args);
    }
}
