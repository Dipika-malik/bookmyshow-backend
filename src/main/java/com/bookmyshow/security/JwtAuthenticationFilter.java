package com.bookmyshow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs ONCE per HTTP request.
 *
 * WHAT IT DOES (in order):
 *   1. Extracts the JWT from the "Authorization: Bearer <token>" header
 *   2. Validates the token (signature, expiry)
 *   3. Loads the user from DB using the email in the token
 *   4. Sets the user as authenticated in Spring's SecurityContext
 *
 * WHY OncePerRequestFilter?
 *   Guarantees the filter runs exactly once per request, avoiding
 *   duplicate authentication checks in filter chains.
 *
 * WHAT HAPPENS AFTER THIS FILTER?
 *   Spring Security checks the SecurityContext when evaluating
 *   @PreAuthorize, hasRole(), etc. on controller methods.
 *   If the context has an authenticated user, the request proceeds;
 *   otherwise it's rejected with 401 or 403.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractTokenFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String email = tokenProvider.getEmailFromToken(jwt);

                // Load full user details (including roles) from DB
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Create authentication token — null credentials since we're using JWT (stateless)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                // Attach request details (IP, session) to the authentication object
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Register the authentication in the current request's security context
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
            // Don't throw — let the request proceed as unauthenticated
            // The authorization check on the endpoint will reject it if needed
        }

        // Always continue the filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT from the Authorization header.
     * Expected format: "Authorization: Bearer eyJhbGciOi..."
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Remove "Bearer " prefix
        }
        return null;
    }
}
