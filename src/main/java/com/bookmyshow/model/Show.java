package com.bookmyshow.model;

import com.bookmyshow.enums.ShowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a specific screening of a Movie at a Screen on a given date/time.
 *
 * Think of it as: "Interstellar @ PVR Cinemas Screen 2 on 2024-04-20 at 14:30"
 *
 * Pricing model:
 *   - basePrice: cost for a REGULAR seat
 *   - ShowSeat.price is computed as: basePrice × seatType multiplier
 *     (REGULAR=1.0, PREMIUM=1.5, VIP=2.0)
 *
 * When a Show is created, the system should auto-create ShowSeat records
 * for every Seat in the Screen, all set to AVAILABLE. (Done in ShowService)
 *
 * @Version enables optimistic locking — prevents lost-update anomalies
 * when two users try to book the same show simultaneously.
 */
@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The movie being screened
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    // The screen where this show is being held
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    // Date + time combined (e.g., 2024-04-20T14:30:00)
    @Column(nullable = false)
    private LocalDateTime showTime;

    // Base ticket price for REGULAR seats
    @Column(nullable = false)
    private Double basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShowStatus status = ShowStatus.SCHEDULED;

    // Optimistic locking version — JPA increments this on each update
    @Version
    private Integer version;

    // All seat availability records for this show
    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShowSeat> showSeats = new ArrayList<>();
}
