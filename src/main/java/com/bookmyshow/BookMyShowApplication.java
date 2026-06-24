package com.bookmyshow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the application.
 *
 * @SpringBootApplication is a convenience annotation that combines:
 *   - @Configuration       : Marks this as a source of bean definitions
 *   - @EnableAutoConfiguration : Tells Spring Boot to auto-configure based on classpath
 *   - @ComponentScan       : Scans this package and sub-packages for Spring components
 *
 * Architecture Overview:
 * ┌─────────────────────────────────────────────────────────────┐
 * │  CLIENT (Postman / Frontend)                                │
 * │         ↓ HTTP Request                                      │
 * │  CONTROLLER  →  SERVICE  →  REPOSITORY  →  DATABASE (H2)   │
 * │         ↑ HTTP Response                                     │
 * │  (DTOs used for request/response, Models for DB mapping)   │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Core Modules:
 *  - Auth     : JWT-based registration and login
 *  - Movie    : Movie catalog management
 *  - Theatre  : Theatre and screen management
 *  - Show     : Scheduling movies in screens
 *  - Booking  : Seat selection and ticket booking
 *  - Payment  : Payment recording
 */
@SpringBootApplication
public class BookMyShowApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookMyShowApplication.class, args);
    }
}
