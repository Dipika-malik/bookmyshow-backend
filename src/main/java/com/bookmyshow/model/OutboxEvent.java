package com.bookmyshow.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * TRANSACTIONAL OUTBOX — an event waiting to be published to Kafka.
 *
 * WHY NOT SEND TO KAFKA DIRECTLY FROM BookingService?
 *   The booking is committed to MySQL and the message is sent to Kafka — two different systems,
 *   with no shared transaction. Whichever happens second can fail:
 *     - commit, then crash before send  → booking exists, event is lost
 *     - send, then commit fails         → event for a booking that doesn't exist
 *   Writing the event as a row in the SAME DB transaction as the booking makes both atomic.
 *   OutboxRelay then publishes unpublished rows to Kafka and marks them published.
 *
 * Delivery is at-least-once (the relay can crash after sending but before marking),
 * so every consumer must be idempotent — each event carries a unique eventId for that.
 */
@Entity
@Table(name = "outbox_events",
        indexes = @Index(name = "idx_outbox_unpublished", columnList = "publishedAt, id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique per event; consumers use it to detect redeliveries
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    // e.g. "Booking" — what kind of entity the event is about
    @Column(nullable = false)
    private String aggregateType;

    // e.g. the booking id — used as the Kafka message key, so all events
    // for one booking land on the same partition and stay in order
    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    // JSON body sent to Kafka as-is
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // NULL until the relay has published the event
    private LocalDateTime publishedAt;

    @Builder.Default
    private Integer publishAttempts = 0;
}
