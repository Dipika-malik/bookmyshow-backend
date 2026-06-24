package com.bookmyshow.service;

import com.bookmyshow.dto.request.ShowRequest;
import com.bookmyshow.dto.response.SeatResponse;
import com.bookmyshow.dto.response.ShowResponse;
import com.bookmyshow.enums.ShowSeatStatus;
import com.bookmyshow.enums.ShowStatus;
import com.bookmyshow.exception.ResourceNotFoundException;
import com.bookmyshow.model.*;
import com.bookmyshow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Manages movie show scheduling and seat availability display.
 *
 * SHOW CREATION (critical business logic):
 *   1. Validate movie and screen exist
 *   2. Check for scheduling conflicts (same screen, overlapping time)
 *   3. Create the Show
 *   4. Auto-generate ShowSeat records for EVERY seat in the screen
 *   5. Calculate each ShowSeat's price based on seatType multiplier
 *
 * SEAT PRICING MODEL:
 *   REGULAR : basePrice × 1.0
 *   PREMIUM : basePrice × 1.5
 *   VIP     : basePrice × 2.0
 */
@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ShowSeatRepository showSeatRepository;

    private static final Map<com.bookmyshow.enums.SeatType, Double> SEAT_MULTIPLIERS = Map.of(
            com.bookmyshow.enums.SeatType.REGULAR, 1.0,
            com.bookmyshow.enums.SeatType.PREMIUM, 1.5,
            com.bookmyshow.enums.SeatType.VIP, 2.0
    );

    @Transactional(readOnly = true)
    public ShowResponse getShowById(Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", id));
        return toResponse(show);
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> getShowsByMovie(Long movieId) {
        return showRepository.findByMovieId(movieId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> getUpcomingShowsForMovieInCity(Long movieId, String city) {
        return showRepository.findUpcomingShowsForMovieInCity(movieId, city, LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the full seat map for a show — used by the frontend to
     * render an interactive seat picker (colored by availability).
     */
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsForShow(Long showId) {
        if (!showRepository.existsById(showId)) {
            throw new ResourceNotFoundException("Show", "id", showId);
        }

        return showSeatRepository.findByShowId(showId).stream()
                .map(ss -> SeatResponse.builder()
                        .showSeatId(ss.getId())
                        .seatId(ss.getSeat().getId())
                        .seatNumber(ss.getSeat().getSeatNumber())
                        .rowName(ss.getSeat().getRowName())
                        .seatType(ss.getSeat().getSeatType())
                        .status(ss.getStatus())
                        .price(ss.getPrice())
                        .build())
                .toList();
    }

    @Transactional
    public ShowResponse createShow(ShowRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", request.getMovieId()));

        Screen screen = screenRepository.findById(request.getScreenId())
                .orElseThrow(() -> new ResourceNotFoundException("Screen", "id", request.getScreenId()));

        // Check for scheduling conflict: same screen, overlapping time window
        // Conflict window = showTime to showTime + movie duration
        LocalDateTime showEnd = request.getShowTime().plusMinutes(movie.getDurationMinutes() + 30);
        boolean hasConflict = showRepository.hasConflictingShow(
                screen.getId(), request.getShowTime(), showEnd);

        if (hasConflict) {
            throw new IllegalArgumentException(
                    "Screen '" + screen.getName() + "' already has a show scheduled at this time");
        }

        Show show = Show.builder()
                .movie(movie)
                .screen(screen)
                .showTime(request.getShowTime())
                .basePrice(request.getBasePrice())
                .status(ShowStatus.SCHEDULED)
                .build();

        showRepository.save(show);

        // Auto-generate ShowSeat records for every seat in this screen
        List<Seat> seats = seatRepository.findByScreenId(screen.getId());
        List<ShowSeat> showSeats = seats.stream()
                .map(seat -> {
                    Double multiplier = SEAT_MULTIPLIERS.getOrDefault(seat.getSeatType(), 1.0);
                    Double seatPrice = Math.round(request.getBasePrice() * multiplier * 100.0) / 100.0;

                    return ShowSeat.builder()
                            .show(show)
                            .seat(seat)
                            .status(ShowSeatStatus.AVAILABLE)
                            .price(seatPrice)
                            .build();
                })
                .toList();

        showSeatRepository.saveAll(showSeats);

        return toResponse(show);
    }

    @Transactional
    public ShowResponse cancelShow(Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", id));

        show.setStatus(ShowStatus.CANCELLED);
        return toResponse(showRepository.save(show));
        // In production: trigger refunds for all CONFIRMED bookings for this show
    }

    public ShowResponse toResponse(Show show) {
        long totalSeats = showSeatRepository.countByShowIdAndStatus(show.getId(), ShowSeatStatus.AVAILABLE)
                + showSeatRepository.countByShowIdAndStatus(show.getId(), ShowSeatStatus.BOOKED)
                + showSeatRepository.countByShowIdAndStatus(show.getId(), ShowSeatStatus.LOCKED);

        long availableSeats = showSeatRepository.countByShowIdAndStatus(show.getId(), ShowSeatStatus.AVAILABLE);

        Screen screen = show.getScreen();
        Theatre theatre = screen.getTheatre();

        return ShowResponse.builder()
                .id(show.getId())
                .movieId(show.getMovie().getId())
                .movieTitle(show.getMovie().getTitle())
                .theatreId(theatre.getId())
                .theatreName(theatre.getName())
                .theatreCity(theatre.getCity())
                .screenId(screen.getId())
                .screenName(screen.getName())
                .showTime(show.getShowTime())
                .basePrice(show.getBasePrice())
                .status(show.getStatus())
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .build();
    }
}
