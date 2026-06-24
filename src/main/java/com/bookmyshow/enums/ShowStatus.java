package com.bookmyshow.enums;

/**
 * Lifecycle of a scheduled show.
 *
 * SCHEDULED  - Show is upcoming, bookings open.
 * CANCELLED  - Show cancelled; triggers refunds for existing bookings.
 * COMPLETED  - Show has aired.
 */
public enum ShowStatus {
    SCHEDULED,
    CANCELLED,
    COMPLETED
}
