package com.bookmyshow.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Remembers which events a consumer has already handled (the "idempotent consumer" pattern).
 *
 * Kafka delivers at-least-once: after a crash or rebalance the same message can arrive again.
 * Before acting, a consumer records (consumer, eventId); the unique constraint means a
 * redelivered event is detected and skipped instead of, say, sending a second email.
 */
@Entity
@Table(name = "processed_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_consumer_event",
                columnNames = {"consumer", "eventId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String consumer;

    @Column(nullable = false, length = 36)
    private String eventId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime processedAt;
}
