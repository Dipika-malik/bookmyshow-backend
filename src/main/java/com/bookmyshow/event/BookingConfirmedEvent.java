package com.bookmyshow.event;

import java.util.List;

/**
 * Published (in-process, via Spring's ApplicationEventPublisher) when a booking is confirmed.
 *
 * Listeners use @TransactionalEventListener(AFTER_COMMIT), so they only run once the
 * booking is safely in MySQL — never for a booking that later rolls back.
 */
public record BookingConfirmedEvent(
        Long bookingId,
        String bookingReference,
        Long userId,
        Long showId,
        List<Long> showSeatIds,
        double totalAmount
) {}
