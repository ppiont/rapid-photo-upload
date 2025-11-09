package com.demo.photoupload.application.commands;

import com.demo.photoupload.application.dto.InitializeUploadResponseDto;
import com.demo.photoupload.application.dto.PhotoMetadataDto;
import com.demo.photoupload.domain.model.S3Location;
import com.demo.photoupload.domain.model.UploadJob;
import com.demo.photoupload.domain.repository.UploadJobRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InitializeUploadHandler.
 * Tests the initialization of photo uploads with mock dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InitializeUploadHandler Tests")
class InitializeUploadHandlerTest {

    @Mock
    private UploadJobRepository uploadJobRepository;

    @Mock
    private S3Service s3Service;

    private InitializeUploadHandler handler;

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_USER_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        handler = new InitializeUploadHandler(uploadJobRepository, s3Service);

        // Mock S3Service to return test bucket name
        when(s3Service.getBucketName()).thenReturn(TEST_BUCKET);
    }

    @Test
    @DisplayName("Should successfully initialize upload with single photo")
    void shouldInitializeUploadWithSinglePhoto() {
        // Arrange
        PhotoMetadataDto photoDto = new PhotoMetadataDto(
            "test-photo.jpg",
            2_000_000L,
            "image/jpeg"
        );
        List<PhotoMetadataDto> photos = List.of(photoDto);
        InitializeUploadCommand command = new InitializeUploadCommand(TEST_USER_ID, photos);

        // Mock repository to return the saved job
        UploadJob mockJob = TestDataFactory.uploadJob(1);
        when(uploadJobRepository.save(any(UploadJob.class))).thenReturn(mockJob);

        // Mock S3Service to return pre-signed URL
        String testUploadUrl = "https://test-bucket.s3.amazonaws.com/upload-url";
        when(s3Service.generatePresignedUploadUrl(any(S3Location.class))).thenReturn(testUploadUrl);

        // Act
        InitializeUploadResponseDto response = handler.handle(command);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.jobId()).isEqualTo(mockJob.getId().value().toString());
        assertThat(response.totalPhotos()).isEqualTo(1);
        assertThat(response.photos()).hasSize(1);
        assertThat(response.photos().getFirst().uploadUrl()).isEqualTo(testUploadUrl);
        assertThat(response.photos().getFirst().filename()).isNotNull();

        // Verify interactions
        verify(uploadJobRepository).save(any(UploadJob.class));
        verify(s3Service).generatePresignedUploadUrl(any(S3Location.class));
        verify(s3Service).getBucketName();
    }

    @Test
    @DisplayName("Should successfully initialize upload with multiple photos")
    void shouldInitializeUploadWithMultiplePhotos() {
        // Arrange
        List<PhotoMetadataDto> photos = List.of(
            new PhotoMetadataDto("photo-1.jpg", 1_500_000L, "image/jpeg"),
            new PhotoMetadataDto("photo-2.jpg", 2_000_000L, "image/jpeg"),
            new PhotoMetadataDto("photo-3.jpg", 1_800_000L, "image/jpeg")
        );
        InitializeUploadCommand command = new InitializeUploadCommand(TEST_USER_ID, photos);

        // Mock repository
        UploadJob mockJob = TestDataFactory.uploadJob(3);
        when(uploadJobRepository.save(any(UploadJob.class))).thenReturn(mockJob);

        // Mock S3Service
        when(s3Service.generatePresignedUploadUrl(any(S3Location.class)))
            .thenReturn("https://test-url-1")
            .thenReturn("https://test-url-2")
            .thenReturn("https://test-url-3");

        // Act
        InitializeUploadResponseDto response = handler.handle(command);

        // Assert
        assertThat(response.totalPhotos()).isEqualTo(3);
        assertThat(response.photos()).hasSize(3);

        // Verify each photo has a unique upload URL
        assertThat(response.photos())
            .extracting("uploadUrl")
            .contains("https://test-url-1", "https://test-url-2", "https://test-url-3");

        // Verify S3Service was called 3 times for URL generation
        verify(s3Service, times(3)).generatePresignedUploadUrl(any(S3Location.class));
    }

    @Test
    @DisplayName("Should handle large batch upload with 100 photos")
    void shouldHandleLargeBatchUpload() {
        // Arrange
        List<PhotoMetadataDto> photos = createPhotoMetadataList(100);
        InitializeUploadCommand command = new InitializeUploadCommand(TEST_USER_ID, photos);

        // Mock repository
        UploadJob mockJob = TestDataFactory.uploadJob(100);
        when(uploadJobRepository.save(any(UploadJob.class))).thenReturn(mockJob);

        // Mock S3Service
        when(s3Service.generatePresignedUploadUrl(any(S3Location.class)))
            .thenReturn("https://test-upload-url");

        // Act
        InitializeUploadResponseDto response = handler.handle(command);

        // Assert
        assertThat(response.totalPhotos()).isEqualTo(100);
        assertThat(response.photos()).hasSize(100);

        // Verify batch save was called once (not 100 times)
        verify(uploadJobRepository, times(1)).save(any(UploadJob.class));

        // Verify S3 URL generation was called 100 times (once per photo)
        verify(s3Service, times(100)).generatePresignedUploadUrl(any(S3Location.class));
    }

    @Test
    @DisplayName("Should create UploadJob with correct user ID and photos")
    void shouldCreateUploadJobWithCorrectData() {
        // Arrange
        List<PhotoMetadataDto> photos = List.of(
            new PhotoMetadataDto("test.jpg", 2_000_000L, "image/jpeg")
        );
        InitializeUploadCommand command = new InitializeUploadCommand(TEST_USER_ID, photos);

        // Capture the UploadJob being saved
        ArgumentCaptor<UploadJob> jobCaptor = ArgumentCaptor.forClass(UploadJob.class);
        UploadJob mockJob = TestDataFactory.uploadJob(1);
        when(uploadJobRepository.save(jobCaptor.capture())).thenReturn(mockJob);
        when(s3Service.generatePresignedUploadUrl(any(S3Location.class)))
            .thenReturn("https://test-url");

        // Act
        handler.handle(command);

        // Assert - verify the UploadJob passed to repository
        UploadJob capturedJob = jobCaptor.getValue();
        assertThat(capturedJob).isNotNull();
        assertThat(capturedJob.getUserId().value().toString()).isEqualTo(TEST_USER_ID);
        assertThat(capturedJob.getTotalPhotos()).isEqualTo(1);
        assertThat(capturedJob.getPhotos()).hasSize(1);
    }

    @Test
    @DisplayName("Should generate pre-signed URLs for all photos")
    void shouldGeneratePresignedUrlsForAllPhotos() {
        // Arrange
        int photoCount = 5;
        List<PhotoMetadataDto> photos = createPhotoMetadataList(photoCount);
        InitializeUploadCommand command = new InitializeUploadCommand(TEST_USER_ID, photos);

        UploadJob mockJob = TestDataFactory.uploadJob(photoCount);
        when(uploadJobRepository.save(any(UploadJob.class))).thenReturn(mockJob);
        when(s3Service.generatePresignedUploadUrl(any(S3Location.class)))
            .thenReturn("https://test-url");

        // Act
        InitializeUploadResponseDto response = handler.handle(command);

        // Assert - all photos should have upload URLs
        assertThat(response.photos())
            .allMatch(photo -> photo.uploadUrl() != null && !photo.uploadUrl().isEmpty());
    }

    @Test
    @DisplayName("Should use S3 bucket name from service")
    void shouldUseS3BucketNameFromService() {
        // Arrange
        List<PhotoMetadataDto> photos = List.of(
            new PhotoMetadataDto("test.jpg", 2_000_000L, "image/jpeg")
        );
        InitializeUploadCommand command = new InitializeUploadCommand(TEST_USER_ID, photos);

        UploadJob mockJob = TestDataFactory.uploadJob(1);
        when(uploadJobRepository.save(any(UploadJob.class))).thenReturn(mockJob);
        when(s3Service.generatePresignedUploadUrl(any(S3Location.class)))
            .thenReturn("https://test-url");

        // Act
        handler.handle(command);

        // Assert - verify getBucketName was called to get the S3 bucket
        verify(s3Service).getBucketName();
    }

    /**
     * Helper method to create a list of PhotoMetadataDto for testing.
     */
    private List<PhotoMetadataDto> createPhotoMetadataList(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new PhotoMetadataDto(
                "photo-" + i + ".jpg",
                2_000_000L + (i * 100_000L),
                "image/jpeg"
            ))
            .toList();
    }
}
