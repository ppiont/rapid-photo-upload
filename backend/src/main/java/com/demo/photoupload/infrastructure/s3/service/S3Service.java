package com.demo.photoupload.infrastructure.s3.service;

import com.demo.photoupload.domain.model.S3Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * Service for S3 operations, primarily generating pre-signed URLs.
 *
 * Pre-signed URLs allow clients to upload/download directly to/from S3
 * without going through the backend, which is critical for the 100-photo
 * concurrent upload performance requirement.
 */
@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-duration-minutes:60}")
    private int presignedUrlDurationMinutes;

    public S3Service(S3Presigner s3Presigner, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    /**
     * Generate a pre-signed PUT URL for uploading a file to S3.
     * The client can use this URL to upload directly to S3.
     *
     * @param s3Location The S3 location (bucket + key) for the upload
     * @return Pre-signed URL valid for the configured duration (default: 1 hour)
     */
    public String generatePresignedUploadUrl(S3Location s3Location) {
        return generatePresignedUploadUrl(s3Location.bucket(), s3Location.key());
    }

    /**
     * Generate a pre-signed PUT URL for uploading a file to S3.
     *
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @return Pre-signed URL valid for the configured duration (default: 1 hour)
     */
    public String generatePresignedUploadUrl(String bucket, String key) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlDurationMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String url = presignedRequest.url().toString();

            logger.debug("Generated pre-signed upload URL for s3://{}/{} (expires in {} minutes)",
                bucket, key, presignedUrlDurationMinutes);

            return url;
        } catch (Exception e) {
            logger.error("Failed to generate pre-signed upload URL for s3://{}/{}", bucket, key, e);
            throw new RuntimeException("Failed to generate S3 upload URL", e);
        }
    }

    /**
     * Generate a pre-signed GET URL for downloading a file from S3.
     * The client can use this URL to download directly from S3.
     *
     * @param s3Location The S3 location (bucket + key) for the download
     * @return Pre-signed URL valid for the configured duration (default: 1 hour)
     */
    public String generatePresignedDownloadUrl(S3Location s3Location) {
        return generatePresignedDownloadUrl(s3Location.bucket(), s3Location.key());
    }

    /**
     * Generate a pre-signed GET URL for downloading a file from S3.
     *
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @return Pre-signed URL valid for the configured duration (default: 1 hour)
     */
    public String generatePresignedDownloadUrl(String bucket, String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlDurationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            logger.debug("Generated pre-signed download URL for s3://{}/{} (expires in {} minutes)",
                bucket, key, presignedUrlDurationMinutes);

            return url;
        } catch (Exception e) {
            logger.error("Failed to generate pre-signed download URL for s3://{}/{}", bucket, key, e);
            throw new RuntimeException("Failed to generate S3 download URL", e);
        }
    }

    /**
     * Delete an object from S3.
     *
     * @param key The S3 object key to delete
     */
    public void deleteObject(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

            s3Client.deleteObject(deleteObjectRequest);

            logger.info("Deleted object from S3: s3://{}/{}", bucketName, key);
        } catch (Exception e) {
            logger.error("Failed to delete object from S3: s3://{}/{}", bucketName, key, e);
            throw new RuntimeException("Failed to delete S3 object", e);
        }
    }

    /**
     * Get the configured bucket name.
     */
    public String getBucketName() {
        return bucketName;
    }
}
