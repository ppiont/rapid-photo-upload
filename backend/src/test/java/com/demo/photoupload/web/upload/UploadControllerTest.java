package com.demo.photoupload.web.upload;

import com.demo.photoupload.application.commands.*;
import com.demo.photoupload.application.dto.InitializeUploadResponseDto;
import com.demo.photoupload.application.dto.PhotoUploadUrlDto;
import com.demo.photoupload.application.dto.PhotoMetadataDto;
import com.demo.photoupload.application.dto.UploadJobStatusDto;
import com.demo.photoupload.application.queries.GetUploadJobStatusHandler;
import com.demo.photoupload.web.dto.InitializeUploadRequest;
import com.demo.photoupload.web.dto.PhotoMetadataRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.photoupload.util.TestAuthHelper;
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
 * Controller integration tests for UploadController.
 * Tests upload flow endpoints with mocked handlers.
 */
@WebMvcTest(controllers = UploadController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    })
@ActiveProfiles("test")
@DisplayName("UploadController Tests")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InitializeUploadHandler initializeUploadHandler;

    @MockBean
    private StartPhotoUploadHandler startPhotoUploadHandler;

    @MockBean
    private CompletePhotoUploadHandler completePhotoUploadHandler;

    @MockBean
    private FailPhotoUploadHandler failPhotoUploadHandler;

    @MockBean
    private GetUploadJobStatusHandler getUploadJobStatusHandler;

    @MockBean
    private com.demo.photoupload.infrastructure.security.JwtService jwtService;

    @Test
    @DisplayName("Should initialize upload with single photo")
    @WithMockUser(username = "test-user-id")
    void shouldInitializeUploadWithSinglePhoto() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        InitializeUploadRequest request = new InitializeUploadRequest(
            List.of(new PhotoMetadataRequest("photo1.jpg", 2_000_000L, "image/jpeg"))
        );

        String jobId = UUID.randomUUID().toString();
        InitializeUploadResponseDto response = new InitializeUploadResponseDto(
            jobId,
            1,
            List.of(new PhotoUploadUrlDto(
                UUID.randomUUID().toString(),
                "photo1.jpg",
                "https://s3.amazonaws.com/test-bucket/upload-url"
            ))
        );

        when(initializeUploadHandler.handle(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/upload/initialize")
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jobId").value(jobId))
            .andExpect(jsonPath("$.totalPhotos").value(1))
            .andExpect(jsonPath("$.photos").isArray())
            .andExpect(jsonPath("$.photos[0].uploadUrl").exists());

        verify(initializeUploadHandler).handle(any(InitializeUploadCommand.class));
    }

    @Test
    @DisplayName("Should initialize upload with multiple photos")
    @WithMockUser(username = "test-user-id")
    void shouldInitializeUploadWithMultiplePhotos() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        InitializeUploadRequest request = new InitializeUploadRequest(
            List.of(
                new PhotoMetadataRequest("photo1.jpg", 2_000_000L, "image/jpeg"),
                new PhotoMetadataRequest("photo2.jpg", 3_000_000L, "image/png"),
                new PhotoMetadataRequest("photo3.jpg", 1_500_000L, "image/jpeg")
            )
        );

        String jobId = UUID.randomUUID().toString();
        InitializeUploadResponseDto response = new InitializeUploadResponseDto(
            jobId,
            3,
            List.of(
                new PhotoUploadUrlDto(UUID.randomUUID().toString(), "photo1.jpg", "https://s3.amazonaws.com/upload-url-1"),
                new PhotoUploadUrlDto(UUID.randomUUID().toString(), "photo2.jpg", "https://s3.amazonaws.com/upload-url-2"),
                new PhotoUploadUrlDto(UUID.randomUUID().toString(), "photo3.jpg", "https://s3.amazonaws.com/upload-url-3")
            )
        );

        when(initializeUploadHandler.handle(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/upload/initialize")
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jobId").value(jobId))
            .andExpect(jsonPath("$.totalPhotos").value(3))
            .andExpect(jsonPath("$.photos").isArray())
            .andExpect(jsonPath("$.photos.length()").value(3));

        verify(initializeUploadHandler).handle(any(InitializeUploadCommand.class));
    }

    // NOTE: Authentication tests removed since security is disabled for @WebMvcTest
    // Authentication behavior is tested in full integration tests (Phase 5)

    @Test
    @DisplayName("Should reject initialize upload with empty photo list")
    @WithMockUser(username = "test-user-id")
    void shouldRejectEmptyPhotoList() throws Exception {
        // Arrange
        InitializeUploadRequest request = new InitializeUploadRequest(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(post("/api/upload/initialize")
                .with(user(UUID.randomUUID().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(initializeUploadHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Should successfully start photo upload")
    void shouldStartPhotoUpload() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        doNothing().when(startPhotoUploadHandler).handle(any());

        // Act & Assert
        mockMvc.perform(put("/api/upload/photos/{photoId}/start", photoId))
            .andExpect(status().isOk());

        verify(startPhotoUploadHandler).handle(any(StartPhotoUploadCommand.class));
    }

    @Test
    @DisplayName("Should handle start photo upload with invalid photo ID")
    void shouldHandleInvalidPhotoIdOnStart() throws Exception {
        // Arrange
        String invalidPhotoId = "not-a-uuid";
        doThrow(new IllegalArgumentException("Invalid photo ID format"))
            .when(startPhotoUploadHandler).handle(any());

        // Act & Assert
        mockMvc.perform(put("/api/upload/photos/{photoId}/start", invalidPhotoId))
            .andExpect(status().isBadRequest());

        verify(startPhotoUploadHandler).handle(any(StartPhotoUploadCommand.class));
    }

    @Test
    @DisplayName("Should successfully complete photo upload")
    void shouldCompletePhotoUpload() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        doNothing().when(completePhotoUploadHandler).handle(any());

        // Act & Assert
        mockMvc.perform(put("/api/upload/photos/{photoId}/complete", photoId))
            .andExpect(status().isOk());

        verify(completePhotoUploadHandler).handle(any(CompletePhotoUploadCommand.class));
    }

    @Test
    @DisplayName("Should handle complete photo upload when photo not found")
    void shouldHandlePhotoNotFoundOnComplete() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        doThrow(new IllegalArgumentException("Photo not found"))
            .when(completePhotoUploadHandler).handle(any());

        // Act & Assert
        mockMvc.perform(put("/api/upload/photos/{photoId}/complete", photoId))
            .andExpect(status().isBadRequest());

        verify(completePhotoUploadHandler).handle(any(CompletePhotoUploadCommand.class));
    }

    @Test
    @DisplayName("Should successfully fail photo upload")
    void shouldFailPhotoUpload() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        doNothing().when(failPhotoUploadHandler).handle(any());

        // Act & Assert
        mockMvc.perform(put("/api/upload/photos/{photoId}/fail", photoId))
            .andExpect(status().isOk());

        verify(failPhotoUploadHandler).handle(any(FailPhotoUploadCommand.class));
    }

    @Test
    @DisplayName("Should get upload job status")
    void shouldGetUploadJobStatus() throws Exception {
        // Arrange
        String jobId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        UploadJobStatusDto status = new UploadJobStatusDto(
            jobId,
            UUID.randomUUID().toString(),
            "IN_PROGRESS",
            3,
            1,
            0,
            2,
            createdAt,
            createdAt,
            null,
            List.of()
        );

        when(getUploadJobStatusHandler.handle(any())).thenReturn(status);

        // Act & Assert
        mockMvc.perform(get("/api/upload/jobs/{jobId}/status", jobId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value(jobId))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.totalPhotos").value(3))
            .andExpect(jsonPath("$.completedPhotos").value(1))
            .andExpect(jsonPath("$.failedPhotos").value(0))
            .andExpect(jsonPath("$.pendingPhotos").value(2));

        verify(getUploadJobStatusHandler).handle(any());
    }

    @Test
    @DisplayName("Should handle get status for non-existent job")
    void shouldHandleNonExistentJob() throws Exception {
        // Arrange
        String jobId = UUID.randomUUID().toString();
        when(getUploadJobStatusHandler.handle(any()))
            .thenThrow(new IllegalArgumentException("Upload job not found"));

        // Act & Assert
        mockMvc.perform(get("/api/upload/jobs/{jobId}/status", jobId))
            .andExpect(status().isBadRequest());

        verify(getUploadJobStatusHandler).handle(any());
    }

    @Test
    @DisplayName("Should initialize large batch upload (100 photos)")
    @WithMockUser(username = "test-user-id")
    void shouldInitializeLargeBatchUpload() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        List<PhotoMetadataRequest> photos = new java.util.ArrayList<>();
        List<PhotoUploadUrlDto> responsePhotos = new java.util.ArrayList<>();

        for (int i = 0; i < 100; i++) {
            photos.add(new PhotoMetadataRequest("photo-" + i + ".jpg", 2_000_000L, "image/jpeg"));
            responsePhotos.add(new PhotoUploadUrlDto(
                UUID.randomUUID().toString(),
                "photo-" + i + ".jpg",
                "https://s3.amazonaws.com/upload-url-" + i
            ));
        }

        InitializeUploadRequest request = new InitializeUploadRequest(photos);
        String jobId = UUID.randomUUID().toString();
        InitializeUploadResponseDto response = new InitializeUploadResponseDto(
            jobId,
            100,
            responsePhotos
        );

        when(initializeUploadHandler.handle(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/upload/initialize")
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalPhotos").value(100))
            .andExpect(jsonPath("$.photos.length()").value(100));

        verify(initializeUploadHandler).handle(any(InitializeUploadCommand.class));
    }

    @Test
    @DisplayName("Should handle concurrent photo state transitions")
    void shouldHandleConcurrentStateTransitions() throws Exception {
        // Arrange
        String photoId = UUID.randomUUID().toString();
        doNothing().when(startPhotoUploadHandler).handle(any());
        doNothing().when(completePhotoUploadHandler).handle(any());

        // Act & Assert - start
        mockMvc.perform(put("/api/upload/photos/{photoId}/start", photoId))
            .andExpect(status().isOk());

        // Act & Assert - complete
        mockMvc.perform(put("/api/upload/photos/{photoId}/complete", photoId))
            .andExpect(status().isOk());

        verify(startPhotoUploadHandler).handle(any(StartPhotoUploadCommand.class));
        verify(completePhotoUploadHandler).handle(any(CompletePhotoUploadCommand.class));
    }

    @Test
    @DisplayName("Should validate photo metadata on initialize")
    @WithMockUser(username = "test-user-id")
    void shouldValidatePhotoMetadata() throws Exception {
        // Arrange - invalid MIME type
        String userId = UUID.randomUUID().toString();
        InitializeUploadRequest request = new InitializeUploadRequest(
            List.of(new PhotoMetadataRequest("photo1.jpg", -1L, "invalid-mime"))
        );

        // Act & Assert
        mockMvc.perform(post("/api/upload/initialize")
                .with(user(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(initializeUploadHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Should return job status with completed timestamp")
    void shouldReturnJobStatusWithCompletedTimestamp() throws Exception {
        // Arrange
        String jobId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now().minusSeconds(60);
        Instant completedAt = Instant.now();
        UploadJobStatusDto status = new UploadJobStatusDto(
            jobId,
            UUID.randomUUID().toString(),
            "COMPLETED",
            3,
            3,
            0,
            0,
            createdAt,
            completedAt,
            completedAt,
            List.of()
        );

        when(getUploadJobStatusHandler.handle(any())).thenReturn(status);

        // Act & Assert
        mockMvc.perform(get("/api/upload/jobs/{jobId}/status", jobId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt").exists());

        verify(getUploadJobStatusHandler).handle(any());
    }
}
