package com.demo.photoupload.web.dto;

/**
 * Response DTO for user registration.
 */
public record RegisterResponse(
    String userId,
    String email,
    String fullName,
    String token
) {
}
