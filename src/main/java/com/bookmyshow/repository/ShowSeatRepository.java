package com.bookmyshow.repository;

import com.bookmyshow.enums.ShowSeatStatus;
import com.bookmyshow.model.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Critical repository — handles seat availability.
 *
 * @Lock(LockModeType.PESSIMISTIC_WRITE) on seat fetch queries:
 *   - Acquires a DB-level row lock (SELECT ... FOR UPDATE)
 *   - Two concurrent users trying to book seat A1 will be serialized
 *   - The second user waits until the first transaction commits/rolls back
 *   - This prevents the classic "double booking" race condition
 */
@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    // Get all seats for a show (used to display seat map to user)
    List<ShowSeat> findByShowId(Long showId);

    // Get specific seats by IDs for a show — PESSIMISTIC_WRITE prevents concurrent booking
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.show.id = :showId AND ss.id IN :seatIds")
    List<ShowSeat> findByShowIdAndIdInWithLock(
            @Param("showId") Long showId,
            @Param("seatIds") List<Long> seatIds);

    // Count available seats for a show (used for availability display)
    long countByShowIdAndStatus(Long showId, ShowSeatStatus status);

    // Find a specific show-seat combination
    Optional<ShowSeat> findByShowIdAndSeatId(Long showId, Long seatId);

    // Get all booked seats for a booking
    List<ShowSeat> findByBookingId(Long bookingId);
}
