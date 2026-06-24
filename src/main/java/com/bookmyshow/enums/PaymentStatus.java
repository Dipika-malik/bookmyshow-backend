package com.bookmyshow.enums;

/**
 * Result of a payment attempt.
 *
 * PENDING - Payment initiated but not yet confirmed by gateway.
 * SUCCESS - Payment gateway confirmed the transaction.
 * FAILED  - Payment gateway rejected or transaction timed out.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
