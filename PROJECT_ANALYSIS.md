# BookMyShow Backend — Project Analysis

## 1. Overview

A movie ticket booking backend built with Spring Boot. It models the BookMyShow domain: movies, theatres/screens/seats, shows (screenings), and bookings with payment, secured by JWT-based authentication.

**Stack:** Spring Boot 3.2.3, Java 17, Maven, Spring Data JPA/Hibernate, Spring Security 6 + JWT (JJWT 0.12.3), H2 in-memory database (dev-ready, MySQL config present for prod), Lombok.

## 2. Main Spring Boot Class

`src/main/java/com/bookmyshow/BookMyShowApplication.java`

Annotated with `@SpringBootApplication`, this is the entry point that bootstraps the embedded server and Spring context.

## 3. Database Configuration

File: `src/main/resources/application.properties`

- **Server port:** `8081`
- **Database:** H2 in-memory — `jdbc:h2:mem:bookmyshow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- **H2 Console:** enabled at `/h2-console` (default for dev inspection)
- **Hibernate DDL:** `create-drop` — schema is dropped and recreated on every app start/stop
- **SQL logging:** `spring.jpa.show-sql=true` with formatted SQL
- **JWT:** secret key + 24-hour expiration (`jwt.expiration=86400000`)
- **MySQL production config** is present but commented out (swap-in ready: update datasource URL, driver, credentials, dialect, and set `ddl-auto=validate`)

## 4. Package Structure

```
com.bookmyshow/
├── BookMyShowApplication.java
├── config/            → SecurityConfig
├── controller/        → 5 REST controllers
├── dto/request/        → Input DTOs (validated)
├── dto/response/       → Output DTOs
├── enums/              → Genre, Role, BookingStatus, ShowStatus, etc.
├── exception/          → GlobalExceptionHandler, custom exceptions
├── model/              → 9 JPA entities
├── repository/         → 9 Spring Data repositories
├── security/           → JWT provider, filter, UserDetailsService
└── service/            → Business logic layer
```

Layered architecture: **Controller → Service → Repository → Model**, with DTOs decoupling the wire format from entities.

## 5. All Controllers

| Controller          | Base Path       | Access                           |
|---------------------|-----------------|----------------------------------|
| `AuthController`    | `/api/auth`     | Public                           |
| `MovieController`   | `/api/movies`   | Mixed (read public, write ADMIN) |
| `TheatreController` | `/api/theatres` | Mixed (read public, write ADMIN) |
| `ShowController`    | `/api/shows`    | Mixed (read public, write ADMIN) |
| `BookingController` | `/api/bookings` | USER (authenticated)             |

### Full Endpoint List

| Method | Endpoint                            | Auth   | Purpose                                    |
|--------|-------------------------------------|--------|--------------------------------------------|
| POST   | `/api/auth/register`                | public | Register user, returns JWT                 |
| POST   | `/api/auth/login`                   | public | Login, returns JWT                         |
| GET    | `/api/movies`                       | public | List/search movies (title, city, language) |
| GET    | `/api/movies/{id}`                  | public | Movie detail                               |
| POST   | `/api/movies`                       | ADMIN  | Create movie                               |
| PUT    | `/api/movies/{id}`                  | ADMIN  | Update movie                               |
| DELETE | `/api/movies/{id}`                  | ADMIN  | Delete movie                               |
| GET    | `/api/theatres`                     | public | List theatres (by city)                    |
| GET    | `/api/theatres/{id}`                | public | Theatre + screens                          |
| POST   | `/api/theatres`                     | ADMIN  | Create theatre                             |
| PUT    | `/api/theatres/{id}`                | ADMIN  | Update theatre                             |
| POST   | `/api/theatres/{theatreId}/screens` | ADMIN  | Add screen to theatre                      |
| GET    | `/api/shows`                        | public | Shows filtered by movieId + city           |
| GET    | `/api/shows/{id}`                   | public | Show detail                                |
| GET    | `/api/shows/{id}/seats`             | public | Seat map for a show                        |
| POST   | `/api/shows`                        | ADMIN  | Create show                                |
| PUT    | `/api/shows/{id}/cancel`            | ADMIN  | Cancel show                                |
| POST   | `/api/bookings`                     | USER   | Create booking (locks seats)               |
| GET    | `/api/bookings`                     | USER   | List own bookings                          |
| GET    | `/api/bookings/{id}`                | USER   | Booking detail                             |
| PUT    | `/api/bookings/{id}/cancel`         | USER   | Cancel booking                             |

Auth uses a JWT Bearer token in the `Authorization` header; admin-only endpoints are role-gated via Spring Security.

## 6. Core Entities

| Entity     | Key Fields                                                                | Relationships                  |
|------------|---------------------------------------------------------------------------|--------------------------------|
| `User`     | name, email, password (hashed), phone, role                               | 1→N Booking                    |
| `Movie`    | title, description, durationMinutes, genre, language, releaseDate, rating | 1→N Show                       |
| `Theatre`  | name, address, city, pincode                                              | 1→N Screen                     |
| `Screen`   | name, totalSeats, screenType                                              | N→1 Theatre, 1→N Seat/Show     |
| `Seat`     | seatNumber, rowName, seatType                                             | N→1 Screen                     |
| `Show`     | showTime, basePrice, status, version (optimistic lock)                    | N→1 Movie/Screen, 1→N ShowSeat |
| `ShowSeat` | status (AVAILABLE/LOCKED/BOOKED), price                                   | N→1 Show/Seat/Booking          |
| `Booking`  | bookingReference, status, totalAmount, numberOfSeats                      | N→1 User/Show, 1→1 Payment     |
| `Payment`  | amount, status, paymentMethod, transactionId                              | 1→1 Booking                    |

`ShowSeat` decouples a physical `Seat` from per-show availability, and `@Version` on `Show` plus the seat lock state machine (AVAILABLE → LOCKED → BOOKED) prevents double-booking under concurrent requests.

## 7. Easiest API to Test

**`POST /api/auth/register`**

Reasons:
- No authentication required (public endpoint)
- No dependent data needed (no movie/theatre/show must exist first)
- Simple single-row insert with straightforward validation

## 8. Exact Postman Request

```
POST http://localhost:8081/api/auth/register
Headers:
  Content-Type: application/json

Body (raw JSON):
{
  "name": "Test User",
  "email": "testuser@example.com",
  "password": "password123",
  "phone": "9876543210"
}
```

**Validation rules** (`RegisterRequest` DTO):
- `name`: required, 2–100 characters
- `email`: required, valid email format
- `password`: required, minimum 6 characters
- `phone`: must match `^[6-9]\d{9}$` (valid 10-digit Indian mobile number)

**Expected response:** `200 OK` with an `AuthResponse` body:
```json
{
  "token": "<jwt-token>",
  "tokenType": "Bearer",
  "userId": 1,
  "name": "Test User",
  "email": "testuser@example.com",
  "role": "USER",
  "expiresIn": 86400000
}
```

## 9. Database Table Updated

Registering a user inserts a new row into the **`USERS`** table (backing the `User` entity), with columns: `name`, `email`, `password` (BCrypt-hashed), `phone`, `role` (defaults to `USER`), `created_at`.
