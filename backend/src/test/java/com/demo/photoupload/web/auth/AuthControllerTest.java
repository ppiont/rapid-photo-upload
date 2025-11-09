package com.demo.photoupload.web.auth;

import com.demo.photoupload.application.services.UserService;
import com.demo.photoupload.infrastructure.persistence.entity.UserEntity;
import com.demo.photoupload.infrastructure.security.JwtService;
import com.demo.photoupload.web.dto.LoginRequest;
import com.demo.photoupload.web.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller integration tests for AuthController.
 * Uses @WebMvcTest for focused controller testing with mocked dependencies.
 */
@WebMvcTest(controllers = AuthController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
    })
@ActiveProfiles("test")
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("Should successfully register a new user")
    void shouldRegisterNewUser() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "SecurePassword123",
            "John Doe"
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity("test@example.com", "hashed-password", "John Doe");
        user.setId(userId);

        when(userService.registerUser(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn("test-jwt-token");

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.fullName").value("John Doe"))
            .andExpect(jsonPath("$.token").value("test-jwt-token"));

        verify(userService).registerUser("test@example.com", "SecurePassword123", "John Doe");
        verify(jwtService).generateToken(userId, "test@example.com");
    }

    @Test
    @DisplayName("Should reject registration with duplicate email")
    void shouldRejectDuplicateEmail() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
            "duplicate@example.com",
            "SecurePassword123",
            "Jane Doe"
        );

        when(userService.registerUser(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Email already exists"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(userService).registerUser("duplicate@example.com", "SecurePassword123", "Jane Doe");
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Should reject registration with invalid email format")
    void shouldRejectInvalidEmailFormat() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
            "not-an-email",
            "SecurePassword123",
            "John Doe"
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(userService, never()).registerUser(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject registration with missing required fields")
    void shouldRejectMissingRequiredFields() throws Exception {
        // Arrange - missing password field
        String invalidJson = "{\"email\":\"test@example.com\",\"fullName\":\"John Doe\"}";

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());

        verify(userService, never()).registerUser(any(), any(), any());
    }

    @Test
    @DisplayName("Should successfully login existing user")
    void shouldLoginExistingUser() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest(
            "test@example.com",
            "SecurePassword123"
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity("test@example.com", "hashed-password", "John Doe");
        user.setId(userId);

        when(userService.authenticateUser(anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn("test-jwt-token");

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.fullName").value("John Doe"))
            .andExpect(jsonPath("$.token").value("test-jwt-token"));

        verify(userService).authenticateUser("test@example.com", "SecurePassword123");
        verify(jwtService).generateToken(userId, "test@example.com");
    }

    @Test
    @DisplayName("Should reject login with invalid credentials")
    void shouldRejectInvalidCredentials() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest(
            "test@example.com",
            "WrongPassword"
        );

        when(userService.authenticateUser(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(userService).authenticateUser("test@example.com", "WrongPassword");
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("Should reject login for non-existent user")
    void shouldRejectNonExistentUser() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest(
            "nonexistent@example.com",
            "Password123"
        );

        when(userService.authenticateUser(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("User not found"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(userService).authenticateUser("nonexistent@example.com", "Password123");
    }

    @Test
    @DisplayName("Should reject login with missing credentials")
    void shouldRejectMissingCredentials() throws Exception {
        // Arrange - missing password
        String invalidJson = "{\"email\":\"test@example.com\"}";

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());

        verify(userService, never()).authenticateUser(any(), any());
    }

    @Test
    @DisplayName("Should return JWT token in correct format")
    void shouldReturnJwtTokenInCorrectFormat() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest(
            "test@example.com",
            "SecurePassword123"
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity("test@example.com", "hashed-password", "John Doe");
        user.setId(userId);

        String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        when(userService.authenticateUser(anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn(jwtToken);

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(jwtToken))
            .andExpect(jsonPath("$.token").value(notNullValue()));

        verify(jwtService).generateToken(userId, "test@example.com");
    }

    @Test
    @DisplayName("Should handle registration with special characters in name")
    void shouldHandleSpecialCharactersInName() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "SecurePassword123",
            "José María O'Brien-Smith"
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity("test@example.com", "hashed-password", "José María O'Brien-Smith");
        user.setId(userId);

        when(userService.registerUser(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn("test-jwt-token");

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fullName").value("José María O'Brien-Smith"));

        verify(userService).registerUser("test@example.com", "SecurePassword123", "José María O'Brien-Smith");
    }

    @Test
    @DisplayName("Should handle registration with long email")
    void shouldHandleRegistrationWithLongEmail() throws Exception {
        // Arrange
        String longEmail = "verylongemailaddress.with.many.dots@subdomain.example.com";
        RegisterRequest request = new RegisterRequest(
            longEmail,
            "SecurePassword123",
            "John Doe"
        );

        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(longEmail, "hashed-password", "John Doe");
        user.setId(userId);

        when(userService.registerUser(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn("test-jwt-token");

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(longEmail));

        verify(userService).registerUser(longEmail, "SecurePassword123", "John Doe");
    }
}
