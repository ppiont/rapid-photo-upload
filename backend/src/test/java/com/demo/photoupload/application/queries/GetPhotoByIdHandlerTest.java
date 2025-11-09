package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.PhotoDto;
import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.PhotoId;
import com.demo.photoupload.domain.repository.PhotoRepository;
import com.demo.photoupload.infrastructure.persistence.entity.PhotoTagEntity;
import com.demo.photoupload.infrastructure.persistence.repository.PhotoTagJpaRepository;
import com.demo.photoupload.infrastructure.s3.service.S3Service;
import com.demo.photoupload.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Unit tests for GetPhotoByIdHandler.
 * Tests single photo query with download URL and tags.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetPhotoByIdHandler Tests")
class GetPhotoByIdHandlerTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private PhotoTagJpaRepository photoTagRepository;

    @Mock
    private S3Service s3Service;

    private GetPhotoByIdHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetPhotoByIdHandler(photoRepository, photoTagRepository, s3Service);
    }

    @Test
    @DisplayName("Should successfully get photo by ID with tags")
    void shouldGetPhotoByIdWithTags() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        photo.markAsStarted();
        photo.markAsCompleted();

        UUID photoId = photo.getId().value();
        List<PhotoTagEntity> tags = List.of(
            new PhotoTagEntity(photoId, "vacation"),
            new PhotoTagEntity(photoId, "beach"),
            new PhotoTagEntity(photoId, "summer")
        );

        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(tags);
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoDto result = handler.handle(query);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.photoId()).isEqualTo(photoId.toString());
        assertThat(result.tags()).containsExactlyInAnyOrder("vacation", "beach", "summer");
        assertThat(result.downloadUrl()).isNotNull();

        verify(photoRepository).findById(any(PhotoId.class));
        verify(photoTagRepository).findByPhotoId(photoId);
        verify(s3Service).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should generate download URL for completed photos only")
    void shouldGenerateDownloadUrlForCompletedPhotosOnly() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        photo.markAsStarted();
        photo.markAsCompleted();

        UUID photoId = photo.getId().value();

        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoDto result = handler.handle(query);

        // Assert
        assertThat(result.downloadUrl()).isNotNull();
        verify(s3Service).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should not generate download URL for pending photos")
    void shouldNotGenerateDownloadUrlForPendingPhotos() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        // Photo remains in PENDING status

        UUID photoId = photo.getId().value();

        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());

        // Act
        PhotoDto result = handler.handle(query);

        // Assert
        assertThat(result.downloadUrl()).isNull();
        verify(s3Service, never()).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should throw exception when photo not found")
    void shouldThrowExceptionWhenPhotoNotFound() {
        // Arrange
        UUID photoId = UUID.randomUUID();
        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(query))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Photo not found");

        verify(photoTagRepository, never()).findByPhotoId(any());
        verify(s3Service, never()).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should include all photo details in response")
    void shouldIncludeAllPhotoDetailsInResponse() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        photo.markAsStarted();
        photo.markAsCompleted();

        UUID photoId = photo.getId().value();

        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoDto result = handler.handle(query);

        // Assert
        assertThat(result.photoId()).isEqualTo(photoId.toString());
        assertThat(result.filename()).isEqualTo(photo.getFilename());
        assertThat(result.originalFilename()).isEqualTo(photo.getMetadata().originalFilename());
        assertThat(result.fileSizeBytes()).isEqualTo(photo.getMetadata().fileSizeBytes());
        assertThat(result.mimeType()).isEqualTo(photo.getMetadata().mimeType());
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.downloadUrl()).isNotNull();
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.uploadCompletedAt()).isNotNull();
        assertThat(result.tags()).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception for invalid photo ID format")
    void shouldThrowExceptionForInvalidPhotoIdFormat() {
        // Arrange
        String invalidPhotoId = "not-a-uuid";
        GetPhotoByIdQuery query = new GetPhotoByIdQuery(invalidPhotoId);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(query))
            .isInstanceOf(IllegalArgumentException.class);

        verify(photoRepository, never()).findById(any());
        verify(photoTagRepository, never()).findByPhotoId(any());
        verify(s3Service, never()).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should handle photo with no tags")
    void shouldHandlePhotoWithNoTags() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        photo.markAsStarted();
        photo.markAsCompleted();

        UUID photoId = photo.getId().value();

        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoDto result = handler.handle(query);

        // Assert
        assertThat(result.tags()).isEmpty();
        verify(photoTagRepository).findByPhotoId(photoId);
    }

    @Test
    @DisplayName("Should verify repository call ordering")
    void shouldVerifyRepositoryCallOrdering() {
        // Arrange
        Photo photo = TestDataFactory.photo();
        photo.markAsStarted();
        photo.markAsCompleted();

        UUID photoId = photo.getId().value();

        GetPhotoByIdQuery query = new GetPhotoByIdQuery(photoId.toString());

        when(photoRepository.findById(any(PhotoId.class))).thenReturn(Optional.of(photo));
        when(photoTagRepository.findByPhotoId(photoId)).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        handler.handle(query);

        // Assert - verify calls happen in correct order
        var inOrder = inOrder(photoRepository, photoTagRepository, s3Service);
        inOrder.verify(photoRepository).findById(any(PhotoId.class));
        inOrder.verify(photoTagRepository).findByPhotoId(photoId);
        inOrder.verify(s3Service).generatePresignedDownloadUrl(any());
    }
}
