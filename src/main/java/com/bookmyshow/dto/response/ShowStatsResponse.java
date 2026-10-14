package com.bookmyshow.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Live sales numbers for a show, maintained from Kafka booking events.
 * Eventually consistent: may lag a booking by the relay interval (~1s).
 */
@Data
@Builder
public class ShowStatsResponse {
    private Long showId;
    private long ticketsSold;
    private long activeBookings;
    private double revenue;
}
