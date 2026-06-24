package com.bookmyshow.model;

import com.bookmyshow.enums.ShowSeatStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Join table between Show and Seat — tracks per-show seat availability.
 *
 * WHY this exists (instead of a boolean on Seat):
 *   A seat in Screen 1 can be booked for the 10 AM show but still available
 *   for the 3 PM show. We need per-show granularity.
 *
 * State transitions:
 *   AVAILABLE ──book──▶ LOCKED ──payment success──▶ BOOKED
 *                           └──payment timeout──▶ AVAILABLE
 *
 * Concurrency protection:
 *   @Version on Show + PESSIMISTIC_WRITE on ShowSeat queries prevents
 *   two users booking the same seat at the same time.
 *
 * price is pre-calculated when the show is created:
 *   price = show.basePrice × seat.seatType multiplier
 */
@Entity
@Table(name = "show_seats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShowSeatStatus status = ShowSeatStatus.AVAILABLE;

    // Pre-computed price = basePrice × seatType multiplier
    @Column(nullable = false)
    private Double price;

    // Set when status = LOCKED; null otherwise
    private LocalDateTime lockedAt;

    // Set when status = BOOKED; links back to the booking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;
}
