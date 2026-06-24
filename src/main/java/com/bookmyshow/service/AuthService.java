package com.bookmyshow.service;

import com.bookmyshow.dto.request.LoginRequest;
import com.bookmyshow.dto.request.RegisterRequest;
import com.bookmyshow.dto.response.AuthResponse;
import com.bookmyshow.enums.Role;
import com.bookmyshow.exception.ResourceNotFoundException;
import com.bookmyshow.model.User;
import com.bookmyshow.repository.UserRepository;
import com.bookmyshow.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login.
 *
 * Registration flow:
 *   1. Validate email is not already taken
 *   2. Hash the password with BCrypt
 *   3. Save user to DB
 *   4. Generate JWT token
 *   5. Return token + user info
 *
 * Login flow:
 *   1. Use AuthenticationManager to validate email + password
 *      (AuthManager internally calls UserDetailsServiceImpl + BCrypt comparison)
 *   2. If valid, generate JWT token
 *   3. Return token + user info
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email '" + request.getEmail() + "' is already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt hash
                .phone(request.getPhone())
                .role(Role.USER) // All self-registrations are USER role
                .build();

        userRepository.save(user);

        // Generate token immediately after registration (auto-login)
        String token = tokenProvider.generateToken(user.getEmail());

        return buildAuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        // This line does the actual authentication:
        // - Calls UserDetailsServiceImpl.loadUserByUsername(email)
        // - Compares request.getPassword() against the stored BCrypt hash
        // - Throws BadCredentialsException if they don't match
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        String token = tokenProvider.generateToken(authentication);

        return buildAuthResponse(user, token);
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .expiresIn(86400) // 24 hours in seconds
                .build();
    }
}
