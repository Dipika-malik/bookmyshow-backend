package com.bookmyshow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user tries to book a seat that is already LOCKED or BOOKED.
 * Returns 409 Conflict — the request itself is valid but conflicts with current state.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SeatNotAvailableException extends RuntimeException {

    public SeatNotAvailableException(String seatNumber) {
        super(String.format("Seat '%s' is not available for booking", seatNumber));
    }

    public SeatNotAvailableException(String message, boolean raw) {
        super(message);
    }
}
