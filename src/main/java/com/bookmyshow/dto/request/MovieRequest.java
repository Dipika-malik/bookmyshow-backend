package com.bookmyshow.dto.request;

import com.bookmyshow.enums.Genre;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/** Request body for creating or updating a Movie (ADMIN only). */
@Data
public class MovieRequest {

    @NotBlank(message = "Movie title is required")
    private String title;

    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be positive")
    @Max(value = 600, message = "Duration cannot exceed 600 minutes")
    private Integer durationMinutes;

    private Genre genre;

    @NotBlank(message = "Language is required")
    private String language;

    private LocalDate releaseDate;

    @DecimalMin(value = "0.0", message = "Rating cannot be negative")
    @DecimalMax(value = "10.0", message = "Rating cannot exceed 10.0")
    private Double rating;

    private String posterUrl;
}
