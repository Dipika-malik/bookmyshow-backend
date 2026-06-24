package com.bookmyshow.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request body for scheduling a Show (ADMIN only).
 *
 * The service will validate:
 *  - Movie exists
 *  - Screen exists and belongs to a theatre
 *  - No conflicting show in the same screen at overlapping times
 */
@Data
public class ShowRequest {

    @NotNull(message = "Movie ID is required")
    private Long movieId;

    @NotNull(message = "Screen ID is required")
    private Long screenId;

    @NotNull(message = "Show time is required")
    @Future(message = "Show time must be in the future")
    private LocalDateTime showTime;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private Double basePrice;
}
