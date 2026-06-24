package com.bookmyshow.model;

import com.bookmyshow.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered user (customer or admin).
 *
 * @Table(name = "users") - We avoid the default "user" table name
 *   because "USER" is a reserved keyword in H2 and many SQL databases.
 *
 * Spring Security integration:
 *   - The password field stores a BCrypt-hashed password (never plain text).
 *   - The role field is used for authorization (ROLE_USER, ROLE_ADMIN).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    // unique = true enforces no two users share the same email at the DB level
    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    // BCrypt hash stored here — NEVER plaintext password
    @Column(nullable = false)
    private String password;

    @Column(length = 15)
    private String phone;

    // Stored as string (e.g., "USER", "ADMIN") in the DB column
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // One user can have many bookings; cascade REMOVE would be too destructive
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
