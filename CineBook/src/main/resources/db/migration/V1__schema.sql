-- ============================================================================
-- Flyway Migration V1: CineBook Core Relational Schema
-- Database: PostgreSQL 15+
-- ============================================================================

-- 1. USERS TABLE
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(180) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (LOWER(email));
CREATE INDEX idx_users_role ON users (role);

-- 2. GENRES TABLE
CREATE TABLE genres (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(80) NOT NULL UNIQUE
);

-- 3. MOVIES TABLE
CREATE TABLE movies (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    poster_url          VARCHAR(500) NOT NULL,
    backdrop_url        VARCHAR(500),
    genre_id            BIGINT       NOT NULL REFERENCES genres(id) ON DELETE RESTRICT,
    language            VARCHAR(60)  NOT NULL,
    duration_minutes    INT          NOT NULL CHECK (duration_minutes > 0 AND duration_minutes <= 600),
    release_date        DATE         NOT NULL,
    age_rating          VARCHAR(20)  NOT NULL DEFAULT 'UA',
    cast_details        VARCHAR(500),
    director            VARCHAR(160),
    trailer_url         VARCHAR(500),
    release_status      VARCHAR(20)  NOT NULL DEFAULT 'NOW_SHOWING' CHECK (release_status IN ('NOW_SHOWING', 'COMING_SOON')),
    popularity_score    INT          NOT NULL DEFAULT 100,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_movies_title ON movies (LOWER(title));
CREATE INDEX idx_movies_genre_id ON movies (genre_id);
CREATE INDEX idx_movies_language ON movies (language);
CREATE INDEX idx_movies_release_status ON movies (release_status, is_active);
CREATE INDEX idx_movies_release_date ON movies (release_date DESC);

-- 4. THEATRES TABLE
CREATE TABLE theatres (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(180) NOT NULL,
    address         VARCHAR(350) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_theatres_city ON theatres (LOWER(city), is_active);

-- 5. SCREENS TABLE
CREATE TABLE screens (
    id              BIGSERIAL PRIMARY KEY,
    theatre_id      BIGINT       NOT NULL REFERENCES theatres(id) ON DELETE RESTRICT,
    name            VARCHAR(100) NOT NULL,
    capacity        INT          NOT NULL CHECK (capacity > 0 AND capacity <= 1000),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_screens_theatre_name UNIQUE (theatre_id, name)
);

CREATE INDEX idx_screens_theatre_id ON screens (theatre_id);

-- 6. SEATS TABLE
-- Physical seat layout belongs to a screen and is independent of showtime booking status.
CREATE TABLE seats (
    id              BIGSERIAL PRIMARY KEY,
    screen_id       BIGINT       NOT NULL REFERENCES screens(id) ON DELETE CASCADE,
    row_label       VARCHAR(5)   NOT NULL,
    seat_number     INT          NOT NULL CHECK (seat_number > 0 AND seat_number <= 100),
    seat_type       VARCHAR(20)  NOT NULL CHECK (seat_type IN ('REGULAR', 'PREMIUM', 'RECLINER')),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_seats_screen_row_number UNIQUE (screen_id, row_label, seat_number)
);

CREATE INDEX idx_seats_screen_id ON seats (screen_id, row_label, seat_number);

-- 7. SHOWTIMES TABLE
-- Uses TIMESTAMPTZ for unambiguous point-in-time representation across timezones.
CREATE TABLE showtimes (
    id              BIGSERIAL PRIMARY KEY,
    movie_id        BIGINT         NOT NULL REFERENCES movies(id) ON DELETE RESTRICT,
    screen_id       BIGINT         NOT NULL REFERENCES screens(id) ON DELETE RESTRICT,
    start_time      TIMESTAMPTZ    NOT NULL,
    end_time        TIMESTAMPTZ    NOT NULL,
    format          VARCHAR(25)    NOT NULL DEFAULT '2D' CHECK (format IN ('2D', '3D', 'IMAX 2D', 'IMAX 3D', '4DX', 'DOLBY ATMOS')),
    ticket_price    NUMERIC(10, 2) NOT NULL CHECK (ticket_price > 0),
    premium_price   NUMERIC(10, 2) NOT NULL CHECK (premium_price > 0),
    recliner_price  NUMERIC(10, 2) NOT NULL CHECK (recliner_price > 0),
    status          VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_showtimes_time_order CHECK (end_time > start_time)
);

CREATE INDEX idx_showtimes_movie_start ON showtimes (movie_id, start_time);
CREATE INDEX idx_showtimes_screen_start_end ON showtimes (screen_id, status, start_time, end_time);
CREATE INDEX idx_showtimes_start_time ON showtimes (start_time);

-- 8. BOOKINGS TABLE
CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    booking_reference   VARCHAR(36)    NOT NULL UNIQUE,
    verification_token  VARCHAR(64)    NOT NULL UNIQUE,
    user_id             BIGINT         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    showtime_id         BIGINT         NOT NULL REFERENCES showtimes(id) ON DELETE RESTRICT,
    status              VARCHAR(20)    NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    subtotal            NUMERIC(10, 2) NOT NULL CHECK (subtotal >= 0),
    fees                NUMERIC(10, 2) NOT NULL CHECK (fees >= 0),
    total_amount        NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),
    idempotency_key     VARCHAR(100),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    cancelled_at        TIMESTAMPTZ,
    CONSTRAINT uq_bookings_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_showtime_id ON bookings (showtime_id, status);
CREATE INDEX idx_bookings_reference ON bookings (booking_reference);
CREATE INDEX idx_bookings_created_at ON bookings (created_at DESC);

-- 9. BOOKING_SEATS TABLE
-- Stores price_at_booking so historical tickets never change if showtime prices change.
CREATE TABLE booking_seats (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT         NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    showtime_id         BIGINT         NOT NULL REFERENCES showtimes(id) ON DELETE RESTRICT,
    seat_id             BIGINT         NOT NULL REFERENCES seats(id) ON DELETE RESTRICT,
    price_at_booking    NUMERIC(10, 2) NOT NULL CHECK (price_at_booking >= 0),
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RELEASED')),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- CRITICAL CONCURRENCY CONSTRAINT:
-- Partial unique index enforces that a seat can have AT MOST ONE ACTIVE claim for a showtime,
-- while allowing multiple historical 'RELEASED' rows from cancelled bookings.
CREATE UNIQUE INDEX uq_booking_seats_active_claim
    ON booking_seats (showtime_id, seat_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_booking_seats_booking_id ON booking_seats (booking_id);
CREATE INDEX idx_booking_seats_showtime_status ON booking_seats (showtime_id, status);

-- 10. SEAT_HOLDS TABLE
-- Supports short-lived temporary seat holds during active checkout with strict expiration.
CREATE TABLE seat_holds (
    id              BIGSERIAL PRIMARY KEY,
    showtime_id     BIGINT      NOT NULL REFERENCES showtimes(id) ON DELETE CASCADE,
    seat_id         BIGINT      NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_seat_holds_showtime_seat UNIQUE (showtime_id, seat_id)
);

CREATE INDEX idx_seat_holds_showtime_expires ON seat_holds (showtime_id, expires_at);

-- 11. PAYMENTS TABLE
-- Extensible payment tracking record; never stores raw card numbers or CVVs.
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT         NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    provider            VARCHAR(50)    NOT NULL DEFAULT 'DEMO_CHECKOUT',
    provider_reference  VARCHAR(100)   NOT NULL UNIQUE,
    amount              NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    status              VARCHAR(30)    NOT NULL CHECK (status IN ('DEMO_CONFIRMED', 'PENDING', 'COMPLETED', 'REFUNDED', 'FAILED')),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_booking_id ON payments (booking_id);
