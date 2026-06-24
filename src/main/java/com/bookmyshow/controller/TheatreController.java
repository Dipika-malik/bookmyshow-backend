package com.bookmyshow.controller;

import com.bookmyshow.dto.request.ScreenRequest;
import com.bookmyshow.dto.request.TheatreRequest;
import com.bookmyshow.dto.response.TheatreResponse;
import com.bookmyshow.service.TheatreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/theatres")
@RequiredArgsConstructor
public class TheatreController {

    private final TheatreService theatreService;

    @GetMapping
    public ResponseEntity<List<TheatreResponse>> getAllTheatres(
            @RequestParam(required = false) String city) {
        if (city != null) {
            return ResponseEntity.ok(theatreService.getTheatresByCity(city));
        }
        return ResponseEntity.ok(theatreService.getAllTheatres());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TheatreResponse> getTheatreById(@PathVariable Long id) {
        return ResponseEntity.ok(theatreService.getTheatreById(id));
    }

    @PostMapping
    public ResponseEntity<TheatreResponse> createTheatre(@Valid @RequestBody TheatreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.createTheatre(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TheatreResponse> updateTheatre(
            @PathVariable Long id,
            @Valid @RequestBody TheatreRequest request) {
        return ResponseEntity.ok(theatreService.updateTheatre(id, request));
    }

    @PostMapping("/{theatreId}/screens")
    public ResponseEntity<TheatreResponse> addScreen(
            @PathVariable Long theatreId,
            @Valid @RequestBody ScreenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(theatreService.addScreenToTheatre(theatreId, request));
    }
}
