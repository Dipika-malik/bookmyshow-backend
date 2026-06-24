package com.bookmyshow.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Handles all JWT operations: generation, validation, and claim extraction.
 *
 * HOW JWT WORKS:
 *   A JWT is a Base64-encoded string: header.payload.signature
 *
 *   header  : algorithm + token type    → {"alg":"HS256","typ":"JWT"}
 *   payload : claims (user data)        → {"sub":"user@example.com","iat":...,"exp":...}
 *   signature: HMAC-SHA256(header+payload, secretKey)
 *
 * The signature ensures the token wasn't tampered with.
 * We sign with our secret key; we verify by re-computing the signature.
 *
 * TOKEN FLOW:
 *   1. User logs in → we generate a token with their email as subject
 *   2. Client stores token and sends it in every request header
 *   3. JwtAuthenticationFilter extracts and validates the token
 *   4. If valid, Spring Security sets the authentication context
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration; // milliseconds

    // Derives a cryptographically secure SecretKey from the base64-encoded secret string
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a JWT token from the authenticated user's principal.
     * Called after successful login/registration.
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return buildToken(userDetails.getUsername());
    }

    /**
     * Generates a token directly from an email string.
     * Used after registration when we auto-login the user.
     */
    public String generateToken(String email) {
        return buildToken(email);
    }

    private String buildToken(String subject) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(subject)               // "sub" claim = user's email
                .issuedAt(now)                  // "iat" claim = issued at
                .expiration(expiry)             // "exp" claim = expiry time
                .signWith(getSigningKey())       // HMAC-SHA256 signature
                .compact();
    }

    /** Extracts the email (subject) from a token. */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Validates the token:
     *  1. Signature is correct (not tampered)
     *  2. Token is not expired
     *  3. Token is well-formed
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token); // throws if invalid
            return true;
        } catch (ExpiredJwtException e) {
            // Token expired — user needs to log in again
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // Malformed, unsigned, or otherwise invalid token
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())     // verify the signature
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
