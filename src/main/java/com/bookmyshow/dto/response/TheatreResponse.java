package com.bookmyshow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TheatreResponse {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String pincode;
    private int numberOfScreens;
    private List<ScreenSummary> screens;

    @Data
    @Builder
    public static class ScreenSummary {
        private Long id;
        private String name;
        private String screenType;
        private Integer totalSeats;
    }
}
