package com.demo.photoupload.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a Photo identifier.
 * Immutable and type-safe ID to prevent mixing with other entity IDs.
 */
public record PhotoId(UUID value) {

    public PhotoId {
        Objects.requireNonNull(value, "PhotoId value cannot be null");
    }

    public static PhotoId generate() {
        return new PhotoId(UUID.randomUUID());
    }

    public static PhotoId from(String value) {
        return new PhotoId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
