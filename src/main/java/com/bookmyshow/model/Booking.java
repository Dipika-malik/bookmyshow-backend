package com.bookmyshow.model;

import com.bookmyshow.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a ticket booking made by a user for a specific show.
 *
 * A Booking ties together:
 *   - User       : who is booking
 *   - Show       : which show
 *   - ShowSeats  : which specific seats (1 booking can reserve multiple seats)
 *   - Payment    : payment for this booking
 *
 * bookingReference: Human-readable unique ID (e.g., "BMS-A3F8X2")
 *   Useful for customer support and ticket QR codes.
 *
 * Status flow:
 *   PENDING ──payment success──▶ CONFIRMED
 *   PENDING ──payment fail──────▶ CANCELLED
 *   CONFIRMED ──user cancel──────▶ CANCELLED (if within cancellation window)
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique, human-readable reference like "BMS-A3F8X2"
    @Column(unique = true, nullable = false)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime bookedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    // Sum of all booked ShowSeat prices
    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private Integer numberOfSeats;

    // All seats included in this booking
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ShowSeat> showSeats = new ArrayList<>();

    // One-to-one with Payment; cascade ensures payment is created/deleted with booking
    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Payment payment;
}
