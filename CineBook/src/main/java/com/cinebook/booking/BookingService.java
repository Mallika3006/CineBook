package com.cinebook.booking;

import com.cinebook.booking.dto.BookingDto;
import com.cinebook.booking.dto.BookingSeatItemDto;
import com.cinebook.booking.dto.CreateBookingRequest;
import com.cinebook.booking.dto.PricePreviewResponse;
import com.cinebook.booking.dto.SeatHoldRequest;
import com.cinebook.booking.dto.TicketDto;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.payment.Payment;
import com.cinebook.payment.PaymentGateway;
import com.cinebook.payment.PaymentRepository;
import com.cinebook.payment.PaymentStatus;
import com.cinebook.payment.dto.PaymentDto;
import com.cinebook.seat.Seat;
import com.cinebook.seat.SeatRepository;
import com.cinebook.showtime.Showtime;
import com.cinebook.showtime.ShowtimeRepository;
import com.cinebook.showtime.ShowtimeService;
import com.cinebook.showtime.ShowtimeStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeService showtimeService;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final AppProperties appProperties;

    public BookingService(
            BookingRepository bookingRepository,
            ShowtimeRepository showtimeRepository,
            ShowtimeService showtimeService,
            SeatRepository seatRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            AppProperties appProperties
    ) {
        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
        this.showtimeService = showtimeService;
        this.seatRepository = seatRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.appProperties = appProperties;
    }

    /**
     * Server-side price preview calculation for the checkout page.
     */
    @Transactional(readOnly = true)
    public PricePreviewResponse calculatePricePreview(Long showtimeId, List<Long> rawSeatIds) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + showtimeId));
        List<Long> sortedSeatIds = validateAndDeduplicateSeatIds(rawSeatIds);
        List<Seat> seats = seatRepository.findByScreenIdAndIds(showtime.getScreenId(), sortedSeatIds);
        if (seats.size() != sortedSeatIds.size()) {
            throw new BadRequestException("One or more selected seats do not belong to this screen.");
        }

        List<BookingSeatItemDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Seat s : seats) {
            BigDecimal price = showtimeService.resolveSeatPrice(showtime, s.getSeatType());
            subtotal = subtotal.add(price);
            items.add(new BookingSeatItemDto(
                    s.getId(),
                    s.getRowLabel(),
                    s.getSeatNumber(),
                    s.getSeatLabel(),
                    s.getSeatType().name(),
                    price,
                    "PREVIEW"
            ));
        }

        BigDecimal convenienceFee = subtotal
                .multiply(appProperties.getBooking().getConvenienceFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal gstAmount = convenienceFee
                .multiply(appProperties.getBooking().getGstOnFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalFees = convenienceFee.add(gstAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(totalFees).setScale(2, RoundingMode.HALF_UP);

        return new PricePreviewResponse(
                showtimeId,
                items,
                subtotal.setScale(2, RoundingMode.HALF_UP),
                convenienceFee,
                gstAmount,
                totalFees,
                totalAmount
        );
    }

    /**
     * Places a short-lived temporary seat hold during checkout.
     */
    @Transactional
    public OffsetDateTime holdSeats(Long userId, SeatHoldRequest request) {
        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + request.showtimeId()));
        validateShowtimeBookable(showtime);

        List<Long> sortedSeatIds = validateAndDeduplicateSeatIds(request.seatIds());
        List<Seat> lockedSeats = seatRepository.findAndLockSeatsForScreen(showtime.getScreenId(), sortedSeatIds);
        if (lockedSeats.size() != sortedSeatIds.size()) {
            throw new BadRequestException("One or more requested seats do not belong to the showtime screen.");
        }

        bookingRepository.cleanupExpiredHolds(showtime.getId());
        Set<Long> bookedIds = bookingRepository.findActiveBookedSeatIdsInList(showtime.getId(), sortedSeatIds);
        Set<Long> heldByOthers = bookingRepository.findSeatIdsHeldByOtherUsers(showtime.getId(), sortedSeatIds, userId);

        if (!bookedIds.isEmpty() || !heldByOthers.isEmpty()) {
            throw new ConflictException("One or more selected seats are no longer available. Please refresh the seat map.");
        }

        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(appProperties.getBooking().getHoldDurationSeconds());
        bookingRepository.createSeatHolds(showtime.getId(), userId, sortedSeatIds, expiresAt);
        return expiresAt;
    }

    /**
     * CRITICAL CONCURRENCY-SAFE SEAT BOOKING TRANSACTION:
     * 1. Checks idempotency key for duplicate submission protection.
     * 2. Validates showtime status and start time.
     * 3. Locks requested physical seat rows in deterministic ascending ID order (SELECT ... FOR UPDATE).
     * 4. Verifies every seat belongs to the showtime's screen and is active.
     * 5. Purges expired holds and verifies no active booking or another user's hold exists.
     * 6. Calculates all prices strictly on the server.
     * 7. Inserts booking and batch-inserts active booking_seats rows protected by partial unique index
     *    uq_booking_seats_active_claim (showtime_id, seat_id) WHERE status = 'ACTIVE'.
     * 8. Rolls back the entire transaction if any seat fails or triggers a constraint conflict.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingDto createBooking(Long userId, CreateBookingRequest request, String headerIdempotencyKey) {
        String idempKey = resolveIdempotencyKey(request.idempotencyKey(), headerIdempotencyKey);
        if (idempKey != null) {
            var existing = bookingRepository.findByUserAndIdempotencyKey(userId, idempKey);
            if (existing.isPresent()) {
                return enrichBookingDto(existing.get());
            }
        }

        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + request.showtimeId()));
        validateShowtimeBookable(showtime);

        List<Long> sortedSeatIds = validateAndDeduplicateSeatIds(request.seatIds());

        // Step 1: Acquire deterministic row-level locks on the physical seats in ascending ID order
        List<Seat> lockedSeats = seatRepository.findAndLockSeatsForScreen(showtime.getScreenId(), sortedSeatIds);
        if (lockedSeats.size() != sortedSeatIds.size()) {
            throw new BadRequestException("One or more selected seats do not belong to the screen for this showtime.");
        }
        for (Seat s : lockedSeats) {
            if (!s.isActive()) {
                throw new BadRequestException("Seat " + s.getSeatLabel() + " is currently out of service.");
            }
        }

        // Step 2: Clean up expired holds and verify no other user holds or has booked any requested seat
        bookingRepository.cleanupExpiredHolds(showtime.getId());

        Set<Long> alreadyBookedIds = bookingRepository.findActiveBookedSeatIdsInList(showtime.getId(), sortedSeatIds);
        if (!alreadyBookedIds.isEmpty()) {
            String conflictingLabels = lockedSeats.stream()
                    .filter(s -> alreadyBookedIds.contains(s.getId()))
                    .map(Seat::getSeatLabel)
                    .collect(Collectors.joining(", "));
            throw new ConflictException("Seats [" + conflictingLabels + "] were just booked by another customer. Please choose different seats.");
        }

        Set<Long> heldByOthers = bookingRepository.findSeatIdsHeldByOtherUsers(showtime.getId(), sortedSeatIds, userId);
        if (!heldByOthers.isEmpty()) {
            String heldLabels = lockedSeats.stream()
                    .filter(s -> heldByOthers.contains(s.getId()))
                    .map(Seat::getSeatLabel)
                    .collect(Collectors.joining(", "));
            throw new ConflictException("Seats [" + heldLabels + "] are temporarily held by another customer in checkout.");
        }

        // Step 3: Server-side price calculation
        BigDecimal subtotal = BigDecimal.ZERO;
        List<BookingSeat> bookingSeatRows = new ArrayList<>();
        for (Seat seat : lockedSeats) {
            BigDecimal seatPrice = showtimeService.resolveSeatPrice(showtime, seat.getSeatType());
            subtotal = subtotal.add(seatPrice);

            BookingSeat bs = new BookingSeat();
            bs.setSeatId(seat.getId());
            bs.setRowLabel(seat.getRowLabel());
            bs.setSeatNumber(seat.getSeatNumber());
            bs.setSeatType(seat.getSeatType().name());
            bs.setPriceAtBooking(seatPrice);
            bs.setStatus(BookingSeatStatus.ACTIVE);
            bookingSeatRows.add(bs);
        }

        BigDecimal convenienceFee = subtotal
                .multiply(appProperties.getBooking().getConvenienceFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal gstAmount = convenienceFee
                .multiply(appProperties.getBooking().getGstOnFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalFees = convenienceFee.add(gstAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(totalFees).setScale(2, RoundingMode.HALF_UP);

        String bookingRef = generateBookingReference();
        String verificationToken = generateVerificationToken();

        Booking booking = new Booking();
        booking.setBookingReference(bookingRef);
        booking.setVerificationToken(verificationToken);
        booking.setUserId(userId);
        booking.setShowtimeId(showtime.getId());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        booking.setFees(totalFees);
        booking.setTotalAmount(totalAmount);
        booking.setIdempotencyKey(idempKey);

        try {
            Booking savedBooking = bookingRepository.insertBooking(booking);
            bookingRepository.batchInsertBookingSeats(savedBooking.getId(), showtime.getId(), bookingSeatRows);
            bookingRepository.releaseUserHolds(showtime.getId(), userId);

            PaymentGateway.PaymentExecutionResult payRes = paymentGateway.authorizeDemoOrExternal(bookingRef, totalAmount);
            Payment payment = new Payment();
            payment.setBookingId(savedBooking.getId());
            payment.setProvider(payRes.provider());
            payment.setProviderReference(payRes.providerReference());
            payment.setAmount(totalAmount);
            payment.setStatus(payRes.status());
            paymentRepository.save(payment);

            return enrichBookingDto(savedBooking);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("One or more selected seats were just reserved by another customer. Transaction rolled back.");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getMyBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::enrichBookingDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDto getBookingById(Long bookingId, Long requesterUserId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));
        if (!isAdmin && !booking.getUserId().equals(requesterUserId)) {
            throw new ForbiddenException("You do not have permission to view this booking.");
        }
        return enrichBookingDto(booking);
    }

    @Transactional(readOnly = true)
    public TicketDto getTicket(Long bookingId, Long requesterUserId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));
        if (!isAdmin && !booking.getUserId().equals(requesterUserId)) {
            throw new ForbiddenException("You do not have permission to access this ticket.");
        }
        return buildTicketDto(booking);
    }

    @Transactional(readOnly = true)
    public TicketDto verifyTicketByToken(String verificationToken) {
        Booking booking = bookingRepository.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or unrecognized ticket verification token."));
        return buildTicketDto(booking);
    }

    /**
     * Atomically cancels a booking, updates booking status to CANCELLED,
     * marks booking_seats rows as RELEASED so the partial unique index frees the seats immediately,
     * and preserves historical booking and seat records.
     */
    @Transactional
    public BookingDto cancelBooking(Long bookingId, Long requesterUserId, boolean isAdmin) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));

        if (!isAdmin && !booking.getUserId().equals(requesterUserId)) {
            throw new ForbiddenException("You do not have permission to cancel this booking.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!booking.getShowStartTime().isAfter(now)) {
            throw new BadRequestException("Cannot cancel a booking after the showtime has started.");
        }

        int cutoffMinutes = appProperties.getBooking().getCancellationCutoffMinutes();
        if (!isAdmin && booking.getShowStartTime().minusMinutes(cutoffMinutes).isBefore(now)) {
            throw new BadRequestException(
                    "Customer cancellations are only permitted at least " + cutoffMinutes + " minutes before showtime start."
            );
        }

        bookingRepository.cancelBookingAndReleaseSeats(bookingId);
        paymentRepository.updateStatusByBookingId(bookingId, PaymentStatus.REFUNDED);

        Booking updated = bookingRepository.findById(bookingId).orElseThrow();
        return enrichBookingDto(updated);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> listAdminBookings(
            String reference,
            LocalDate date,
            Long movieId,
            Long theatreId,
            String status
    ) {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        return bookingRepository.searchAdminBookings(reference, date, movieId, theatreId, status, zoneId).stream()
                .map(this::enrichBookingDto)
                .toList();
    }

    private BookingDto enrichBookingDto(Booking b) {
        List<BookingSeatItemDto> seats = bookingRepository.findBookingSeats(b.getId()).stream()
                .map(BookingSeatItemDto::from)
                .toList();
        PaymentDto paymentDto = paymentRepository.findByBookingId(b.getId())
                .map(PaymentDto::from)
                .orElse(null);

        int cutoffMinutes = appProperties.getBooking().getCancellationCutoffMinutes();
        boolean cancellable = b.getStatus() == BookingStatus.CONFIRMED
                && b.getShowStartTime().minusMinutes(cutoffMinutes).isAfter(OffsetDateTime.now());

        return BookingDto.from(b, cancellable, seats, paymentDto);
    }

    private TicketDto buildTicketDto(Booking b) {
        List<BookingSeatItemDto> seats = bookingRepository.findBookingSeats(b.getId()).stream()
                .map(BookingSeatItemDto::from)
                .toList();
        Payment payment = paymentRepository.findByBookingId(b.getId()).orElse(null);

        String qrPayload = "CINEBOOK|REF:" + b.getBookingReference()
                + "|VERIFY:" + b.getVerificationToken()
                + "|STATUS:" + b.getStatus().name();

        return new TicketDto(
                b.getId(),
                b.getBookingReference(),
                b.getVerificationToken(),
                qrPayload,
                b.getStatus().name(),
                b.getCustomerName(),
                b.getMovieTitle(),
                b.getMoviePosterUrl(),
                b.getMovieLanguage(),
                b.getMovieAgeRating(),
                b.getFormat(),
                b.getTheatreName(),
                b.getTheatreAddress(),
                b.getTheatreCity(),
                b.getScreenName(),
                b.getShowStartTime(),
                b.getShowEndTime(),
                seats,
                b.getSubtotal(),
                b.getFees(),
                b.getTotalAmount(),
                payment != null ? payment.getProvider() : "DEMO_CHECKOUT",
                payment != null ? payment.getProviderReference() : "N/A",
                payment != null ? payment.getStatus().name() : "DEMO_CONFIRMED",
                b.getCreatedAt()
        );
    }

    private void validateShowtimeBookable(Showtime showtime) {
        if (showtime.getStatus() != ShowtimeStatus.SCHEDULED) {
            throw new BadRequestException("This showtime is not open for booking (Status: " + showtime.getStatus() + ").");
        }
        if (!showtime.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("This showtime has already started or completed.");
        }
    }

    private List<Long> validateAndDeduplicateSeatIds(List<Long> rawSeatIds) {
        if (rawSeatIds == null || rawSeatIds.isEmpty()) {
            throw new BadRequestException("Please select at least one seat.");
        }
        List<Long> distinctSorted = rawSeatIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();
        if (distinctSorted.isEmpty()) {
            throw new BadRequestException("Invalid seat selection.");
        }
        int maxSeats = appProperties.getBooking().getMaxSeatsPerOrder();
        if (distinctSorted.size() > maxSeats) {
            throw new BadRequestException("You can reserve a maximum of " + maxSeats + " seats per booking.");
        }
        return distinctSorted;
    }

    private String resolveIdempotencyKey(String bodyKey, String headerKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        if (bodyKey != null && !bodyKey.isBlank()) {
            return bodyKey.trim();
        }
        return null;
    }

    private String generateBookingReference() {
        byte[] bytes = new byte[4];
        SECURE_RANDOM.nextBytes(bytes);
        String hex = HexFormat.of().withUpperCase().formatHex(bytes);
        return "CB-" + LocalDate.now().toString().replace("-", "") + "-" + hex;
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
