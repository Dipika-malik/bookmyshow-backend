package com.bookmyshow.messaging;

import com.bookmyshow.model.OutboxEvent;
import com.bookmyshow.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox rows to Kafka (the second half of the transactional outbox pattern).
 *
 * Every interval it locks the next batch of unpublished events, sends each one, waits for
 * the broker's ack, and marks it published — all in one DB transaction.
 *
 * GUARANTEES:
 *   - At-least-once: if the app dies after the send but before the commit, the row is
 *     sent again on the next run. Consumers dedupe by eventId.
 *   - Per-booking order: the message key is the booking id, so all events for one booking
 *     go to the same partition. On the first failure the batch stops, so a later event
 *     is never published ahead of an earlier one that failed.
 *   - If Kafka is down, bookings still succeed; events just wait in the outbox until it is back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.booking-events}")
    private String topic;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextUnpublished(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(topic, event.getAggregateId(), event.getPayload());
                record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

                // Block for the broker ack: only mark published once Kafka has durably stored it
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

                event.setPublishedAt(LocalDateTime.now());
                published++;
            } catch (Exception e) {
                event.setPublishAttempts(event.getPublishAttempts() + 1);
                log.warn("Outbox publish failed for event {} (attempt {}), will retry: {}",
                        event.getEventId(), event.getPublishAttempts(), e.getMessage());
                break;
            }
        }

        if (published > 0) {
            log.info("Outbox relay published {} event(s) to '{}'", published, topic);
        }
    }
}
