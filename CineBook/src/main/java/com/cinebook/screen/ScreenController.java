package com.cinebook.screen;

import com.cinebook.screen.dto.ScreenDto;
import com.cinebook.screen.dto.ScreenRequest;
import com.cinebook.screen.dto.SeatLayoutGeneratorRequest;
import com.cinebook.seat.dto.SeatDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScreenController {

    private final ScreenService screenService;

    public ScreenController(ScreenService screenService) {
        this.screenService = screenService;
    }

    @GetMapping("/api/admin/screens")
    public ResponseEntity<List<ScreenDto>> listAllScreens() {
        return ResponseEntity.ok(screenService.listAllScreens());
    }

    @GetMapping("/api/admin/screens/{id}/seats")
    public ResponseEntity<List<SeatDto>> getScreenSeats(@PathVariable Long id) {
        return ResponseEntity.ok(screenService.getScreenSeats(id));
    }

    @PostMapping("/api/admin/screens")
    public ResponseEntity<ScreenDto> createScreen(@Valid @RequestBody ScreenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(screenService.createScreen(request));
    }

    @PostMapping("/api/admin/screens/{id}/seats")
    public ResponseEntity<List<SeatDto>> configureScreenSeats(
            @PathVariable Long id,
            @Valid @RequestBody SeatLayoutGeneratorRequest request
    ) {
        return ResponseEntity.ok(screenService.regenerateSeatLayout(id, request));
    }
}
