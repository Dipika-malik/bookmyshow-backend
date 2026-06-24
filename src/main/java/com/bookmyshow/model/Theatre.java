package com.bookmyshow.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a physical movie theatre (multiplex).
 *
 * Theatre → Screen (one-to-many)
 *   A theatre has multiple screens (Screen 1, Screen 2, IMAX, etc.)
 *
 * The city field is important for filtering — users search movies
 * available in their city, which maps to: city → theatres → screens → shows → movies.
 */
@Entity
@Table(name = "theatres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Theatre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    // City is indexed separately for efficient city-based lookups
    @Column(nullable = false)
    private String city;

    private String pincode;

    // CascadeType.ALL: creating/deleting a theatre also creates/deletes its screens
    @OneToMany(mappedBy = "theatre", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Screen> screens = new ArrayList<>();
}
