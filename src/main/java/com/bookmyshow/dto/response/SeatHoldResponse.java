package com.bookmyshow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Returned after seats are held — tells the client how long it has to complete the booking.
 */
@Data
@Builder
public class SeatHoldResponse {
    private Long showId;
    private List<Long> showSeatIds;
    private long holdSeconds;
    private LocalDateTime expiresAt;
}
