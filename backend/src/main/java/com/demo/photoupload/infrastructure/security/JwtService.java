package com.demo.photoupload.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Service for JWT token generation and validation.
 * Uses JJWT library (0.13.0) with HS256 signing algorithm.
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationHours;

    public JwtService(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-hours:24}") long expirationHours
    ) {
        // Create secret key from configuration (must be at least 256 bits for HS256)
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    /**
     * Generate a JWT token for a user.
     *
     * @param userId User ID to embed in token
     * @param email User email to embed in token
     * @return JWT token string
     */
    public String generateToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationHours, ChronoUnit.HOURS);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact();
    }

    /**
     * Validate a JWT token and extract claims.
     *
     * @param token JWT token string
     * @return Claims if valid, throws exception if invalid
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Extract user ID from JWT token.
     *
     * @param token JWT token string
     * @return User ID UUID
     */
    public UUID extractUserId(String token) {
        Claims claims = validateAndExtractClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract email from JWT token.
     *
     * @param token JWT token string
     * @return User email
     */
    public String extractEmail(String token) {
        Claims claims = validateAndExtractClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Check if token is valid (not expired).
     *
     * @param token JWT token string
     * @return true if valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = validateAndExtractClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
