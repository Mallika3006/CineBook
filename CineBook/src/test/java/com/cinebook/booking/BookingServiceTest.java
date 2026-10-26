package com.cinebook.booking;

import com.cinebook.booking.dto.BookingDto;
import com.cinebook.booking.dto.CreateBookingRequest;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ForbiddenException;
import com.cinebook.payment.DemoPaymentGateway;
import com.cinebook.payment.Payment;
import com.cinebook.payment.PaymentRepository;
import com.cinebook.seat.Seat;
import com.cinebook.seat.SeatRepository;
import com.cinebook.seat.SeatType;
import com.cinebook.showtime.Showtime;
import com.cinebook.showtime.ShowtimeRepository;
import com.cinebook.showtime.ShowtimeService;
import com.cinebook.showtime.ShowtimeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private ShowtimeService showtimeService;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private PaymentRepository paymentRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getBooking().setMaxSeatsPerOrder(10);
        props.getBooking().setConvenienceFeePercent(new BigDecimal("8.50"));
        props.getBooking().setGstOnFeePercent(new BigDecimal("18.00"));
        props.getBooking().setCancellationCutoffMinutes(60);

        bookingService = new BookingService(
                bookingRepository,
                showtimeRepository,
                showtimeService,
                seatRepository,
                paymentRepository,
                new DemoPaymentGateway(),
                props
        );
    }

    @Test
    @DisplayName("Booking multiple seats calculates prices strictly on the server and saves active seat claims")
    void createMultiSeatBookingCalculatesServerPrices() {
        Showtime showtime = buildScheduledShowtime(10L, 1L);
        Seat seatA1 = buildSeat(101L, 1L, "A", 1, SeatType.REGULAR);
        Seat seatG1 = buildSeat(102L, 1L, "G", 1, SeatType.RECLINER);

        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(seatRepository.findAndLockSeatsForScreen(1L, List.of(101L, 102L)))
                .thenReturn(List.of(seatA1, seatG1));
        when(bookingRepository.findActiveBookedSeatIdsInList(10L, List.of(101L, 102L)))
                .thenReturn(Set.of());
        when(bookingRepository.findSeatIdsHeldByOtherUsers(10L, List.of(101L, 102L), 7L))
                .thenReturn(Set.of());

        when(showtimeService.resolveSeatPrice(showtime, SeatType.REGULAR)).thenReturn(new BigDecimal("200.00"));
        when(showtimeService.resolveSeatPrice(showtime, SeatType.RECLINER)).thenReturn(new BigDecimal("500.00"));

        when(bookingRepository.insertBooking(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(500L);
            b.setShowStartTime(showtime.getStartTime());
            b.setShowEndTime(showtime.getEndTime());
            return b;
        });
        when(bookingRepository.findBookingSeats(500L)).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByBookingId(500L)).thenReturn(Optional.empty());

        CreateBookingRequest req = new CreateBookingRequest(10L, List.of(102L, 101L), "idemp-key-1");
        BookingDto result = bookingService.createBooking(7L, req, null);

        // Subtotal = 200 + 500 = 700.00
        // Convenience fee = 8.5% of 700 = 59.50
        // GST on fee = 18% of 59.50 = 10.71
        // Total Fees = 70.21, Total Amount = 770.21
        assertThat(result.subtotal()).isEqualByComparingTo("700.00");
        assertThat(result.fees()).isEqualByComparingTo("70.21");
        assertThat(result.totalAmount()).isEqualByComparingTo("770.21");
        verify(bookingRepository).batchInsertBookingSeats(eq(500L), eq(10L), any());
    }

    @Test
    @DisplayName("Atomic rollback: if one seat in a multi-seat request is already booked, the entire booking is rejected")
    void rejectEntireBookingWhenOneSeatIsUnavailable() {
        Showtime showtime = buildScheduledShowtime(10L, 1L);
        Seat seatA1 = buildSeat(101L, 1L, "A", 1, SeatType.REGULAR);
        Seat seatA2 = buildSeat(102L, 1L, "A", 2, SeatType.REGULAR);

        when(showtimeRepository.findById(10L)).thenReturn(Optional.of(showtime));
        when(seatRepository.findAndLockSeatsForScreen(1L, List.of(101L, 102L)))
                .thenReturn(List.of(seatA1, seatA2));
        // Seat 102L (A2) is already booked!
        when(bookingRepository.findActiveBookedSeatIdsInList(10L, List.of(101L, 102L)))
                .thenReturn(Set.of(102L));

        CreateBookingRequest req = new CreateBookingRequest(10L, List.of(101L, 102L), null);

        assertThatThrownBy(() -> bookingService.createBooking(7L, req, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("A2");

        verify(bookingRepository, never()).insertBooking(any());
        verify(bookingRepository, never()).batchInsertBookingSeats(any(), any(), any());
    }

    @Test
    @DisplayName("Repeated booking request with same idempotency key returns existing booking without duplicate insert")
    void idempotencyKeyPreventsDuplicateBooking() {
        Booking existing = new Booking();
        existing.setId(888L);
        existing.setBookingReference("CB-20261026-ABCD1234");
        existing.setUserId(7L);
        existing.setShowtimeId(10L);
        existing.setStatus(BookingStatus.CONFIRMED);
        existing.setSubtotal(new BigDecimal("200.00"));
        existing.setFees(new BigDecimal("20.06"));
        existing.setTotalAmount(new BigDecimal("220.06"));
        existing.setShowStartTime(OffsetDateTime.now().plusDays(1));

        when(bookingRepository.findByUserAndIdempotencyKey(7L, "dup-key-99"))
                .thenReturn(Optional.of(existing));
        when(bookingRepository.findBookingSeats(888L)).thenReturn(List.of());
        when(paymentRepository.findByBookingId(888L)).thenReturn(Optional.empty());

        CreateBookingRequest req = new CreateBookingRequest(10L, List.of(101L), "dup-key-99");
        BookingDto dto = bookingService.createBooking(7L, req, "dup-key-99");

        assertThat(dto.id()).isEqualTo(888L);
        assertThat(dto.bookingReference()).isEqualTo("CB-20261026-ABCD1234");
        verify(bookingRepository, never()).insertBooking(any());
    }

    @Test
    @DisplayName("One customer cannot view or cancel another customer's booking (ForbiddenException)")
    void preventCrossUserBookingAccessAndCancellation() {
        Booking otherUsersBooking = new Booking();
        otherUsersBooking.setId(55L);
        otherUsersBooking.setUserId(999L); // Owned by user 999
        otherUsersBooking.setStatus(BookingStatus.CONFIRMED);
        otherUsersBooking.setShowStartTime(OffsetDateTime.now().plusDays(1));

        when(bookingRepository.findById(55L)).thenReturn(Optional.of(otherUsersBooking));
        when(bookingRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(otherUsersBooking));

        assertThatThrownBy(() -> bookingService.getBookingById(55L, 7L, false))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> bookingService.cancelBooking(55L, 7L, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Cancellation after showtime start is rejected with BadRequestException")
    void rejectCancellationAfterShowtimeStart() {
        Booking startedBooking = new Booking();
        startedBooking.setId(55L);
        startedBooking.setUserId(7L);
        startedBooking.setStatus(BookingStatus.CONFIRMED);
        startedBooking.setShowStartTime(OffsetDateTime.now().minusMinutes(10));

        when(bookingRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(startedBooking));

        assertThatThrownBy(() -> bookingService.cancelBooking(55L, 7L, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("after the showtime has started");
    }

    private Showtime buildScheduledShowtime(Long id, Long screenId) {
        Showtime s = new Showtime();
        s.setId(id);
        s.setScreenId(screenId);
        s.setStatus(ShowtimeStatus.SCHEDULED);
        s.setStartTime(OffsetDateTime.now().plusDays(1));
        s.setEndTime(OffsetDateTime.now().plusDays(1).plusHours(3));
        s.setTicketPrice(new BigDecimal("200.00"));
        s.setPremiumPrice(new BigDecimal("300.00"));
        s.setReclinerPrice(new BigDecimal("500.00"));
        return s;
    }

    private Seat buildSeat(Long id, Long screenId, String row, int num, SeatType type) {
        Seat s = new Seat();
        s.setId(id);
        s.setScreenId(screenId);
        s.setRowLabel(row);
        s.setSeatNumber(num);
        s.setSeatType(type);
        s.setActive(true);
        return s;
    }
}
