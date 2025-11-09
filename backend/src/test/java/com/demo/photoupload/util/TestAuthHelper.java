package com.demo.photoupload.util;

import com.demo.photoupload.infrastructure.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class for generating JWT tokens in tests.
 * <p>
 * Provides methods to create test JWT tokens without requiring a full JwtService instance.
 * Useful for integration tests where you need to make authenticated requests.
 */
public class TestAuthHelper {

    /**
     * Test JWT secret - matches the secret in application-test.yml
     */
    private static final String TEST_SECRET = "test-secret-key-for-integration-tests-min-256-bits-aaaaaaaaaaaaaa";

    /**
     * Secret key for signing JWTs
     */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            TEST_SECRET.getBytes(StandardCharsets.UTF_8)
    );

    /**
     * Token expiration time in milliseconds (1 hour)
     */
    private static final long EXPIRATION_TIME = 3600000L;

    /**
     * Generate a JWT token for the specified user ID.
     *
     * @param userId User ID to include in the token
     * @return JWT token string
     */
    public static String generateToken(String userId) {
        return generateToken(userId, "test@example.com", EXPIRATION_TIME);
    }

    /**
     * Generate a JWT token for the specified user ID and email.
     *
     * @param userId User ID to include in the token
     * @param email  User email to include in the token
     * @return JWT token string
     */
    public static String generateToken(String userId, String email) {
        return generateToken(userId, email, EXPIRATION_TIME);
    }

    /**
     * Generate a JWT token for the specified user ID with custom expiration.
     *
     * @param userId         User ID to include in the token
     * @param email          User email to include in the token
     * @param expirationMs   Expiration time in milliseconds from now
     * @return JWT token string
     */
    public static String generateToken(String userId, String email, long expirationMs) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Generate a JWT token for a random UUID user ID.
     *
     * @return JWT token string
     */
    public static String generateToken() {
        return generateToken(UUID.randomUUID().toString());
    }

    /**
     * Generate an expired JWT token (useful for testing token expiry).
     *
     * @param userId User ID to include in the token
     * @return Expired JWT token string
     */
    public static String generateExpiredToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", "test@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 2 * EXPIRATION_TIME))
                .expiration(new Date(System.currentTimeMillis() - EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Create an Authorization header value with Bearer token.
     *
     * @param userId User ID to include in the token
     * @return Authorization header value (e.g., "Bearer eyJhbGc...")
     */
    public static String authHeader(String userId) {
        return "Bearer " + generateToken(userId);
    }

    /**
     * Create an Authorization header value for a random user.
     *
     * @return Authorization header value
     */
    public static String authHeader() {
        return "Bearer " + generateToken();
    }

    /**
     * Extract user ID from a JWT token.
     *
     * @param token JWT token
     * @return User ID
     */
    public static String extractUserId(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Validate if a token is valid (not expired, correct signature).
     *
     * @param token JWT token
     * @return true if valid, false otherwise
     */
    public static boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
