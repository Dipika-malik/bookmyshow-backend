# BookMyShow Backend

A movie-ticket-booking REST API backend (inspired by BookMyShow), built with **Spring Boot 3** and **Java 17**. It supports user registration/login with JWT auth, a movie/theatre/show catalog, Redis-backed caching and seat holds, and concurrency-safe seat booking.

## Tech Stack

- **Java 17**, **Spring Boot 3.2.3**
- **Spring Web** (REST APIs)
- **Spring Data JPA** + **Hibernate** (persistence)
- **Spring Security** + **JWT** (`jjwt`) for stateless authentication
- **MySQL** (production / AWS RDS), **H2** (test scope)
- **Redis** (Spring Cache + Lua scripts) for caching and temporary seat holds
- **Lombok** (boilerplate reduction)
- **Maven** build, **Docker** for deployment

## Features

- 🔐 **Auth** — register & login, BCrypt-hashed passwords, JWT-based stateless auth with role-based access
- 🎬 **Movies** — catalog management (CRUD)
- 🏛️ **Theatres & Screens** — theatre and screen setup
- 🕒 **Shows** — schedule movies on screens with seat inventory
- ⚡ **Caching** — movie catalogue cached in Redis (cache-aside, TTL, evicted on writes)
- ⏳ **Seat holds** — seats held for 10 min in Redis during checkout; atomic all-or-nothing via Lua, auto-released by TTL
- 🎟️ **Booking** — seat selection and ticket booking with **pessimistic locking** to prevent double-booking
- 💳 **Payment** — payment record tracking
- ⚠️ **Global exception handling** via `@RestControllerAdvice`

## Architecture

Classic layered architecture:

```
Controller  →  Service  →  Repository  →  Database
   (REST)      (logic)      (Spring Data JPA)   (MySQL)
                  │
                  └──→  Redis  (movie cache · seat holds)
        DTOs for request/response · Entities for persistence
```

Package structure (`com.bookmyshow`):

| Package        | Responsibility                          |
|----------------|-----------------------------------------|
| `controller`   | REST endpoints                          |
| `service`      | Business logic                          |
| `repository`   | Spring Data JPA interfaces              |
| `model`        | JPA entities                            |
| `dto`          | Request/response data transfer objects  |
| `enums`        | Domain enums (Role, SeatType, etc.)     |
| `security`     | JWT provider, auth filter, user details |
| `config`       | Security and Redis/cache configuration  |
| `event`        | Domain events (e.g. booking confirmed)  |
| `exception`    | Custom exceptions + global handler      |

## Getting Started (Local)

**Prerequisites:** Java 17, Maven, MySQL and Redis running locally (`brew install redis && brew services start redis`).

1. Create a database named `bookmyshow` in MySQL.
2. Set environment variables (or rely on the local defaults in `application.properties`):

   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/bookmyshow?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
   export DB_USERNAME="root"
   export DB_PASSWORD="your_password"
   export JWT_SECRET="any-long-random-string-at-least-32-characters"
   export REDIS_HOST="localhost"   # default
   ```

3. Run:

   ```bash
   mvn spring-boot:run
   ```

   App starts on `http://localhost:8082`.

## Running with Docker

```bash
docker network create bms
docker run -d --name redis --network bms redis:7-alpine
docker build -t bookmyshow-backend .
docker run -d -p 8082:8082 --network bms \
  -e REDIS_HOST=redis \
  -e DB_URL="jdbc:mysql://<host>:3306/bookmyshow?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
  -e DB_USERNAME="admin" \
  -e DB_PASSWORD="<password>" \
  -e JWT_SECRET="<secret>" \
  bookmyshow-backend
```

## Deployment

Deployed on **AWS EC2** (Docker) backed by **AWS RDS MySQL**. See [DEPLOYMENT.md](DEPLOYMENT.md) for the full step-by-step guide.

## API Overview

| Method | Endpoint                  | Description              | Auth        |
|--------|---------------------------|-------------------------|-------------|
| POST   | `/api/auth/register`      | Register a new user     | Public      |
| POST   | `/api/auth/login`         | Login, returns JWT      | Public      |
| GET    | `/api/movies`             | List movies             | Public      |
| POST   | `/api/movies`             | Add a movie             | Auth        |
| GET    | `/api/theatres`           | List theatres           | Public      |
| POST   | `/api/theatres`           | Add a theatre           | Auth        |
| POST   | `/api/shows`              | Create a show           | Auth        |
| GET    | `/api/shows/...`          | Browse shows            | Public      |
| GET    | `/api/shows/{id}/seats`   | Seat map (held seats show as `LOCKED`) | Public |
| POST   | `/api/bookings/holds`     | Hold seats for 10 min   | Auth        |
| DELETE | `/api/bookings/holds?showId=&showSeatIds=` | Release your holds | Auth |
| POST   | `/api/bookings`           | Book seats for a show   | Auth        |

> Send the JWT as `Authorization: Bearer <token>` for authenticated endpoints. Check the controllers for exact paths and payloads.

## Redis Design

**Caching (cache-aside).** `MovieService` reads are annotated with `@Cacheable` and stored in Redis as JSON with a 10-minute TTL (`cache.movies.ttl`). Creates, updates and deletes evict the affected entries. Shows and seat maps are deliberately *not* cached — availability changes on every booking.

**Seat holds.** `POST /api/bookings/holds` writes one key per seat — `bms:hold:{show:<id>}:seat:<showSeatId>` → `userId` — with a TTL (`seat-hold.ttl`, default 10 min).
- A Lua script checks and sets all requested seats atomically, so a multi-seat hold is all-or-nothing and two users can never both hold the same seat.
- The `{show:<id>}` hash tag keeps a show's seats in one Redis Cluster slot so the multi-key script still works if Redis is sharded.
- Abandoned checkouts are released by the TTL — no cleanup job. Holds are deleted after the booking transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`).
- `POST /api/bookings` rejects seats held by another user before taking the short `PESSIMISTIC_WRITE` DB lock, which remains the final guarantee against double-booking.

**When Redis is down** the app degrades instead of failing: cache reads fall through to MySQL, the seat map shows DB status, booking still works via the DB lock, and only placing a hold returns `503`.

## Configuration

All secrets are externalized via environment variables (`${DB_URL}`, `${DB_PASSWORD}`, `${JWT_SECRET}`, `${REDIS_HOST}`, etc.) — nothing sensitive is committed. See `src/main/resources/application.properties`.
