package com.cinebook.showtime;

import com.cinebook.booking.BookingRepository;
import com.cinebook.config.AppProperties;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.movie.Movie;
import com.cinebook.movie.MovieRepository;
import com.cinebook.screen.Screen;
import com.cinebook.screen.ScreenRepository;
import com.cinebook.seat.Seat;
import com.cinebook.seat.SeatRepository;
import com.cinebook.seat.SeatType;
import com.cinebook.seat.dto.ShowtimeSeatDto;
import com.cinebook.seat.dto.ShowtimeSeatMapResponse;
import com.cinebook.showtime.dto.ShowtimeDto;
import com.cinebook.showtime.dto.ShowtimeRequest;
import com.cinebook.theatre.Theatre;
import com.cinebook.theatre.TheatreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class ShowtimeService {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("2D", "3D", "IMAX 2D", "IMAX 3D", "4DX", "DOLBY ATMOS");

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final TheatreRepository theatreRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final AppProperties appProperties;

    public ShowtimeService(
            ShowtimeRepository showtimeRepository,
            MovieRepository movieRepository,
            ScreenRepository screenRepository,
            TheatreRepository theatreRepository,
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            AppProperties appProperties
    ) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
        this.theatreRepository = theatreRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.appProperties = appProperties;
    }

    public List<ShowtimeDto> listShowtimes(
            LocalDate date,
            String city,
            Long movieId,
            Long theatreId,
            String status
    ) {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        return showtimeRepository.findShowtimes(date, city, movieId, theatreId, status, zoneId).stream()
                .map(ShowtimeDto::from)
                .toList();
    }

    public ShowtimeDto getShowtime(Long id) {
        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + id));
        return ShowtimeDto.from(s);
    }

    @Transactional
    public ShowtimeSeatMapResponse getShowtimeSeatMap(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + showtimeId));

        bookingRepository.cleanupExpiredHolds(showtimeId);

        List<Seat> physicalSeats = seatRepository.findByScreenId(showtime.getScreenId());
        Set<Long> activeBookedSeatIds = bookingRepository.findActiveBookedSeatIds(showtimeId);
        Set<Long> activeHeldSeatIds = bookingRepository.findActiveHeldSeatIds(showtimeId);

        List<ShowtimeSeatDto> seatDtos = physicalSeats.stream().map(seat -> {
            BigDecimal price = resolveSeatPrice(showtime, seat.getSeatType());
            String status;
            if (!seat.isActive() || showtime.getStatus() != ShowtimeStatus.SCHEDULED) {
                status = "UNAVAILABLE";
            } else if (activeBookedSeatIds.contains(seat.getId())) {
                status = "BOOKED";
            } else if (activeHeldSeatIds.contains(seat.getId())) {
                status = "HELD";
            } else {
                status = "AVAILABLE";
            }
            return new ShowtimeSeatDto(
                    seat.getId(),
                    seat.getScreenId(),
                    seat.getRowLabel(),
                    seat.getSeatNumber(),
                    seat.getSeatLabel(),
                    seat.getSeatType().name(),
                    price,
                    status
            );
        }).toList();

        return new ShowtimeSeatMapResponse(
                ShowtimeDto.from(showtime),
                appProperties.getBooking().getMaxSeatsPerOrder(),
                appProperties.getBooking().getConvenienceFeePercent(),
                appProperties.getBooking().getGstOnFeePercent(),
                seatDtos
        );
    }

    public BigDecimal resolveSeatPrice(Showtime showtime, SeatType seatType) {
        return switch (seatType) {
            case REGULAR -> showtime.getTicketPrice();
            case PREMIUM -> showtime.getPremiumPrice();
            case RECLINER -> showtime.getReclinerPrice();
        };
    }

    /**
     * Creates a new showtime with transaction-level overlap protection by locking the screen row.
     */
    @Transactional
    public ShowtimeDto createShowtime(ShowtimeRequest request) {
        return scheduleOrUpdateShowtime(null, request);
    }

    @Transactional
    public ShowtimeDto updateShowtime(Long id, ShowtimeRequest request) {
        Showtime existing = showtimeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + id));
        if (existing.getStatus() == ShowtimeStatus.CANCELLED) {
            throw new BadRequestException("Cannot edit a cancelled showtime.");
        }
        return scheduleOrUpdateShowtime(id, request);
    }

    private ShowtimeDto scheduleOrUpdateShowtime(Long existingId, ShowtimeRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new BadRequestException("Movie not found with ID: " + request.movieId()));
        if (!movie.isActive()) {
            throw new BadRequestException("Cannot schedule a showtime for an inactive movie.");
        }

        // Lock screen row to serialize concurrent showtime creation on the same screen
        Screen screen = screenRepository.findByIdForUpdate(request.screenId())
                .orElseThrow(() -> new BadRequestException("Screen not found with ID: " + request.screenId()));
        if (!screen.isActive()) {
            throw new BadRequestException("Cannot schedule a showtime on an inactive screen.");
        }

        Theatre theatre = theatreRepository.findById(screen.getTheatreId())
                .orElseThrow(() -> new BadRequestException("Theatre not found for screen."));
        if (!theatre.isActive()) {
            throw new BadRequestException("Cannot schedule a showtime in an inactive theatre.");
        }

        if (!request.startTime().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Showtime start time must be in the future.");
        }

        String normalizedFormat = request.format().trim().toUpperCase();
        if (!SUPPORTED_FORMATS.contains(normalizedFormat)) {
            throw new BadRequestException("Unsupported format '" + request.format() + "'. Supported: " + SUPPORTED_FORMATS);
        }

        int bufferMinutes = appProperties.getShowtime().getTurnaroundBufferMinutes();
        OffsetDateTime calculatedEndTime = request.startTime()
                .plusMinutes((long) movie.getDurationMinutes() + bufferMinutes);

        if (!calculatedEndTime.isAfter(request.startTime())) {
            throw new BadRequestException("Calculated show end time must be after start time.");
        }

        if (showtimeRepository.hasOverlappingShowtime(screen.getId(), request.startTime(), calculatedEndTime, existingId)) {
            throw new ConflictException(
                    "Schedule conflict: Screen '" + screen.getName() + "' already has an overlapping showtime between "
                            + request.startTime() + " and " + calculatedEndTime + "."
            );
        }

        BigDecimal basePrice = request.ticketPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal premPrice = request.premiumPrice() != null
                ? request.premiumPrice().setScale(2, RoundingMode.HALF_UP)
                : basePrice.multiply(new BigDecimal("1.35")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal recPrice = request.reclinerPrice() != null
                ? request.reclinerPrice().setScale(2, RoundingMode.HALF_UP)
                : basePrice.multiply(new BigDecimal("1.85")).setScale(2, RoundingMode.HALF_UP);

        Showtime s = new Showtime();
        s.setMovieId(movie.getId());
        s.setScreenId(screen.getId());
        s.setStartTime(request.startTime());
        s.setEndTime(calculatedEndTime);
        s.setFormat(normalizedFormat);
        s.setTicketPrice(basePrice);
        s.setPremiumPrice(premPrice);
        s.setReclinerPrice(recPrice);
        s.setStatus(ShowtimeStatus.SCHEDULED);

        Showtime saved = (existingId == null)
                ? showtimeRepository.save(s)
                : showtimeRepository.update(existingId, s);
        return ShowtimeDto.from(saved);
    }

    /**
     * Cancels a showtime safely and releases any active bookings on that showtime.
     */
    @Transactional
    public ShowtimeDto cancelShowtime(Long id) {
        Showtime showtime = showtimeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found with ID: " + id));
        if (showtime.getStatus() == ShowtimeStatus.CANCELLED) {
            throw new BadRequestException("Showtime is already cancelled.");
        }

        showtimeRepository.updateStatus(id, ShowtimeStatus.CANCELLED);
        bookingRepository.cancelAllBookingsForShowtime(id);
        return ShowtimeDto.from(showtimeRepository.findById(id).orElseThrow());
    }
}
