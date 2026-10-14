package com.bookmyshow.event;

import com.bookmyshow.enums.BookingEventType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON body of a message on the booking-events Kafka topic.
 *
 * It carries everything consumers need (email, movie, seats, amount) so they never
 * have to call back into this service's database — consumers stay decoupled.
 */
public record BookingEventPayload(
        String eventId,
        BookingEventType eventType,
        LocalDateTime occurredAt,
        Long bookingId,
        String bookingReference,
        Long userId,
        String userEmail,
        String userName,
        Long showId,
        String movieTitle,
        String theatreName,
        LocalDateTime showTime,
        List<String> seatNumbers,
        int numberOfSeats,
        double totalAmount
) {}
