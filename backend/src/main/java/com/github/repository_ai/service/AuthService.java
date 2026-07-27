package com.github.repository_ai.service;

import com.github.repository_ai.dto.*;
import com.github.repository_ai.entity.User;
import com.github.repository_ai.exception.ResourceNotFoundException;
import com.github.repository_ai.repository.UserRepository;
import com.github.repository_ai.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling user registration, authentication, and JWT token issuance.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Registers a new user and issues a JWT token.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("[AUTH] Registration attempt for email '{}'", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("An account with email '" + request.email() + "' already exists.");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username '" + request.username() + "' is already taken.");
        }

        User user = User.builder()
                .username(request.username().trim())
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User savedUser = userRepository.save(user);
        log.info("[AUTH] User registered successfully with id={}", savedUser.getId());

        String token = tokenProvider.generateToken(savedUser.getId(), savedUser.getEmail());
        UserDto userDto = toUserDto(savedUser);

        return new AuthResponse(token, userDto);
    }

    /**
     * Authenticates a user with email & password and issues a JWT token.
     */
    public AuthResponse login(LoginRequest request) {
        log.info("[AUTH] Login attempt for email '{}'", request.email());

        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("[AUTH] Failed password match for email '{}'", request.email());
            throw new IllegalArgumentException("Invalid email or password.");
        }

        log.info("[AUTH] User authenticated successfully id={}", user.getId());

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        UserDto userDto = toUserDto(user);

        return new AuthResponse(token, userDto);
    }

    /**
     * Maps User entity to UserDto.
     */
    public UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
