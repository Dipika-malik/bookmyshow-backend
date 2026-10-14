package com.bookmyshow.messaging;

import com.bookmyshow.event.BookingEventPayload;
import com.bookmyshow.model.ProcessedEvent;
import com.bookmyshow.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer group "bms-notifications": sends the booking confirmation / cancellation notification.
 *
 * The actual email/SMS provider is out of scope, so the notification is logged —
 * the point is that it runs OFF the booking request path: a slow or failing email
 * provider can no longer slow down or fail a booking.
 *
 * IDEMPOTENT: the (consumer, eventId) row is saved in the same DB transaction as the work,
 * so a redelivered event is recognised and skipped — the user never gets two emails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    static final String CONSUMER = "notifications";

    private final BookingEventReader reader;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "${app.kafka.topics.booking-events}", groupId = "bms-notifications")
    @Transactional
    public void onBookingEvent(String message) {
        BookingEventPayload event = reader.read(message);

        if (processedEventRepository.existsByConsumerAndEventId(CONSUMER, event.eventId())) {
            log.info("[notifications] Skipping duplicate event {}", event.eventId());
            return;
        }
        // Unique constraint backs up the check above if two deliveries race
        processedEventRepository.save(ProcessedEvent.builder()
                .consumer(CONSUMER)
                .eventId(event.eventId())
                .build());

        switch (event.eventType()) {
            case BOOKING_CONFIRMED -> log.info(
                    "[notifications] Email to {}: booking {} confirmed — {} at {} on {}, seats {}, total ₹{}",
                    event.userEmail(), event.bookingReference(), event.movieTitle(), event.theatreName(),
                    event.showTime(), event.seatNumbers(), event.totalAmount());
            case BOOKING_CANCELLED -> log.info(
                    "[notifications] Email to {}: booking {} cancelled — refund of ₹{} initiated",
                    event.userEmail(), event.bookingReference(), event.totalAmount());
        }
    }
}
