package com.bookmyshow.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body for holding seats while the user completes checkout.
 * Seats are identified by ShowSeat IDs, same as BookingRequest.
 */
@Data
public class SeatHoldRequest {

    @NotNull(message = "Show ID is required")
    private Long showId;

    @NotEmpty(message = "At least one seat must be selected")
    @Size(max = 10, message = "You can hold at most 10 seats at a time")
    private List<Long> showSeatIds;
}
