package com.demo.photoupload.application.commands;

import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RemovePhotoTagHandler.
 * Tests tag removal with idempotent behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RemovePhotoTagHandler Tests")
class RemovePhotoTagHandlerTest {

    @Mock
    private PhotoJpaRepository photoRepository;

    @Mock
    private PhotoTagJpaRepository photoTagRepository;

    private RemovePhotoTagHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemovePhotoTagHandler(photoRepository, photoTagRepository);
    }

    @Test
    @DisplayName("Should successfully remove tag from photo")
    void shouldRemoveTagFromPhoto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        String tagName = "vacation";
        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            photoId.toString(),
            userId.toString(),
            tagName
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert
        verify(photoTagRepository).deleteByPhotoIdAndTagName(photoId, tagName);
    }

    @Test
    @DisplayName("Should be idempotent when tag does not exist")
    void shouldBeIdempotentWhenTagDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        String tagName = "nonexistent";
        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            photoId.toString(),
            userId.toString(),
            tagName
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act - should not throw exception
        handler.handle(command);

        // Assert - deleteByPhotoIdAndTagName is still called (idempotent)
        verify(photoTagRepository).deleteByPhotoIdAndTagName(photoId, tagName);
    }

    @Test
    @DisplayName("Should trim tag name before removal")
    void shouldTrimTagNameBeforeRemoval() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        String tagNameWithSpaces = "  vacation  ";
        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            photoId.toString(),
            userId.toString(),
            tagNameWithSpaces
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act
        handler.handle(command);

        // Assert - trimmed tag name should be used
        verify(photoTagRepository).deleteByPhotoIdAndTagName(photoId, "vacation");
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            photoId.toString(),
            userId.toString(),
            "vacation"
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(photoTagRepository, never()).deleteByPhotoIdAndTagName(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when photo does not belong to user")
    void shouldThrowExceptionWhenPhotoDoesNotBelongToUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, differentUserId);

        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            photoId.toString(),
            userId.toString(),
            "vacation"
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to user");

        verify(photoTagRepository, never()).deleteByPhotoIdAndTagName(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for empty tag name")
    void shouldThrowExceptionForEmptyTagName() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            photoId.toString(),
            userId.toString(),
            "   "
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Tag name cannot be empty");

        verify(photoRepository, never()).findById(any());
        verify(photoTagRepository, never()).deleteByPhotoIdAndTagName(any(), any());
    }

    @Test
    @DisplayName("Should throw exception for invalid photo ID format")
    void shouldThrowExceptionForInvalidPhotoIdFormat() {
        // Arrange
        String invalidPhotoId = "not-a-uuid";
        UUID userId = UUID.randomUUID();

        RemovePhotoTagCommand command = new RemovePhotoTagCommand(
            invalidPhotoId,
            userId.toString(),
            "vacation"
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid photo ID format");

        verify(photoRepository, never()).findById(any());
        verify(photoTagRepository, never()).deleteByPhotoIdAndTagName(any(), any());
    }
}
