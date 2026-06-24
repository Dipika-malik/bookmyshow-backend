package com.bookmyshow.repository;

import com.bookmyshow.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Get all bookings for a user (My Bookings page)
    List<Booking> findByUserIdOrderByBookedAtDesc(Long userId);

    // Look up by human-readable reference (e.g., ticket QR code scan)
    Optional<Booking> findByBookingReference(String bookingReference);

    // Check if a reference already exists (avoid duplicates)
    boolean existsByBookingReference(String bookingReference);
}
