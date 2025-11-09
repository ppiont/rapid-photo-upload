package com.demo.photoupload.application.commands;

import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeletePhotoHandler.
 * Tests photo deletion with S3 cleanup and cascade tag deletion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeletePhotoHandler Tests")
class DeletePhotoHandlerTest {

    @Mock
    private PhotoJpaRepository photoJpaRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private PhotoTagJpaRepository photoTagRepository;

    @Mock
    private S3Service s3Service;

    private DeletePhotoHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeletePhotoHandler(photoJpaRepository, photoRepository, photoTagRepository, s3Service);
    }

    @Test
    @DisplayName("Should successfully delete photo with S3 cleanup")
    void shouldDeletePhotoWithS3Cleanup() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            userId.toString()
        );

        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert
        InOrder inOrder = inOrder(s3Service, photoTagRepository, photoRepository);
        inOrder.verify(s3Service).deleteObject(photo.getS3Key());
        inOrder.verify(photoTagRepository).deleteByPhotoId(photoId);
        inOrder.verify(photoRepository).deleteById(any(PhotoId.class));
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            userId.toString()
        );

        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(s3Service, never()).deleteObject(any());
        verify(photoTagRepository, never()).deleteByPhotoId(any());
        verify(photoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should throw exception when photo does not belong to user")
    void shouldThrowExceptionWhenPhotoDoesNotBelongToUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, differentUserId);

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            userId.toString()
        );

        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to user");

        verify(s3Service, never()).deleteObject(any());
        verify(photoTagRepository, never()).deleteByPhotoId(any());
        verify(photoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should continue deletion even if S3 deletion fails")
    void shouldContinueDeletionEvenIfS3DeletionFails() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            userId.toString()
        );

        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.of(photo));
        doThrow(new RuntimeException("S3 service unavailable")).when(s3Service).deleteObject(any());

        // Act - should not throw exception
        handler.handle(command);

        // Assert - database cleanup should still happen
        verify(s3Service).deleteObject(photo.getS3Key());
        verify(photoTagRepository).deleteByPhotoId(photoId);
        verify(photoRepository).deleteById(any(PhotoId.class));
    }

    @Test
    @DisplayName("Should verify correct operation ordering")
    void shouldVerifyCorrectOperationOrdering() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            userId.toString()
        );

        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert - operations must happen in this order
        InOrder inOrder = inOrder(photoJpaRepository, s3Service, photoTagRepository, photoRepository);
        inOrder.verify(photoJpaRepository).findById(photoId);
        inOrder.verify(s3Service).deleteObject(photo.getS3Key());
        inOrder.verify(photoTagRepository).deleteByPhotoId(photoId);
        inOrder.verify(photoRepository).deleteById(any(PhotoId.class));
    }

    @Test
    @DisplayName("Should throw exception for invalid photo ID format")
    void shouldThrowExceptionForInvalidPhotoIdFormat() {
        // Arrange
        String invalidPhotoId = "not-a-uuid";
        UUID userId = UUID.randomUUID();

        DeletePhotoCommand command = new DeletePhotoCommand(
            invalidPhotoId,
            userId.toString()
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid photo ID format");

        verify(photoJpaRepository, never()).findById(any());
        verify(s3Service, never()).deleteObject(any());
        verify(photoTagRepository, never()).deleteByPhotoId(any());
        verify(photoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should throw exception for invalid user ID format")
    void shouldThrowExceptionForInvalidUserIdFormat() {
        // Arrange
        UUID photoId = UUID.randomUUID();
        String invalidUserId = "not-a-uuid";

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            invalidUserId
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid user ID format");

        verify(photoJpaRepository, never()).findById(any());
        verify(s3Service, never()).deleteObject(any());
        verify(photoTagRepository, never()).deleteByPhotoId(any());
        verify(photoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should delete photo even when S3 key is null")
    void shouldDeletePhotoEvenWhenS3KeyIsNull() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        // Simulate photo with no S3 key (never uploaded)
        photo.setS3Key(null);

        DeletePhotoCommand command = new DeletePhotoCommand(
            photoId.toString(),
            userId.toString()
        );

        when(photoJpaRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert - S3 delete should NOT be called
        verify(s3Service, never()).deleteObject(any());

        // But tags and photo should still be deleted
        verify(photoTagRepository).deleteByPhotoId(photoId);
        verify(photoRepository).deleteById(any(PhotoId.class));
    }
}
