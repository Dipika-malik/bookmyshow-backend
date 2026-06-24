package com.bookmyshow.model;

import com.bookmyshow.enums.Genre;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a movie in the catalog.
 *
 * A Movie can be screened at many Shows (one-to-many).
 * Movie data is managed by ADMIN users.
 *
 * Entity design:
 *  - durationMinutes: stored as integer, frontend can format as "2h 30m"
 *  - rating: 0.0 to 10.0 scale (like IMDb)
 *  - posterUrl: link to image storage (S3 / CDN in production)
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Duration in minutes (e.g., 150 = 2h 30m)
    @Column(nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    private Genre genre;

    // Language like "English", "Hindi", "Tamil"
    private String language;

    private LocalDate releaseDate;

    // Rating out of 10.0
    private Double rating;

    // URL to poster image
    private String posterUrl;

    // A movie can be shown at many shows across different theatres
    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Show> shows = new ArrayList<>();
}
