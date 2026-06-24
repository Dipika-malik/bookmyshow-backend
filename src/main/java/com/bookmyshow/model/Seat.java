package com.bookmyshow.model;

import com.bookmyshow.enums.SeatType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a physical seat inside a Screen.
 *
 * Seat numbering convention:
 *   rowName   : "A", "B", "C" ... (alphabetical rows from screen)
 *   seatNumber: "A1", "A2", "B1" ... (row + column number)
 *
 * seatType determines price tier:
 *   REGULAR  → base price × 1.0
 *   PREMIUM  → base price × 1.5
 *   VIP      → base price × 2.0
 *
 * A Seat is a physical entity — it doesn't change per show.
 * Per-show availability is tracked in ShowSeat (the join table).
 */
@Entity
@Table(name = "seats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "seat_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Composite identifier like "A1", "B5", "C12"
    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    // Row label: "A", "B", "C", etc.
    @Column(nullable = false)
    private String rowName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    // Many seats belong to one screen
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;
}
