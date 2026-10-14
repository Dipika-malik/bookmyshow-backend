package com.bookmyshow.messaging;

/**
 * A Kafka message that can never be processed (e.g. malformed JSON).
 * Registered as not-retryable, so it goes straight to the dead-letter topic.
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
