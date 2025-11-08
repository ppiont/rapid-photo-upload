package com.demo.photoupload.web.auth;

import com.demo.photoupload.application.services.UserService;
import com.demo.photoupload.infrastructure.persistence.entity.UserEntity;
import com.demo.photoupload.infrastructure.security.JwtService;
import com.demo.photoupload.web.dto.LoginRequest;
import com.demo.photoupload.web.dto.LoginResponse;
import com.demo.photoupload.web.dto.RegisterRequest;
import com.demo.photoupload.web.dto.RegisterResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication.
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // TODO: Restrict in production
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * Register a new user.
     *
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Registering user: {}", request.email());

        // Register user
        UserEntity user = userService.registerUser(
            request.email(),
            request.password(),
            request.fullName()
        );

        // Generate JWT token
        String token = jwtService.generateToken(user.getId(), user.getEmail());

        RegisterResponse response = new RegisterResponse(
            user.getId().toString(),
            user.getEmail(),
            user.getFullName(),
            token
        );

        logger.info("User registered successfully: {} ({})", user.getEmail(), user.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Login existing user.
     *
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("User login attempt: {}", request.email());

        // Authenticate user
        UserEntity user = userService.authenticateUser(request.email(), request.password());

        // Generate JWT token
        String token = jwtService.generateToken(user.getId(), user.getEmail());

        LoginResponse response = new LoginResponse(
            user.getId().toString(),
            user.getEmail(),
            user.getFullName(),
            token
        );

        logger.info("User logged in successfully: {} ({})", user.getEmail(), user.getId());

        return ResponseEntity.ok(response);
    }
}
