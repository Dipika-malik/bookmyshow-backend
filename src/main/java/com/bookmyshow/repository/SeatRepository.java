package com.bookmyshow.repository;

import com.bookmyshow.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    // Get the seat layout for a screen (used when creating ShowSeats)
    List<Seat> findByScreenId(Long screenId);

    // Count seats in a screen (used for capacity validation)
    long countByScreenId(Long screenId);
}
