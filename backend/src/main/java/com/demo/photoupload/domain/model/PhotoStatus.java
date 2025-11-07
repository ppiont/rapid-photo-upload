package com.demo.photoupload.domain.model;

/**
 * Enum representing the lifecycle status of a photo upload.
 * State transitions: PENDING → UPLOADING → (COMPLETED | FAILED)
 */
public enum PhotoStatus {
    /**
     * Photo metadata created, awaiting client upload to S3.
     */
    PENDING,

    /**
     * Client has started uploading to S3.
     */
    UPLOADING,

    /**
     * Photo successfully uploaded to S3.
     */
    COMPLETED,

    /**
     * Photo upload failed.
     */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean canTransitionTo(PhotoStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == UPLOADING || newStatus == FAILED;
            case UPLOADING -> newStatus == COMPLETED || newStatus == FAILED;
            case COMPLETED, FAILED -> false; // Terminal states
        };
    }
}
