package com.demo.photoupload.application.commands;

import com.demo.photoupload.infrastructure.persistence.entity.PhotoEntity;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoTagEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoJpaRepository;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddPhotoTagsHandler.
 * Tests tag addition with validation and duplicate detection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddPhotoTagsHandler Tests")
class AddPhotoTagsHandlerTest {

    @Mock
    private PhotoJpaRepository photoRepository;

    @Mock
    private PhotoTagJpaRepository photoTagRepository;

    private AddPhotoTagsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddPhotoTagsHandler(photoRepository, photoTagRepository);
    }

    @Test
    @DisplayName("Should successfully add new tags to photo")
    void shouldAddNewTagsToPhoto() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        List<String> tags = List.of("vacation", "beach", "summer");
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());

        // Act
        handler.handle(command);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoTagEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(photoTagRepository).saveAll(captor.capture());

        List<PhotoTagEntity> savedTags = captor.getValue();
        assertThat(savedTags).hasSize(3);
        assertThat(savedTags).extracting("tagName")
            .containsExactlyInAnyOrder("vacation", "beach", "summer");
    }

    @Test
    @DisplayName("Should skip duplicate tags")
    void shouldSkipDuplicateTags() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        // Existing tag
        PhotoTagEntity existingTag = new PhotoTagEntity(photoId, "vacation");
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of(existingTag));

        List<String> tags = List.of("vacation", "beach"); // "vacation" is duplicate
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        // Act
        handler.handle(command);

        // Assert - only "beach" should be saved
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoTagEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(photoTagRepository).saveAll(captor.capture());

        List<PhotoTagEntity> savedTags = captor.getValue();
        assertThat(savedTags).hasSize(1);
        assertThat(savedTags.getFirst().getTagName()).isEqualTo("beach");
    }

    @Test
    @DisplayName("Should trim and validate tag names")
    void shouldTrimAndValidateTagNames() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        List<String> tags = List.of("  vacation  ", "beach");
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());

        // Act
        handler.handle(command);

        // Assert - tags should be trimmed
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoTagEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(photoTagRepository).saveAll(captor.capture());

        List<PhotoTagEntity> savedTags = captor.getValue();
        assertThat(savedTags).extracting("tagName")
            .containsExactlyInAnyOrder("vacation", "beach");
    }

    @Test
    @DisplayName("Should skip empty and blank tags")
    void shouldSkipEmptyAndBlankTags() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        List<String> tags = List.of("vacation", "", "   ", "beach");
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());

        // Act
        handler.handle(command);

        // Assert - only non-empty tags should be saved
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PhotoTagEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(photoTagRepository).saveAll(captor.capture());

        List<PhotoTagEntity> savedTags = captor.getValue();
        assertThat(savedTags).hasSize(2);
        assertThat(savedTags).extracting("tagName")
            .containsExactlyInAnyOrder("vacation", "beach");
    }

    @Test
    @DisplayName("Should reject tags exceeding max length")
    void shouldRejectTagsExceedingMaxLength() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        String longTag = "a".repeat(101); // 101 characters (max is 100)
        List<String> tags = List.of(longTag);
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum length");

        verify(photoTagRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        List<String> tags = List.of("vacation");
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(photoTagRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should throw exception when photo does not belong to user")
    void shouldThrowExceptionWhenPhotoDoesNotBelongToUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, differentUserId);

        List<String> tags = List.of("vacation");
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to user");

        verify(photoTagRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should handle invalid photo ID format")
    void shouldHandleInvalidPhotoIdFormat() {
        // Arrange
        String invalidPhotoId = "not-a-uuid";
        UUID userId = UUID.randomUUID();
        List<String> tags = List.of("vacation");

        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            invalidPhotoId,
            userId.toString(),
            tags
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid photo ID format");

        verify(photoRepository, never()).findById(any());
        verify(photoTagRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should not save when all tags are duplicates")
    void shouldNotSaveWhenAllTagsAreDuplicates() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        PhotoEntity photo = TestDataFactory.photoEntity(photoId, userId);

        // All tags already exist
        List<PhotoTagEntity> existingTags = List.of(
            new PhotoTagEntity(photoId, "vacation"),
            new PhotoTagEntity(photoId, "beach")
        );

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(existingTags);

        List<String> tags = List.of("vacation", "beach");
        AddPhotoTagsCommand command = new AddPhotoTagsCommand(
            photoId.toString(),
            userId.toString(),
            tags
        );

        // Act
        handler.handle(command);

        // Assert - saveAll should not be called when no new tags
        verify(photoTagRepository, never()).saveAll(any());
    }
}
