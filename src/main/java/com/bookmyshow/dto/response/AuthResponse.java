package com.bookmyshow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned after a successful login or registration.
 *
 * The client stores the token and includes it in subsequent requests:
 *   Authorization: Bearer <token>
 *
 * tokenType is always "Bearer" — standard for JWT-based auth.
 * expiresIn is in seconds (86400 = 24 hours).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long userId;
    private String name;
    private String email;
    private String role;
    private long expiresIn;
}
