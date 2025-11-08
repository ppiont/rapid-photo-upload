package com.demo.photoupload.infrastructure.s3.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Configuration for AWS S3 client and presigner.
 */
@Configuration
public class S3Config {

    @Value("${aws.region:us-west-1}")
    private String awsRegion;

    /**
     * S3 Client bean for direct S3 operations.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    }

    /**
     * S3 Presigner bean for generating pre-signed URLs.
     * This is used to create upload and download URLs that clients can use directly.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    }
}
