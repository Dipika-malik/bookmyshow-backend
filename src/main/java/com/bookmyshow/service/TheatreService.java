package com.bookmyshow.service;

import com.bookmyshow.dto.request.ScreenRequest;
import com.bookmyshow.dto.request.TheatreRequest;
import com.bookmyshow.dto.response.TheatreResponse;
import com.bookmyshow.enums.SeatType;
import com.bookmyshow.exception.ResourceNotFoundException;
import com.bookmyshow.model.Screen;
import com.bookmyshow.model.Seat;
import com.bookmyshow.model.Theatre;
import com.bookmyshow.repository.ScreenRepository;
import com.bookmyshow.repository.TheatreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages theatres and their screens + seats.
 *
 * SCREEN CREATION FLOW:
 *   1. Admin sends ScreenRequest with a seatLayout definition
 *   2. We create the Screen entity
 *   3. We iterate the layout and create individual Seat entities
 *   4. Example: row "A" with 10 REGULAR seats → creates A1, A2, ..., A10
 *
 * This seat generation is a one-time operation per screen.
 * After this, when a Show is created in this screen, ShowSeats are
 * auto-generated for each Seat (in ShowService).
 */
@Service
@RequiredArgsConstructor
public class TheatreService {

    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;

    @Transactional(readOnly = true)
    public List<TheatreResponse> getAllTheatres() {
        return theatreRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TheatreResponse getTheatreById(Long id) {
        Theatre theatre = theatreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre", "id", id));
        return toResponse(theatre);
    }

    @Transactional(readOnly = true)
    public List<TheatreResponse> getTheatresByCity(String city) {
        return theatreRepository.findByCityIgnoreCase(city).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TheatreResponse createTheatre(TheatreRequest request) {
        Theatre theatre = Theatre.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .pincode(request.getPincode())
                .build();

        return toResponse(theatreRepository.save(theatre));
    }

    @Transactional
    public TheatreResponse updateTheatre(Long id, TheatreRequest request) {
        Theatre theatre = theatreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre", "id", id));

        theatre.setName(request.getName());
        theatre.setAddress(request.getAddress());
        theatre.setCity(request.getCity());
        theatre.setPincode(request.getPincode());

        return toResponse(theatreRepository.save(theatre));
    }

    @Transactional
    public TheatreResponse addScreenToTheatre(Long theatreId, ScreenRequest request) {
        Theatre theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre", "id", theatreId));

        // Calculate total seats from the layout
        int totalSeats = request.getSeatLayout().stream()
                .mapToInt(ScreenRequest.SeatRowRequest::getSeatsInRow)
                .sum();

        Screen screen = Screen.builder()
                .name(request.getName())
                .screenType(request.getScreenType())
                .totalSeats(totalSeats)
                .theatre(theatre)
                .seats(new ArrayList<>())
                .build();

        // Generate all seats based on the layout
        List<Seat> seats = generateSeats(screen, request.getSeatLayout());
        screen.getSeats().addAll(seats);

        theatre.getScreens().add(screen);
        theatreRepository.save(theatre);

        return toResponse(theatre);
    }

    /**
     * Generates Seat entities from a seat layout definition.
     *
     * Example layout:
     *   [{ rowName: "A", seatsInRow: 10, seatType: REGULAR }]
     *   → Creates seats: A1, A2, A3, A4, A5, A6, A7, A8, A9, A10
     */
    private List<Seat> generateSeats(Screen screen, List<ScreenRequest.SeatRowRequest> layout) {
        List<Seat> seats = new ArrayList<>();
        for (ScreenRequest.SeatRowRequest row : layout) {
            for (int col = 1; col <= row.getSeatsInRow(); col++) {
                Seat seat = Seat.builder()
                        .seatNumber(row.getRowName() + col)  // e.g., "A1", "B5"
                        .rowName(row.getRowName())
                        .seatType(row.getSeatType())
                        .screen(screen)
                        .build();
                seats.add(seat);
            }
        }
        return seats;
    }

    public TheatreResponse toResponse(Theatre theatre) {
        List<TheatreResponse.ScreenSummary> screenSummaries = theatre.getScreens().stream()
                .map(s -> TheatreResponse.ScreenSummary.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .screenType(s.getScreenType())
                        .totalSeats(s.getTotalSeats())
                        .build())
                .toList();

        return TheatreResponse.builder()
                .id(theatre.getId())
                .name(theatre.getName())
                .address(theatre.getAddress())
                .city(theatre.getCity())
                .pincode(theatre.getPincode())
                .numberOfScreens(theatre.getScreens().size())
                .screens(screenSummaries)
                .build();
    }
}
