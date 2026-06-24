package com.bookmyshow.dto.request;

import com.bookmyshow.enums.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request body for creating a booking.
 *
 * The user selects:
 *  1. A show (showId)
 *  2. Specific seats by their ShowSeat IDs (showSeatIds)
 *  3. Payment method
 *
 * WHY ShowSeat IDs instead of Seat IDs?
 *   ShowSeat is the per-show seat record. Using its ID directly avoids
 *   an extra lookup and ties the booking to the correct show+seat combo.
 */
@Data
public class BookingRequest {

    @NotNull(message = "Show ID is required")
    private Long showId;

    @NotEmpty(message = "At least one seat must be selected")
    private List<Long> showSeatIds;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
}
