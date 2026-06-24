package com.bookmyshow.security;

import com.bookmyshow.model.User;
import com.bookmyshow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges Spring Security's authentication system with our User database.
 *
 * Spring Security calls loadUserByUsername() during authentication.
 * We look up the user by email (our "username") and return a UserDetails
 * object that Spring Security uses to:
 *   - Compare the provided password (BCrypt comparison)
 *   - Get granted authorities (roles) for authorization
 *
 * The returned UserDetails is Spring's built-in org.springframework.security.core.userdetails.User
 * which takes: (username, encodedPassword, authorities).
 *
 * Authorities format: "ROLE_USER" or "ROLE_ADMIN"
 * Spring Security's @PreAuthorize("hasRole('ADMIN')") checks for "ROLE_ADMIN" prefix automatically.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        // Convert our Role enum to Spring's GrantedAuthority
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(authority)
        );
    }
}
