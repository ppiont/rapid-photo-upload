package com.demo.photoupload.web.photos;

import com.demo.photoupload.application.commands.AddPhotoTagsHandler;
import com.demo.photoupload.application.commands.DeletePhotoHandler;
import com.demo.photoupload.application.commands.RemovePhotoTagHandler;
import com.demo.photoupload.application.dto.PhotoDto;
import com.demo.photoupload.application.queries.GetPhotoByIdHandler;
import com.demo.photoupload.application.queries.GetPhotosHandler;
import com.demo.photoupload.application.queries.PhotoQueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests for PhotosController.
 * Tests photo gallery and management endpoints with mocked handlers.
 */
@WebMvcTest(controllers = PhotosController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    })
@ActiveProfiles("test")
@DisplayName("PhotosController Tests")
class PhotosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetPhotosHandler getPhotosHandler;

    @MockBean
    private GetPhotoByIdHandler getPhotoByIdHandler;

    @MockBean
    private AddPhotoTagsHandler addPhotoTagsHandler;

    @MockBean
    private RemovePhotoTagHandler removePhotoTagHandler;

    @MockBean
    private DeletePhotoHandler deletePhotoHandler;

    @Test
    @DisplayName("Should get paginated photos for user")
    @WithMockUser(username = "test-user-id")
    void shouldGetPaginatedPhotos() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        List<PhotoDto> photos = List.of(
            createPhotoDto("photo1", "COMPLETED"),
            createPhotoDto("photo2", "COMPLETED"),
            createPhotoDto("photo3", "PENDING")
        );

        PhotoQueryResult result = new PhotoQueryResult(photos, 25L);
        when(getPhotosHandler.handle(any())).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/api/photos")
                .with(user(userId))
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos").isArray())
            .andExpect(jsonPath("$.photos.length()").value(3))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalCount").value(25));

        verify(getPhotosHandler).handle(any());
    }

    @Test
    @DisplayName("Should get photos without authentication required - returns 401")
    void shouldRequireAuthenticationForGetPhotos() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/photos")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isUnauthorized());

        verify(getPhotosHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Should use default pagination parameters")
    @WithMockUser(username = "test-user-id")
    void shouldUseDefaultPaginationParameters() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        PhotoQueryResult result = new PhotoQueryResult(Collections.emptyList(), 0L);
        when(getPhotosHandler.handle(any())).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/api/photos")
                .with(user(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.hasMore").value(false));

        verify(getPhotosHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle empty photo list")
    @WithMockUser(username = "test-user-id")
    void shouldHandleEmptyPhotoList() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        PhotoQueryResult result = new PhotoQueryResult(Collections.emptyList(), 0L);
        when(getPhotosHandler.handle(any())).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/api/photos")
                .with(user(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos").isEmpty())
            .andExpect(jsonPath("$.totalCount").value(0))
            .andExpect(jsonPath("$.hasMore").value(false));

        verify(getPhotosHandler).handle(any());
    }

    @Test
    @DisplayName("Should get single photo by ID")
    void shouldGetPhotoById() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        PhotoDto photo = createPhotoDto(photoId, "COMPLETED");

        when(getPhotoByIdHandler.handle(any())).thenReturn(photo);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{photoId}", photoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photoId").value(photoId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.downloadUrl").exists());

        verify(getPhotoByIdHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle photo not found")
    void shouldHandlePhotoNotFound() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        when(getPhotoByIdHandler.handle(any()))
            .thenThrow(new IllegalArgumentException("Photo not found"));

        // Act & Assert
        mockMvc.perform(get("/api/photos/{photoId}", photoId))
            .andExpect(status().isBadRequest());

        verify(getPhotoByIdHandler).handle(any());
    }

    @Test
    @DisplayName("Should successfully add tags to photo")
    @WithMockUser(username = "test-user-id")
    void shouldAddTagsToPhoto() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();
        PhotosController.AddTagsRequest request = new PhotosController.AddTagsRequest(
            List.of("vacation", "beach", "sunset")
        );

        doNothing().when(addPhotoTagsHandler).handle(any());

        // Act & Assert
        mockMvc.perform(post("/api/photos/{photoId}/tags", photoId)
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(addPhotoTagsHandler).handle(any());
    }

    @Test
    @DisplayName("Should reject adding tags without authentication")
    void shouldRejectAddingTagsWithoutAuth() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        PhotosController.AddTagsRequest request = new PhotosController.AddTagsRequest(
            List.of("vacation")
        );

        // Act & Assert
        mockMvc.perform(post("/api/photos/{photoId}/tags", photoId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());

        verify(addPhotoTagsHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Should successfully remove tag from photo")
    @WithMockUser(username = "test-user-id")
    void shouldRemoveTagFromPhoto() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();
        String tagName = "vacation";

        doNothing().when(removePhotoTagHandler).handle(any());

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{photoId}/tags/{tagName}", photoId, tagName)
                .with(user(userId)))
            .andExpect(status().isOk());

        verify(removePhotoTagHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle removing tag from non-existent photo")
    @WithMockUser(username = "test-user-id")
    void shouldHandleRemovingTagFromNonExistentPhoto() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();
        String tagName = "vacation";

        doThrow(new IllegalArgumentException("Photo not found"))
            .when(removePhotoTagHandler).handle(any());

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{photoId}/tags/{tagName}", photoId, tagName)
                .with(user(userId)))
            .andExpect(status().isBadRequest());

        verify(removePhotoTagHandler).handle(any());
    }

    @Test
    @DisplayName("Should successfully delete photo")
    @WithMockUser(username = "test-user-id")
    void shouldDeletePhoto() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();

        doNothing().when(deletePhotoHandler).handle(any());

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{photoId}", photoId)
                .with(user(userId)))
            .andExpect(status().isNoContent());

        verify(deletePhotoHandler).handle(any());
    }

    @Test
    @DisplayName("Should reject deleting photo without authentication")
    void shouldRejectDeletingPhotoWithoutAuth() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{photoId}", photoId))
            .andExpect(status().isUnauthorized());

        verify(deletePhotoHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Should handle deleting non-existent photo")
    @WithMockUser(username = "test-user-id")
    void shouldHandleDeletingNonExistentPhoto() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();

        doThrow(new IllegalArgumentException("Photo not found"))
            .when(deletePhotoHandler).handle(any());

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{photoId}", photoId)
                .with(user(userId)))
            .andExpect(status().isBadRequest());

        verify(deletePhotoHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle pagination with custom page size")
    @WithMockUser(username = "test-user-id")
    void shouldHandleCustomPageSize() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        List<PhotoDto> photos = List.of(
            createPhotoDto("photo1", "COMPLETED"),
            createPhotoDto("photo2", "COMPLETED")
        );

        PhotoQueryResult result = new PhotoQueryResult(photos, 100L);
        when(getPhotosHandler.handle(any())).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/api/photos")
                .with(user(userId))
                .param("page", "2")
                .param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(50))
            .andExpect(jsonPath("$.totalCount").value(100))
            .andExpect(jsonPath("$.hasMore").value(false));

        verify(getPhotosHandler).handle(any());
    }

    @Test
    @DisplayName("Should include download URLs for completed photos only")
    @WithMockUser(username = "test-user-id")
    void shouldIncludeDownloadUrlsForCompletedPhotos() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        List<PhotoDto> photos = List.of(
            new PhotoDto("photo1", "photo1.jpg", "original1.jpg", 2_000_000L, "image/jpeg",
                "COMPLETED", "https://s3.amazonaws.com/download-url-1", Instant.now(), Instant.now(), List.of()),
            new PhotoDto("photo2", "photo2.jpg", "original2.jpg", 2_000_000L, "image/jpeg",
                "PENDING", null, Instant.now(), null, List.of())
        );

        PhotoQueryResult result = new PhotoQueryResult(photos, 2L);
        when(getPhotosHandler.handle(any())).thenReturn(result);

        // Act & Assert
        mockMvc.perform(get("/api/photos")
                .with(user(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[0].downloadUrl").exists())
            .andExpect(jsonPath("$.photos[1].downloadUrl").doesNotExist());

        verify(getPhotosHandler).handle(any());
    }

    @Test
    @DisplayName("Should include photo tags in response")
    void shouldIncludePhotoTags() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        PhotoDto photo = new PhotoDto(
            photoId, "photo.jpg", "original.jpg", 2_000_000L, "image/jpeg",
            "COMPLETED", "https://s3.amazonaws.com/download-url",
            Instant.now(), Instant.now(),
            List.of("vacation", "beach", "sunset")
        );

        when(getPhotoByIdHandler.handle(any())).thenReturn(photo);

        // Act & Assert
        mockMvc.perform(get("/api/photos/{photoId}", photoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags").isArray())
            .andExpect(jsonPath("$.tags.length()").value(3))
            .andExpect(jsonPath("$.tags[0]").value("vacation"))
            .andExpect(jsonPath("$.tags[1]").value("beach"))
            .andExpect(jsonPath("$.tags[2]").value("sunset"));

        verify(getPhotoByIdHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle adding empty tag list")
    @WithMockUser(username = "test-user-id")
    void shouldHandleAddingEmptyTagList() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();
        PhotosController.AddTagsRequest request = new PhotosController.AddTagsRequest(
            Collections.emptyList()
        );

        // Act & Assert
        mockMvc.perform(post("/api/photos/{photoId}/tags", photoId)
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(addPhotoTagsHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle tag name with special characters")
    @WithMockUser(username = "test-user-id")
    void shouldHandleTagNameWithSpecialCharacters() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String photoId = UUID.randomUUID().toString();
        String tagName = "été-2024";

        doNothing().when(removePhotoTagHandler).handle(any());

        // Act & Assert
        mockMvc.perform(delete("/api/photos/{photoId}/tags/{tagName}", photoId, tagName)
                .with(user(userId)))
            .andExpect(status().isOk());

        verify(removePhotoTagHandler).handle(any());
    }

    /**
     * Helper method to create a PhotoDto for testing.
     */
    private PhotoDto createPhotoDto(String photoId, String status) {
        return new PhotoDto(
            photoId,
            photoId + ".jpg",
            "original-" + photoId + ".jpg",
            2_000_000L,
            "image/jpeg",
            status,
            status.equals("COMPLETED") ? "https://s3.amazonaws.com/download-url" : null,
            Instant.now(),
            status.equals("COMPLETED") ? Instant.now() : null,
            List.of()
        );
    }
}
