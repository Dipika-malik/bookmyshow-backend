package com.bookmyshow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for creating or updating a Theatre (ADMIN only). */
@Data
public class TheatreRequest {

    @NotBlank(message = "Theatre name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    private String pincode;
}
