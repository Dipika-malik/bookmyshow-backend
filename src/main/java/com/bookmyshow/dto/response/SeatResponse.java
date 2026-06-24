package com.bookmyshow.dto.response;

import com.bookmyshow.enums.SeatType;
import com.bookmyshow.enums.ShowSeatStatus;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a single seat in the seat-map response.
 *
 * showSeatId: the ID to pass in BookingRequest.showSeatIds
 * seatNumber: display label (e.g., "A5")
 * status: AVAILABLE / LOCKED / BOOKED (frontend uses to render seat map colors)
 * price: pre-computed price for this seat type in this show
 */
@Data
@Builder
public class SeatResponse {
    private Long showSeatId;
    private Long seatId;
    private String seatNumber;
    private String rowName;
    private SeatType seatType;
    private ShowSeatStatus status;
    private Double price;
}
