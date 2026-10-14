package com.bookmyshow.exception;

/**
 * Thrown when a seat hold cannot be placed because Redis is unreachable.
 * Mapped to 503 — the client can retry, or book directly (the DB lock still protects the seats).
 */
public class SeatHoldUnavailableException extends RuntimeException {
    public SeatHoldUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
