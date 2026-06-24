package com.bookmyshow.service;

import com.bookmyshow.dto.request.BookingRequest;
import com.bookmyshow.dto.response.BookingResponse;
import com.bookmyshow.enums.BookingStatus;
import com.bookmyshow.enums.PaymentStatus;
import com.bookmyshow.enums.ShowSeatStatus;
import com.bookmyshow.exception.ResourceNotFoundException;
import com.bookmyshow.exception.SeatNotAvailableException;
import com.bookmyshow.model.*;
import com.bookmyshow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Core booking flow:
     * 1. Lock the requested ShowSeats (PESSIMISTIC_WRITE prevents double-booking)
     * 2. Validate all seats are AVAILABLE
     * 3. Create Booking in PENDING state
     * 4. Mark seats as BOOKED and link to booking
     * 5. Simulate payment (SUCCESS in demo)
     * 6. Confirm booking
     */
    @Transactional
    public BookingResponse createBooking(Long userId, BookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Show show = showRepository.findById(request.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", request.getShowId()));

        // Acquire row-level locks to prevent concurrent booking of same seats
        List<ShowSeat> showSeats = showSeatRepository.findByShowIdAndIdInWithLock(
                request.getShowId(), request.getShowSeatIds());

        if (showSeats.size() != request.getShowSeatIds().size()) {
            throw new SeatNotAvailableException("One or more seats not found for this show");
        }

        // Validate every seat is still AVAILABLE
        List<ShowSeat> unavailable = showSeats.stream()
                .filter(ss -> ss.getStatus() != ShowSeatStatus.AVAILABLE)
                .toList();

        if (!unavailable.isEmpty()) {
            List<String> takenSeats = unavailable.stream()
                    .map(ss -> ss.getSeat().getSeatNumber())
                    .toList();
            throw new SeatNotAvailableException("Seats already booked: " + takenSeats);
        }

        double totalAmount = showSeats.stream().mapToDouble(ShowSeat::getPrice).sum();

        String bookingRef = generateBookingReference();

        Booking booking = Booking.builder()
                .bookingReference(bookingRef)
                .user(user)
                .show(show)
                .status(BookingStatus.PENDING)
                .totalAmount(totalAmount)
                .numberOfSeats(showSeats.size())
                .build();

        bookingRepository.save(booking);

        // Mark seats as BOOKED and link to this booking
        showSeats.forEach(ss -> {
            ss.setStatus(ShowSeatStatus.BOOKED);
            ss.setBooking(booking);
        });
        showSeatRepository.saveAll(showSeats);

        // Simulate payment (in production: call payment gateway)
        Payment payment = Payment.builder()
                .booking(booking)
                .amount(totalAmount)
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.SUCCESS)
                .transactionId(UUID.randomUUID().toString())
                .paidAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        return toResponse(booking, payment, showSeats);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByBookedAtDesc(userId).stream()
                .map(booking -> {
                    List<ShowSeat> seats = showSeatRepository.findByBookingId(booking.getId());
                    Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
                    return toResponse(booking, payment, seats);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Booking does not belong to this user");
        }

        List<ShowSeat> seats = showSeatRepository.findByBookingId(bookingId);
        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        return toResponse(booking, payment, seats);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Booking does not belong to this user");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only CONFIRMED bookings can be cancelled");
        }

        // Release seats back to AVAILABLE
        List<ShowSeat> seats = showSeatRepository.findByBookingId(bookingId);
        seats.forEach(ss -> {
            ss.setStatus(ShowSeatStatus.AVAILABLE);
            ss.setBooking(null);
        });
        showSeatRepository.saveAll(seats);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Payment payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        return toResponse(booking, payment, seats);
    }

    private String generateBookingReference() {
        String ref;
        do {
            ref = "BMS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (bookingRepository.existsByBookingReference(ref));
        return ref;
    }

    private BookingResponse toResponse(Booking booking, Payment payment, List<ShowSeat> seats) {
        Show show = booking.getShow();
        Screen screen = show.getScreen();
        Theatre theatre = screen.getTheatre();

        List<String> seatNumbers = seats.stream()
                .map(ss -> ss.getSeat().getSeatNumber())
                .toList();

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .status(booking.getStatus())
                .movieTitle(show.getMovie().getTitle())
                .theatreName(theatre.getName())
                .screenName(screen.getName())
                .showTime(show.getShowTime())
                .userEmail(booking.getUser().getEmail())
                .userName(booking.getUser().getName())
                .seatNumbers(seatNumbers)
                .numberOfSeats(booking.getNumberOfSeats())
                .totalAmount(booking.getTotalAmount())
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .transactionId(payment != null ? payment.getTransactionId() : null)
                .paymentMethod(payment != null ? payment.getPaymentMethod().name() : null)
                .bookedAt(booking.getBookedAt())
                .build();
    }
}
