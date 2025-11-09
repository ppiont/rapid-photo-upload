package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.UploadJobStatusDto;
import com.demo.photoupload.domain.model.*;
import com.demo.photoupload.domain.repository.UploadJobRepository;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GetUploadJobStatusHandler.
 * Tests job status query functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetUploadJobStatusHandler Tests")
class GetUploadJobStatusHandlerTest {

    @Mock
    private UploadJobRepository uploadJobRepository;

    private GetUploadJobStatusHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetUploadJobStatusHandler(uploadJobRepository);
    }

    @Test
    @DisplayName("Should successfully retrieve upload job status")
    void shouldRetrieveUploadJobStatus() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 3);
        String jobId = job.getId().value().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(jobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.of(job));

        // Act
        UploadJobStatusDto result = handler.handle(query);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(result.userId()).isEqualTo(userId.value().toString());
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.totalPhotos()).isEqualTo(3);
        assertThat(result.photos()).hasSize(3);

        verify(uploadJobRepository).findById(any(UploadJobId.class));
    }

    @Test
    @DisplayName("Should return correct photo statuses in job status")
    void shouldReturnCorrectPhotoStatusesInJobStatus() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 3);
        Photo photo1 = job.getPhotos().get(0);
        Photo photo2 = job.getPhotos().get(1);
        Photo photo3 = job.getPhotos().get(2);

        // Set different statuses
        photo1.markAsStarted();
        photo1.markAsCompleted();
        photo2.markAsStarted();
        // photo3 remains PENDING

        String jobId = job.getId().value().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(jobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.of(job));

        // Act
        UploadJobStatusDto result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(3);
        assertThat(result.photos().get(0).status()).isEqualTo("COMPLETED");
        assertThat(result.photos().get(1).status()).isEqualTo("UPLOADING");
        assertThat(result.photos().get(2).status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should return completed job status when all photos done")
    void shouldReturnCompletedJobStatusWhenAllPhotosDone() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 2);

        // Complete both photos
        job.getPhotos().forEach(photo -> {
            photo.markAsStarted();
            photo.markAsCompleted();
        });
        job.checkAndUpdateStatus();

        String jobId = job.getId().value().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(jobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.of(job));

        // Act
        UploadJobStatusDto result = handler.handle(query);

        // Assert
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.completedPhotos()).isEqualTo(2);
        assertThat(result.failedPhotos()).isEqualTo(0);
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return partial failure status when some photos failed")
    void shouldReturnPartialFailureStatusWhenSomePhotosFailed() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 3);

        // Complete one, fail one, leave one pending
        Photo photo1 = job.getPhotos().get(0);
        Photo photo2 = job.getPhotos().get(1);

        photo1.markAsStarted();
        photo1.markAsCompleted();

        photo2.markAsStarted();
        photo2.markAsFailed();

        job.checkAndUpdateStatus();

        String jobId = job.getId().value().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(jobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.of(job));

        // Act
        UploadJobStatusDto result = handler.handle(query);

        // Assert
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.completedPhotos()).isEqualTo(1);
        assertThat(result.failedPhotos()).isEqualTo(1);
        assertThat(result.pendingPhotos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw exception when job not found")
    void shouldThrowExceptionWhenJobNotFound() {
        // Arrange
        String nonExistentJobId = UUID.randomUUID().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(nonExistentJobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(query))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Upload job not found");

        verify(uploadJobRepository).findById(any(UploadJobId.class));
    }

    @Test
    @DisplayName("Should include all photo details in response")
    void shouldIncludeAllPhotoDetailsInResponse() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 1);
        Photo photo = job.getPhotos().getFirst();
        photo.markAsStarted();

        String jobId = job.getId().value().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(jobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.of(job));

        // Act
        UploadJobStatusDto result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(1);
        var photoDto = result.photos().getFirst();
        assertThat(photoDto.photoId()).isEqualTo(photo.getId().value().toString());
        assertThat(photoDto.filename()).isNotNull();
        assertThat(photoDto.status()).isEqualTo("UPLOADING");
        assertThat(photoDto.fileSizeBytes()).isEqualTo(2_000_000L);
        assertThat(photoDto.createdAt()).isNotNull();
        assertThat(photoDto.uploadStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle job with no photos started")
    void shouldHandleJobWithNoPhotosStarted() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 5);
        // All photos remain in PENDING status

        String jobId = job.getId().value().toString();
        GetUploadJobStatusQuery query = new GetUploadJobStatusQuery(jobId);

        when(uploadJobRepository.findById(any(UploadJobId.class))).thenReturn(Optional.of(job));

        // Act
        UploadJobStatusDto result = handler.handle(query);

        // Assert
        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        assertThat(result.completedPhotos()).isEqualTo(0);
        assertThat(result.failedPhotos()).isEqualTo(0);
        assertThat(result.pendingPhotos()).isEqualTo(5);
        assertThat(result.photos()).allMatch(p -> p.status().equals("PENDING"));
    }
}
