package com.bookmyshow.messaging;

import com.bookmyshow.event.BookingEventPayload;
import com.bookmyshow.service.ShowStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "bms-show-stats": keeps live per-show sales counters in Redis.
 *
 * It reads the same topic as NotificationConsumer but in a DIFFERENT consumer group,
 * so each group gets its own copy of every event (fan-out). Adding a new consumer
 * later — e.g. analytics or loyalty points — needs no change to BookingService.
 *
 * Idempotency is handled inside ShowStatsService with an atomic Redis Lua script.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ShowStatsConsumer {

    private final BookingEventReader reader;
    private final ShowStatsService showStatsService;

    @KafkaListener(topics = "${app.kafka.topics.booking-events}", groupId = "bms-show-stats")
    public void onBookingEvent(String message) {
        BookingEventPayload event = reader.read(message);
        boolean applied = showStatsService.apply(event);
        log.info("[show-stats] {} {} for show {}", applied ? "Applied" : "Skipped duplicate",
                event.eventType(), event.showId());
    }
}
