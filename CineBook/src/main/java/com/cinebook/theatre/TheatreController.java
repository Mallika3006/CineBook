package com.cinebook.theatre;

import com.cinebook.screen.ScreenService;
import com.cinebook.screen.dto.ScreenDto;
import com.cinebook.theatre.dto.TheatreDto;
import com.cinebook.theatre.dto.TheatreRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TheatreController {

    private final TheatreService theatreService;
    private final ScreenService screenService;

    public TheatreController(TheatreService theatreService, ScreenService screenService) {
        this.theatreService = theatreService;
        this.screenService = screenService;
    }

    @GetMapping("/api/theatres")
    public ResponseEntity<List<TheatreDto>> getTheatres(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Authentication auth
    ) {
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(theatreService.listTheatres(city, includeInactive && isAdmin));
    }

    @GetMapping("/api/theatres/{id}/screens")
    public ResponseEntity<List<ScreenDto>> getTheatreScreens(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Authentication auth
    ) {
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(screenService.listScreensForTheatre(id, includeInactive && isAdmin));
    }

    @PostMapping("/api/admin/theatres")
    public ResponseEntity<TheatreDto> createTheatre(@Valid @RequestBody TheatreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theatreService.createTheatre(request));
    }

    @PutMapping("/api/admin/theatres/{id}")
    public ResponseEntity<TheatreDto> updateTheatre(
            @PathVariable Long id,
            @Valid @RequestBody TheatreRequest request
    ) {
        return ResponseEntity.ok(theatreService.updateTheatre(id, request));
    }
}
