package com.demo.photoupload.application.services;

import com.demo.photoupload.infrastructure.persistence.entity.UserEntity;
import com.demo.photoupload.infrastructure.persistence.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user authentication and registration.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserJpaRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user.
     *
     * @param email User email
     * @param password Plain text password (will be hashed)
     * @param fullName User's full name
     * @return Created user entity
     * @throws IllegalArgumentException if email already exists
     */
    @Transactional
    public UserEntity registerUser(String email, String password, String fullName) {
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(password);

        // Create user entity
        UserEntity user = new UserEntity(email, passwordHash, fullName);
        UserEntity saved = userRepository.save(user);

        logger.info("User registered: {} ({})", email, saved.getId());

        return saved;
    }

    /**
     * Authenticate a user.
     *
     * @param email User email
     * @param password Plain text password
     * @return User entity if authentication successful
     * @throws IllegalArgumentException if authentication fails
     */
    @Transactional(readOnly = true)
    public UserEntity authenticateUser(String email, String password) {
        UserEntity user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new IllegalArgumentException("User account is inactive");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        logger.info("User authenticated: {} ({})", email, user.getId());

        return user;
    }
}
