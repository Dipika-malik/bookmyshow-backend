package com.bookmyshow.dto.request;

import com.bookmyshow.enums.SeatType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Request body for creating a Screen inside a Theatre.
 *
 * seatLayout: defines the seat rows to create.
 * Example JSON:
 * {
 *   "name": "Screen 1",
 *   "screenType": "STANDARD",
 *   "seatLayout": [
 *     { "rowName": "A", "seatsInRow": 10, "seatType": "REGULAR" },
 *     { "rowName": "B", "seatsInRow": 10, "seatType": "PREMIUM" },
 *     { "rowName": "C", "seatsInRow": 5,  "seatType": "VIP" }
 *   ]
 * }
 * This will create 25 seats: A1-A10, B1-B10, C1-C5
 */
@Data
public class ScreenRequest {

    @NotBlank(message = "Screen name is required")
    private String name;

    private String screenType;

    @NotNull(message = "Seat layout is required")
    @Size(min = 1, message = "At least one seat row is required")
    private List<SeatRowRequest> seatLayout;

    @Data
    public static class SeatRowRequest {

        @NotBlank(message = "Row name is required")
        private String rowName;

        @Min(value = 1, message = "Seats per row must be at least 1")
        @Max(value = 50, message = "Seats per row cannot exceed 50")
        private int seatsInRow;

        @NotNull(message = "Seat type is required")
        private SeatType seatType;
    }
}
