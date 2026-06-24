package com.bookmyshow.config;

import com.bookmyshow.security.JwtAuthenticationFilter;
import com.bookmyshow.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * KEY CONCEPTS:
 *
 * 1. STATELESS SESSION (JWT-based):
 *    We disable HTTP sessions entirely. Each request must carry its own
 *    JWT token. The server holds no session state — fully stateless, REST-friendly.
 *
 * 2. CSRF DISABLED:
 *    CSRF attacks target cookie-based sessions. Since we use JWT in headers
 *    (not cookies), CSRF is not a threat here. Disabling it removes overhead.
 *
 * 3. AUTHORIZATION RULES:
 *    - Public: /api/auth/** (login, register)
 *    - Public GET: /api/movies/**, /api/theatres/**, /api/shows/**
 *    - Authenticated: booking endpoints
 *    - Admin only: POST/PUT/DELETE on movies, theatres, shows
 *
 * 4. @EnableMethodSecurity:
 *    Allows @PreAuthorize("hasRole('ADMIN')") on service/controller methods
 *    as an additional fine-grained authorization layer.
 *
 * 5. BCryptPasswordEncoder:
 *    BCrypt is adaptive (work factor increases with hardware improvements).
 *    Never store or compare passwords in plaintext. Spring Security's
 *    DaoAuthenticationProvider handles BCrypt comparison automatically.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — safe for stateless JWT APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Allow H2 console to work (uses iframes — needs frameOptions disabled)
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/auth/**").permitAll()

                // H2 console (development only — remove in production)
                .requestMatchers("/h2-console/**").permitAll()

                // Public read-only endpoints
                .requestMatchers(HttpMethod.GET, "/api/movies/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/theatres/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/shows/**").permitAll()

                // Write operations require authentication (ADMIN role can be enforced in production)
                .requestMatchers(HttpMethod.POST, "/api/movies/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/movies/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/movies/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/theatres/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/theatres/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/theatres/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/shows/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/shows/**").authenticated()

                // Booking requires authentication (any role)
                .requestMatchers("/api/bookings/**").authenticated()

                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // Stateless — no HTTP session, JWT carries auth state
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Register our authentication provider (BCrypt + UserDetailsService)
            .authenticationProvider(authenticationProvider())

            // Add JWT filter BEFORE Spring's default username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures how Spring Security authenticates users:
     * - UserDetailsService: loads user from DB by email
     * - PasswordEncoder: BCrypt for password comparison
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager is used in AuthService to trigger authentication
     * (validates email + password during login).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with strength 12 (2^12 = 4096 hash rounds).
     * Strength 10-12 is the industry standard balance of security vs. performance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
