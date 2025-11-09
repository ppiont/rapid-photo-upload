package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.model.PhotoStatus;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StartPhotoUploadHandler.
 * Tests the photo upload start functionality with mock dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StartPhotoUploadHandler Tests")
class StartPhotoUploadHandlerTest {

    @Mock
    private PhotoRepository photoRepository;

    private StartPhotoUploadHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartPhotoUploadHandler(photoRepository);
    }

    @Test
    @DisplayName("Should successfully mark photo as UPLOADING")
    void shouldMarkPhotoAsUploading() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        String photoId = photo.getId().value().toString();
        StartPhotoUploadCommand command = new StartPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.UPLOADING);
        verify(photoRepository).findById(any(PhotoId.class));
        verify(photoRepository).save(photo);
    }

    @Test
    @DisplayName("Should save photo after marking as started")
    void shouldSavePhotoAfterMarkingAsStarted() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        String photoId = photo.getId().value().toString();
        StartPhotoUploadCommand command = new StartPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));

        ArgumentCaptor<Photo> photoCaptor = ArgumentCaptor.forClass(Photo.class);

        // Act
        handler.handle(command);

        // Assert
        verify(photoRepository).save(photoCaptor.capture());
        Photo savedPhoto = photoCaptor.getValue();
        assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.UPLOADING);
        assertThat(savedPhoto.getUploadStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        String nonExistentPhotoId = UUID.randomUUID().toString();
        StartPhotoUploadCommand command = new StartPhotoUploadCommand(nonExistentPhotoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(photoRepository).findById(any(PhotoId.class));
        verify(photoRepository, never()).save(any(Photo.class));
    }

    @Test
    @DisplayName("Should parse photo ID from command correctly")
    void shouldParsePhotoIdFromCommandCorrectly() {
        // Arrange
        UUID photoUuid = UUID.randomUUID();
        Photo photo = TestDataFactory.photo();
        String photoId = photoUuid.toString();
        StartPhotoUploadCommand command = new StartPhotoUploadCommand(photoId);

        ArgumentCaptor<PhotoId> photoIdCaptor = ArgumentCaptor.forClass(PhotoId.class);
        when(photoRepository.findById(photoIdCaptor.capture())).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert
        PhotoId capturedId = photoIdCaptor.getValue();
        assertThat(capturedId.value()).isEqualTo(photoUuid);
    }

    @Test
    @DisplayName("Should transition photo from PENDING to UPLOADING")
    void shouldTransitionPhotoFromPendingToUploading() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING); // Initial state

        String photoId = photo.getId().value().toString();
        StartPhotoUploadCommand command = new StartPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.UPLOADING);
        assertThat(photo.getUploadStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should call repository methods in correct order")
    void shouldCallRepositoryMethodsInCorrectOrder() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        String photoId = photo.getId().value().toString();
        StartPhotoUploadCommand command = new StartPhotoUploadCommand(photoId);

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert - verify order of invocations
        var inOrder = inOrder(photoRepository);
        inOrder.verify(photoRepository).findById(any(PhotoId.class));
        inOrder.verify(photoRepository).save(any(Photo.class));
    }
}
