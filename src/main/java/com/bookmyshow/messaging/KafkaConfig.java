package com.bookmyshow.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka topics and consumer error handling.
 *
 * TOPIC LAYOUT
 *   booking-events      — 3 partitions, keyed by booking id (per-booking ordering, parallel consumers)
 *   booking-events.DLT  — dead-letter topic; same partition count so a failed record keeps its partition
 *
 * ERROR HANDLING
 *   A failing message is retried 3 times, 1s apart (covers short outages, e.g. DB restart).
 *   If it still fails it is published to the DLT and the consumer moves on, so one bad
 *   message ("poison pill") can never block the whole partition.
 *   Malformed JSON is never going to succeed, so it goes to the DLT without retries.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    private static final int PARTITIONS = 3;

    @Value("${app.kafka.topics.booking-events}")
    private String bookingEventsTopic;

    @Bean
    public NewTopic bookingEventsTopic() {
        return TopicBuilder.name(bookingEventsTopic).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic bookingEventsDeadLetterTopic() {
        return TopicBuilder.name(bookingEventsTopic + ".DLT").partitions(PARTITIONS).replicas(1).build();
    }

    // Picked up by Spring Boot's listener container factory, so it applies to every @KafkaListener
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(InvalidEventException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Delivery attempt {} failed for record at {}-{}@{}: {}", attempt,
                        record.topic(), record.partition(), record.offset(), ex.getMessage()));
        return handler;
    }
}
