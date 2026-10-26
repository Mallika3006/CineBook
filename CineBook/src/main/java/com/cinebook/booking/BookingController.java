package com.cinebook.booking;

import com.cinebook.booking.dto.BookingDto;
import com.cinebook.booking.dto.CreateBookingRequest;
import com.cinebook.booking.dto.PricePreviewResponse;
import com.cinebook.booking.dto.SeatHoldRequest;
import com.cinebook.booking.dto.TicketDto;
import com.cinebook.common.ApiResponse;
import com.cinebook.security.UserPrincipal;
import com.cinebook.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/bookings/price-preview")
    public ResponseEntity<PricePreviewResponse> previewPrice(@Valid @RequestBody SeatHoldRequest request) {
        return ResponseEntity.ok(bookingService.calculatePricePreview(request.showtimeId(), request.seatIds()));
    }

    @PostMapping("/api/bookings/holds")
    public ResponseEntity<ApiResponse<Map<String, Object>>> holdSeats(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SeatHoldRequest request
    ) {
        OffsetDateTime expiresAt = bookingService.holdSeats(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Seats held temporarily", Map.of("expiresAt", expiresAt)));
    }

    @PostMapping("/api/bookings")
    public ResponseEntity<BookingDto> createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader
    ) {
        BookingDto created = bookingService.createBooking(principal.getId(), request, idempotencyHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/api/bookings/my")
    public ResponseEntity<List<BookingDto>> getMyBookings(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(bookingService.getMyBookings(principal.getId()));
    }

    @GetMapping("/api/bookings/{id}")
    public ResponseEntity<BookingDto> getBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isAdmin = principal.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(bookingService.getBookingById(id, principal.getId(), isAdmin));
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public ResponseEntity<BookingDto> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isAdmin = principal.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(bookingService.cancelBooking(id, principal.getId(), isAdmin));
    }

    @GetMapping("/api/bookings/{id}/ticket")
    public ResponseEntity<TicketDto> getTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        boolean isAdmin = principal.getRole() == UserRole.ADMIN;
        return ResponseEntity.ok(bookingService.getTicket(id, principal.getId(), isAdmin));
    }

    @GetMapping("/api/bookings/verify/{token}")
    public ResponseEntity<TicketDto> verifyTicket(@PathVariable String token) {
        return ResponseEntity.ok(bookingService.verifyTicketByToken(token));
    }

    @GetMapping("/api/admin/bookings")
    public ResponseEntity<List<BookingDto>> listAdminBookings(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long theatreId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(bookingService.listAdminBookings(reference, date, movieId, theatreId, status));
    }
}
