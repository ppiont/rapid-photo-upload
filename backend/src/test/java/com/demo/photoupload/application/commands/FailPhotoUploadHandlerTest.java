package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.model.*;
import com.demo.photoupload.domain.repository.PhotoRepository;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for FailPhotoUploadHandler.
 * Tests photo failure marking and job status updates.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FailPhotoUploadHandler Tests")
class FailPhotoUploadHandlerTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private UploadJobRepository uploadJobRepository;

    private FailPhotoUploadHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FailPhotoUploadHandler(photoRepository, uploadJobRepository);
    }

    @Test
    @DisplayName("Should successfully mark photo as FAILED and update job")
    void shouldMarkPhotoAsFailedAndUpdateJob() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 1);
        Photo photo = job.getPhotos().getFirst();
        photo.markAsStarted();

        String photoId = photo.getId().value().toString();
        FailPhotoUploadCommand command = new FailPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.FAILED);
        verify(photoRepository).save(photo);
        verify(uploadJobRepository).save(job);
    }

    @Test
    @DisplayName("Should update job to PARTIAL_FAILURE when some photos fail")
    void shouldUpdateJobToPartialFailureWhenSomePhotosFail() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 2);
        Photo photo1 = job.getPhotos().get(0);
        Photo photo2 = job.getPhotos().get(1);

        // Complete first photo successfully
        photo1.markAsStarted();
        photo1.markAsCompleted();

        // About to fail second photo
        photo2.markAsStarted();

        String photoId = photo2.getId().value().toString();
        FailPhotoUploadCommand command = new FailPhotoUploadCommand(photoId);

        when(photoRepository.findById(photo2.getId())).thenReturn(Optional.of(photo2));
        when(uploadJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert
        assertThat(job.getStatus()).isEqualTo(UploadJobStatus.PARTIAL_FAILURE);
        assertThat(job.getCompletedPhotos()).isEqualTo(1);
        assertThat(job.getFailedPhotos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        String nonExistentPhotoId = UUID.randomUUID().toString();
        FailPhotoUploadCommand command = new FailPhotoUploadCommand(nonExistentPhotoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(photoRepository, never()).save(any(Photo.class));
        verify(uploadJobRepository, never()).save(any(UploadJob.class));
    }

    @Test
    @DisplayName("Should transition photo from UPLOADING to FAILED")
    void shouldTransitionPhotoFromUploadingToFailed() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 1);
        Photo photo = job.getPhotos().getFirst();
        photo.markAsStarted();

        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.UPLOADING);

        String photoId = photo.getId().value().toString();
        FailPhotoUploadCommand command = new FailPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.FAILED);
    }

    @Test
    @DisplayName("Should call repositories in correct order")
    void shouldCallRepositoriesInCorrectOrder() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 1);
        Photo photo = job.getPhotos().getFirst();
        photo.markAsStarted();

        String photoId = photo.getId().value().toString();
        FailPhotoUploadCommand command = new FailPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert
        var inOrder = inOrder(photoRepository, uploadJobRepository);
        inOrder.verify(photoRepository).findById(any(PhotoId.class));
        inOrder.verify(photoRepository).save(photo);
        inOrder.verify(uploadJobRepository).findById(photo.getJobId());
        inOrder.verify(uploadJobRepository).save(job);
    }
}
