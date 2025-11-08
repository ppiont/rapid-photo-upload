package com.demo.photoupload.application.queries;

/**
 * Query to get upload job status (for polling).
 */
public record GetUploadJobStatusQuery(
    String jobId
) {
}
