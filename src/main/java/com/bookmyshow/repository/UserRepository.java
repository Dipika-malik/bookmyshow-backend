package com.bookmyshow.repository;

import com.bookmyshow.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for User.
 *
 * JpaRepository<User, Long> provides out-of-the-box:
 *   save(), findById(), findAll(), delete(), count(), existsById(), etc.
 *
 * Custom queries are derived from method names (Spring Data Query DSL):
 *   findByEmail → SELECT * FROM users WHERE email = ?
 *   existsByEmail → SELECT COUNT(*) > 0 FROM users WHERE email = ?
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Used during login and JWT token validation
    Optional<User> findByEmail(String email);

    // Used during registration to prevent duplicate emails
    boolean existsByEmail(String email);
}
