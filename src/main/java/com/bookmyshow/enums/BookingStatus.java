package com.bookmyshow.enums;

/**
 * Lifecycle of a booking.
 *
 * PENDING    - Seats locked, awaiting payment confirmation.
 * CONFIRMED  - Payment successful; tickets issued.
 * CANCELLED  - Booking cancelled by user or due to payment failure.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
