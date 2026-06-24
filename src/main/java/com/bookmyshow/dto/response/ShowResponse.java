package com.bookmyshow.dto.response;

import com.bookmyshow.enums.ShowStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Show listing response.
 * Includes enough info for the "Select a Show" screen:
 * movie name, theatre, screen, time, price, available seats.
 */
@Data
@Builder
public class ShowResponse {
    private Long id;
    private Long movieId;
    private String movieTitle;
    private Long theatreId;
    private String theatreName;
    private String theatreCity;
    private Long screenId;
    private String screenName;
    private LocalDateTime showTime;
    private Double basePrice;
    private ShowStatus status;
    private long totalSeats;
    private long availableSeats;
}
