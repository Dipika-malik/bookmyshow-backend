package com.bookmyshow.enums;

/**
 * USER  - Regular customer who can browse movies and book tickets.
 * ADMIN - Manages movies, theatres, screens, and shows.
 *
 * Spring Security uses this as the ROLE_ prefix convention
 * (e.g., "ROLE_ADMIN") when doing role-based access control.
 */
public enum Role {
    USER,
    ADMIN
}
