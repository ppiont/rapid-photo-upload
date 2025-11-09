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
 * Unit tests for CompletePhotoUploadHandler.
 * Tests photo completion and job status update logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompletePhotoUploadHandler Tests")
class CompletePhotoUploadHandlerTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private UploadJobRepository uploadJobRepository;

    private CompletePhotoUploadHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CompletePhotoUploadHandler(photoRepository, uploadJobRepository);
    }

    @Test
    @DisplayName("Should successfully complete photo and update job")
    void shouldCompletePhotoAndUpdateJob() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 1);
        Photo photo = job.getPhotos().getFirst();
        photo.markAsStarted(); // Transition to UPLOADING first

        String photoId = photo.getId().value().toString();
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.COMPLETED);
        assertThat(photo.getUploadCompletedAt()).isNotNull();
        verify(photoRepository).save(photo);
        verify(uploadJobRepository).save(job);
    }

    @Test
    @DisplayName("Should update job status after photo completion")
    void shouldUpdateJobStatusAfterPhotoCompletion() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 2);
        Photo photo1 = job.getPhotos().get(0);
        Photo photo2 = job.getPhotos().get(1);

        // Mark first photo as uploading then complete it
        photo1.markAsStarted();
        photo1.markAsCompleted();

        // Mark second photo as uploading (about to complete it)
        photo2.markAsStarted();

        String photoId = photo2.getId().value().toString();
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(photoId);

        when(photoRepository.findById(photo2.getId())).thenReturn(Optional.of(photo2));
        when(uploadJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert - job should be COMPLETED when all photos are done
        assertThat(job.getStatus()).isEqualTo(UploadJobStatus.COMPLETED);
        assertThat(job.getCompletedPhotos()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        String nonExistentPhotoId = UUID.randomUUID().toString();
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(nonExistentPhotoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(photoRepository).findById(any(PhotoId.class));
        verify(photoRepository, never()).save(any(Photo.class));
        verify(uploadJobRepository, never()).save(any(UploadJob.class));
    }

    @Test
    @DisplayName("Should throw exception when upload job not found")
    void shouldThrowExceptionWhenUploadJobNotFound() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        photo.markAsStarted();

        String photoId = photo.getId().value().toString();
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Upload job not found");

        // Photo should be saved before job lookup fails
        verify(photoRepository).save(photo);
        verify(uploadJobRepository, never()).save(any(UploadJob.class));
    }

    @Test
    @DisplayName("Should transition photo from UPLOADING to COMPLETED")
    void shouldTransitionPhotoFromUploadingToCompleted() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 1);
        Photo photo = job.getPhotos().getFirst();
        photo.markAsStarted();

        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.UPLOADING);

        String photoId = photo.getId().value().toString();
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.COMPLETED);
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
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(uploadJobRepository.findById(photo.getJobId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert - verify order of operations
        var inOrder = inOrder(photoRepository, uploadJobRepository);
        inOrder.verify(photoRepository).findById(any(PhotoId.class));
        inOrder.verify(photoRepository).save(photo);
        inOrder.verify(uploadJobRepository).findById(photo.getJobId());
        inOrder.verify(uploadJobRepository).save(job);
    }

    @Test
    @DisplayName("Should keep job in IN_PROGRESS when some photos are still pending")
    void shouldKeepJobInProgressWhenSomePhotosPending() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        UploadJob job = TestDataFactory.uploadJob(userId, 3);
        Photo photo1 = job.getPhotos().get(0);
        Photo photo2 = job.getPhotos().get(1);
        Photo photo3 = job.getPhotos().get(2);

        // Complete first photo
        photo1.markAsStarted();
        photo1.markAsCompleted();

        // About to complete second photo
        photo2.markAsStarted();

        // Third photo still PENDING

        String photoId = photo2.getId().value().toString();
        CompletePhotoUploadCommand command = new CompletePhotoUploadCommand(photoId);

        when(photoRepository.findById(photo2.getId())).thenReturn(Optional.of(photo2));
        when(uploadJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        // Act
        handler.handle(command);

        // Assert - job should still be IN_PROGRESS
        assertThat(job.getStatus()).isEqualTo(UploadJobStatus.IN_PROGRESS);
        assertThat(job.getCompletedPhotos()).isEqualTo(2);
        assertThat(job.getTotalPhotos()).isEqualTo(3);
    }
}
