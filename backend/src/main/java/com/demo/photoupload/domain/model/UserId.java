package com.demo.photoupload.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a User identifier.
 * Immutable and type-safe ID to prevent mixing with other entity IDs.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "UserId value cannot be null");
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId from(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
