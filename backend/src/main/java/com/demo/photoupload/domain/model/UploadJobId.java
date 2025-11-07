package com.demo.photoupload.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing an UploadJob identifier.
 * Immutable and type-safe ID to prevent mixing with other entity IDs.
 */
public record UploadJobId(UUID value) {

    public UploadJobId {
        Objects.requireNonNull(value, "UploadJobId value cannot be null");
    }

    public static UploadJobId generate() {
        return new UploadJobId(UUID.randomUUID());
    }

    public static UploadJobId from(String value) {
        return new UploadJobId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
