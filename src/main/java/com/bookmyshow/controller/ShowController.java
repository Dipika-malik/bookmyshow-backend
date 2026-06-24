package com.bookmyshow.controller;

import com.bookmyshow.dto.request.ShowRequest;
import com.bookmyshow.dto.response.SeatResponse;
import com.bookmyshow.dto.response.ShowResponse;
import com.bookmyshow.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @GetMapping("/{id}")
    public ResponseEntity<ShowResponse> getShowById(@PathVariable Long id) {
        return ResponseEntity.ok(showService.getShowById(id));
    }

    @GetMapping
    public ResponseEntity<List<ShowResponse>> getShows(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) String city) {
        if (movieId != null && city != null) {
            return ResponseEntity.ok(showService.getUpcomingShowsForMovieInCity(movieId, city));
        }
        if (movieId != null) {
            return ResponseEntity.ok(showService.getShowsByMovie(movieId));
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatResponse>> getSeatsForShow(@PathVariable Long id) {
        return ResponseEntity.ok(showService.getSeatsForShow(id));
    }

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody ShowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showService.createShow(request));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ShowResponse> cancelShow(@PathVariable Long id) {
        return ResponseEntity.ok(showService.cancelShow(id));
    }
}
