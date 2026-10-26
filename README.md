# CineBook — Movie Ticket Booking Platform

CineBook is a full-stack movie discovery and ticket-booking application built with Java 21, Spring Boot, Spring Security, Spring JDBC, PostgreSQL, Flyway, and a responsive HTML/CSS/JavaScript frontend.

## Project location

The Maven application is in the [`CineBook/`](CineBook/) directory. Open that directory in IntelliJ IDEA (it contains `pom.xml`), rather than opening the repository root as the Maven project.

## Features

- Movie, theatre, screen, and showtime discovery
- Seat selection, temporary holds, booking, cancellation, and e-ticket verification
- JWT authentication, BCrypt password hashing, and role-based administrator APIs
- PostgreSQL schema and seed data managed by Flyway
- Administrator reports and CSV export
- Docker Compose setup and automated tests

## Requirements

- JDK 21
- Maven 3.9+
- PostgreSQL 16, or Docker Desktop with Docker Compose

## Run with Docker Compose

1. Open a terminal in `CineBook/`.
2. Copy `.env.example` to `.env`.
3. Set unique, strong values for `DB_PASSWORD`, `JWT_SECRET` (at least 32 characters), and `CINEBOOK_ADMIN_PASSWORD`. Do not commit `.env`.
4. Start the app and database:

   ```powershell
   docker compose up --build
   ```

5. Open `http://localhost:8080/`. Health check: `http://localhost:8080/actuator/health`.

For local IntelliJ setup, environment variables, API documentation, test instructions, and the detailed feature list, read [`CineBook/README.md`](CineBook/README.md).

## Security notes

- Never commit passwords, JWT signing secrets, or populated `.env` files.
- Configure `JWT_COOKIE_SECURE=true` when serving the app over HTTPS.
- The included payment flow is a demo; it does not charge real cards or UPI accounts.

## Tests

From the `CineBook/` directory, run:

```powershell
mvn clean verify
```
