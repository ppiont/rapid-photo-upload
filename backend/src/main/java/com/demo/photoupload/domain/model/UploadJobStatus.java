package com.demo.photoupload.domain.model;

/**
 * Enum representing the overall status of an upload job.
 * Status is derived from the state of all photos in the job.
 */
public enum UploadJobStatus {
    /**
     * Job is in progress, some photos are still uploading.
     */
    IN_PROGRESS,

    /**
     * All photos successfully uploaded.
     */
    COMPLETED,

    /**
     * Some photos uploaded successfully, but others failed.
     */
    PARTIAL_FAILURE,

    /**
     * All photos failed to upload.
     */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIAL_FAILURE || this == FAILED;
    }
}
