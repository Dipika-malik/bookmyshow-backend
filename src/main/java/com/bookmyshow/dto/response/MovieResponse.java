package com.bookmyshow.dto.response;

import com.bookmyshow.enums.Genre;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Safe movie data returned to clients.
 * Does NOT include the internal Show list (would cause N+1 and expose too much).
 *
 * No-args constructor is needed so Jackson can rebuild this object
 * when it is read back from the Redis cache.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieResponse {
    private Long id;
    private String title;
    private String description;
    private Integer durationMinutes;
    private String durationFormatted; // e.g., "2h 30m"
    private Genre genre;
    private String language;
    private LocalDate releaseDate;
    private Double rating;
    private String posterUrl;
}
