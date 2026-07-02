# BookMyShow Backend

A movie-ticket-booking REST API backend (inspired by BookMyShow), built with **Spring Boot 3** and **Java 17**. It supports user registration/login with JWT auth, a movie/theatre/show catalog, and concurrency-safe seat booking.

## Tech Stack

- **Java 17**, **Spring Boot 3.2.3**
- **Spring Web** (REST APIs)
- **Spring Data JPA** + **Hibernate** (persistence)
- **Spring Security** + **JWT** (`jjwt`) for stateless authentication
- **MySQL** (production / AWS RDS), **H2** (test scope)
- **Lombok** (boilerplate reduction)
- **Maven** build, **Docker** for deployment

## Features

- 🔐 **Auth** — register & login, BCrypt-hashed passwords, JWT-based stateless auth with role-based access
- 🎬 **Movies** — catalog management (CRUD)
- 🏛️ **Theatres & Screens** — theatre and screen setup
- 🕒 **Shows** — schedule movies on screens with seat inventory
- 🎟️ **Booking** — seat selection and ticket booking with **pessimistic locking** to prevent double-booking
- 💳 **Payment** — payment record tracking
- ⚠️ **Global exception handling** via `@RestControllerAdvice`

## Architecture

Classic layered architecture:

```
Controller  →  Service  →  Repository  →  Database
   (REST)      (logic)      (Spring Data JPA)   (MySQL)
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
| `config`       | Security configuration                  |
| `exception`    | Custom exceptions + global handler      |

## Getting Started (Local)

**Prerequisites:** Java 17, Maven, MySQL running locally.

1. Create a database named `bookmyshow` in MySQL.
2. Set environment variables (or rely on the local defaults in `application.properties`):

   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/bookmyshow?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
   export DB_USERNAME="root"
   export DB_PASSWORD="your_password"
   export JWT_SECRET="any-long-random-string-at-least-32-characters"
   ```

3. Run:

   ```bash
   mvn spring-boot:run
   ```

   App starts on `http://localhost:8082`.

## Running with Docker

```bash
docker build -t bookmyshow-backend .
docker run -d -p 8082:8082 \
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
| POST   | `/api/bookings`           | Book seats for a show   | Auth        |

> Send the JWT as `Authorization: Bearer <token>` for authenticated endpoints. Check the controllers for exact paths and payloads.

## Configuration

All secrets are externalized via environment variables (`${DB_URL}`, `${DB_PASSWORD}`, `${JWT_SECRET}`, etc.) — nothing sensitive is committed. See `src/main/resources/application.properties`.
