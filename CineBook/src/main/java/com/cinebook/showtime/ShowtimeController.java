package com.cinebook.showtime;

import com.cinebook.seat.dto.ShowtimeSeatMapResponse;
import com.cinebook.showtime.dto.ShowtimeDto;
import com.cinebook.showtime.dto.ShowtimeRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping("/api/showtimes")
    public ResponseEntity<List<ShowtimeDto>> listShowtimes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long theatreId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(showtimeService.listShowtimes(date, city, movieId, theatreId, status));
    }

    @GetMapping("/api/showtimes/{id}")
    public ResponseEntity<ShowtimeDto> getShowtime(@PathVariable Long id) {
        return ResponseEntity.ok(showtimeService.getShowtime(id));
    }

    @GetMapping("/api/showtimes/{id}/seats")
    public ResponseEntity<ShowtimeSeatMapResponse> getShowtimeSeats(@PathVariable Long id) {
        return ResponseEntity.ok(showtimeService.getShowtimeSeatMap(id));
    }

    @PostMapping("/api/admin/showtimes")
    public ResponseEntity<ShowtimeDto> createShowtime(@Valid @RequestBody ShowtimeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showtimeService.createShowtime(request));
    }

    @PutMapping("/api/admin/showtimes/{id}")
    public ResponseEntity<ShowtimeDto> updateShowtime(
            @PathVariable Long id,
            @Valid @RequestBody ShowtimeRequest request
    ) {
        return ResponseEntity.ok(showtimeService.updateShowtime(id, request));
    }

    @PostMapping("/api/admin/showtimes/{id}/cancel")
    public ResponseEntity<ShowtimeDto> cancelShowtime(@PathVariable Long id) {
        return ResponseEntity.ok(showtimeService.cancelShowtime(id));
    }
}
