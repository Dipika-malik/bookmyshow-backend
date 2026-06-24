package com.bookmyshow.enums;

/**
 * Defines seat tiers in a screen.
 * Each type has different pricing (multiplier applied on base show price).
 *
 * REGULAR  - Standard seats (1.0x price)
 * PREMIUM  - Better viewing angle/comfort (1.5x price)
 * VIP      - Recliner/lounge seats (2.0x price)
 */
public enum SeatType {
    REGULAR,
    PREMIUM,
    VIP
}
