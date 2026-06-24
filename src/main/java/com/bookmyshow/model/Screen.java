package com.bookmyshow.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single auditorium/screen inside a Theatre.
 *
 * Relationship chain:
 *   Theatre → Screen → Seat     (physical layout)
 *   Screen  → Show              (scheduling layer)
 *
 * The totalSeats field is denormalized for quick capacity checks
 * without counting seats every time.
 *
 * screenType can be "STANDARD", "IMAX", "4DX", "DOLBY" etc.
 */
@Entity
@Table(name = "screens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g., "Screen 1", "IMAX Screen", "4DX Hall"
    @Column(nullable = false)
    private String name;

    // Denormalized capacity — must match actual seat count
    @Column(nullable = false)
    private Integer totalSeats;

    // Screen type for display purposes
    private String screenType;

    // Many screens belong to one theatre
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theatre_id", nullable = false)
    private Theatre theatre;

    // CascadeType.ALL: adding/removing a screen manages its seats
    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    // All shows scheduled in this screen
    @OneToMany(mappedBy = "screen", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Show> shows = new ArrayList<>();
}
