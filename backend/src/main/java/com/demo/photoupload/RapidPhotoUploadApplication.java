package com.demo.photoupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for RapidPhotoUpload.
 *
 * This application demonstrates a production-grade, high-volume photo upload system
 * using Domain-Driven Design (DDD), Command Query Responsibility Segregation (CQRS),
 * and Vertical Slice Architecture (VSA).
 *
 * Key Features:
 * - Supports 100 concurrent uploads per user via direct S3 uploads
 * - Achieves sub-90-second completion times for 100 photos (200MB)
 * - Uses pre-signed S3 URLs to bypass backend bottlenecks
 * - Implements proper domain modeling with aggregates and value objects
 * - Separates commands and queries for optimal performance
 */
@SpringBootApplication
public class RapidPhotoUploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(RapidPhotoUploadApplication.class, args);
    }
}
