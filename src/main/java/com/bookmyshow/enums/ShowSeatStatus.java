package com.bookmyshow.enums;

/**
 * Lifecycle of a single seat for a specific show.
 *
 * AVAILABLE → The seat is free to book.
 * LOCKED    → Temporarily held (user is in checkout flow, ~10 min window).
 *             Released back to AVAILABLE if payment doesn't complete in time.
 * BOOKED    → Payment confirmed; seat is permanently taken.
 *
 * This state machine prevents double-booking in concurrent scenarios.
 */
public enum ShowSeatStatus {
    AVAILABLE,
    LOCKED,
    BOOKED
}
