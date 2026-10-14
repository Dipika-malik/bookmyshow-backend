package com.bookmyshow.controller;

import com.bookmyshow.dto.request.BookingRequest;
import com.bookmyshow.dto.request.SeatHoldRequest;
import com.bookmyshow.dto.response.BookingResponse;
import com.bookmyshow.dto.response.SeatHoldResponse;
import com.bookmyshow.model.User;
import com.bookmyshow.repository.UserRepository;
import com.bookmyshow.service.BookingService;
import com.bookmyshow.service.SeatHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final SeatHoldService seatHoldService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BookingRequest request) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(userId, request));
    }

    /**
     * Hold seats for the checkout window (default 10 min) before calling POST /api/bookings.
     * Returns 409 if any seat is booked or held by another user.
     */
    @PostMapping("/holds")
    public ResponseEntity<SeatHoldResponse> holdSeats(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SeatHoldRequest request) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seatHoldService.holdSeats(userId, request));
    }

    /** Release the caller's holds early (e.g. user left the checkout page). */
    @DeleteMapping("/holds")
    public ResponseEntity<Void> releaseHolds(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Long showId,
            @RequestParam List<Long> showSeatIds) {
        Long userId = getUserId(userDetails);
        seatHoldService.releaseHolds(userId, showId, showSeatIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getUserBookings(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getBookingById(id, userId));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.cancelBooking(id, userId));
    }

    private Long getUserId(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        return user.getId();
    }
}
