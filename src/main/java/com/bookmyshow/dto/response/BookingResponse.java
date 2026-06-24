package com.bookmyshow.dto.response;

import com.bookmyshow.enums.BookingStatus;
import com.bookmyshow.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Returned after booking creation or retrieval.
 * Contains all info needed for a booking confirmation/ticket.
 */
@Data
@Builder
public class BookingResponse {
    private Long bookingId;
    private String bookingReference;   // e.g., "BMS-A3F8X2"
    private BookingStatus status;

    // Movie + show details
    private String movieTitle;
    private String theatreName;
    private String screenName;
    private LocalDateTime showTime;

    // User details
    private String userEmail;
    private String userName;

    // Seat details
    private List<String> seatNumbers;  // e.g., ["A3", "A4"]
    private int numberOfSeats;
    private Double totalAmount;

    // Payment details
    private PaymentStatus paymentStatus;
    private String transactionId;
    private String paymentMethod;

    private LocalDateTime bookedAt;
}
