package com.demo.photoupload.domain.model;

import java.util.Objects;

/**
 * Value Object representing an S3 storage location.
 * Immutable representation of where a photo is stored in S3.
 */
public record S3Location(
    String bucket,
    String key
) {

    public S3Location {
        Objects.requireNonNull(bucket, "S3 bucket cannot be null");
        Objects.requireNonNull(key, "S3 key cannot be null");

        if (bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket cannot be blank");
        }

        if (key.isBlank()) {
            throw new IllegalArgumentException("S3 key cannot be blank");
        }
    }

    /**
     * Create S3Location with photos/ prefix for organizing files.
     */
    public static S3Location forPhoto(String bucket, String filename) {
        return new S3Location(bucket, "photos/" + filename);
    }

    /**
     * Get the full S3 URI.
     */
    public String toUri() {
        return String.format("s3://%s/%s", bucket, key);
    }
}
