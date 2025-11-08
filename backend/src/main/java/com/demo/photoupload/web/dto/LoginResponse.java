package com.demo.photoupload.web.dto;

/**
 * Response DTO for user login.
 */
public record LoginResponse(
    String userId,
    String email,
    String fullName,
    String token
) {
}
