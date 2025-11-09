package com.demo.photoupload.application.queries;

import com.demo.photoupload.application.dto.PhotoDto;
import com.demo.photoupload.domain.model.Photo;
import com.demo.photoupload.domain.model.UserId;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GetPhotosHandler.
 * Tests paginated photo queries with download URLs and tags.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetPhotosHandler Tests")
class GetPhotosHandlerTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private PhotoTagJpaRepository photoTagRepository;

    @Mock
    private S3Service s3Service;

    private GetPhotosHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetPhotosHandler(photoRepository, photoTagRepository, s3Service);
    }

    @Test
    @DisplayName("Should return paginated photos for user")
    void shouldReturnPaginatedPhotosForUser() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = TestDataFactory.photoList(5);

        // Mark first 3 as completed
        photos.get(0).markAsStarted();
        photos.get(0).markAsCompleted();
        photos.get(1).markAsStarted();
        photos.get(1).markAsCompleted();
        photos.get(2).markAsStarted();
        photos.get(2).markAsCompleted();

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(25L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(5);
        assertThat(result.totalCount()).isEqualTo(25L);
        verify(photoRepository).findByUserIdWithPagination(any(UserId.class), eq(0), eq(10));
        verify(photoRepository).countByUserId(any(UserId.class));
    }

    @Test
    @DisplayName("Should generate download URLs for completed photos only")
    void shouldGenerateDownloadUrlsForCompletedPhotosOnly() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = TestDataFactory.photoList(3);

        // Only first photo is completed
        photos.get(0).markAsStarted();
        photos.get(0).markAsCompleted();
        // Second photo is still uploading
        photos.get(1).markAsStarted();
        // Third photo is pending

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(3L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(3);
        assertThat(result.photos().get(0).downloadUrl()).isNotNull();
        assertThat(result.photos().get(1).downloadUrl()).isNull();
        assertThat(result.photos().get(2).downloadUrl()).isNull();

        // Should only generate URL for completed photo
        verify(s3Service, times(1)).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should batch-fetch tags for all photos")
    void shouldBatchFetchTagsForAllPhotos() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = TestDataFactory.photoList(3);

        UUID photoId1 = photos.get(0).getId().value();
        UUID photoId2 = photos.get(1).getId().value();

        List<PhotoTagEntity> tags = List.of(
            new PhotoTagEntity(photoId1, "vacation"),
            new PhotoTagEntity(photoId1, "beach"),
            new PhotoTagEntity(photoId2, "nature")
        );

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(3L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(tags);

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(3);

        PhotoDto photo1 = result.photos().get(0);
        PhotoDto photo2 = result.photos().get(1);
        PhotoDto photo3 = result.photos().get(2);

        assertThat(photo1.tags()).containsExactlyInAnyOrder("vacation", "beach");
        assertThat(photo2.tags()).containsExactly("nature");
        assertThat(photo3.tags()).isEmpty();

        // Verify batch fetch was called once with all photo IDs
        verify(photoTagRepository, times(1)).findByPhotoIdIn(anyList());
    }

    @Test
    @DisplayName("Should handle empty result set")
    void shouldHandleEmptyResultSet() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(List.of());
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(0L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0L);
        verify(s3Service, never()).generatePresignedDownloadUrl(any());
    }

    @Test
    @DisplayName("Should handle pagination edge case - first page")
    void shouldHandlePaginationFirstPage() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = TestDataFactory.photoList(10);

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(100L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(10);
        assertThat(result.totalCount()).isEqualTo(100L);
        verify(photoRepository).findByUserIdWithPagination(any(UserId.class), eq(0), eq(10));
    }

    @Test
    @DisplayName("Should handle pagination edge case - last page")
    void shouldHandlePaginationLastPage() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = TestDataFactory.photoList(3); // Last page with only 3 photos

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 9, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(9), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(93L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(3);
        assertThat(result.totalCount()).isEqualTo(93L);
        verify(photoRepository).findByUserIdWithPagination(any(UserId.class), eq(9), eq(10));
    }

    @Test
    @DisplayName("Should include all photo details in response")
    void shouldIncludeAllPhotoDetailsInResponse() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = List.of(TestDataFactory.photo());
        Photo photo = photos.get(0);
        photo.markAsStarted();
        photo.markAsCompleted();

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(1L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());
        when(s3Service.generatePresignedDownloadUrl(any()))
            .thenReturn("https://test-download-url");

        // Act
        PhotoQueryResult result = handler.handle(query);

        // Assert
        assertThat(result.photos()).hasSize(1);
        PhotoDto dto = result.photos().get(0);

        assertThat(dto.photoId()).isEqualTo(photo.getId().value().toString());
        assertThat(dto.filename()).isEqualTo(photo.getFilename());
        assertThat(dto.originalFilename()).isEqualTo(photo.getMetadata().originalFilename());
        assertThat(dto.fileSizeBytes()).isEqualTo(photo.getMetadata().fileSizeBytes());
        assertThat(dto.mimeType()).isEqualTo(photo.getMetadata().mimeType());
        assertThat(dto.status()).isEqualTo("COMPLETED");
        assertThat(dto.downloadUrl()).isNotNull();
        assertThat(dto.createdAt()).isNotNull();
        assertThat(dto.uploadCompletedAt()).isNotNull();
        assertThat(dto.tags()).isEmpty();
    }

    @Test
    @DisplayName("Should verify repository call ordering")
    void shouldVerifyRepositoryCallOrdering() {
        // Arrange
        UserId userId = TestDataFactory.userId();
        List<Photo> photos = TestDataFactory.photoList(2);

        GetPhotosQuery query = new GetPhotosQuery(userId.value().toString(), 0, 10);

        when(photoRepository.findByUserIdWithPagination(any(UserId.class), eq(0), eq(10)))
            .thenReturn(photos);
        when(photoRepository.countByUserId(any(UserId.class))).thenReturn(2L);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());

        // Act
        handler.handle(query);

        // Assert - verify calls happen in correct order
        var inOrder = inOrder(photoRepository, photoTagRepository);
        inOrder.verify(photoRepository).findByUserIdWithPagination(any(UserId.class), eq(0), eq(10));
        inOrder.verify(photoRepository).countByUserId(any(UserId.class));
        inOrder.verify(photoTagRepository).findByPhotoIdIn(anyList());
    }
}
