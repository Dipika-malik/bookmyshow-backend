package com.bookmyshow.model;

import com.bookmyshow.enums.PaymentMethod;
import com.bookmyshow.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Records the payment associated with a booking.
 *
 * In a real system, transactionId would be the reference ID from the
 * payment gateway (Razorpay, Stripe, PayU, etc.).
 *
 * For this demo, we simulate payment processing in PaymentService.
 * In production, you'd integrate with a gateway SDK and use webhooks
 * to receive async payment confirmations.
 *
 * One Booking → One Payment (1:1 relationship).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side of the 1:1 relationship with Booking
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    // Reference ID from payment gateway (simulated as UUID in demo)
    private String transactionId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Timestamp when gateway confirmed payment
    private LocalDateTime paidAt;
}
