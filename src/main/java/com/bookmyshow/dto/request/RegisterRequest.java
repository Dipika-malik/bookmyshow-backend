package com.bookmyshow.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO (Data Transfer Object) for user registration.
 *
 * WHY DTOs instead of passing Model entities directly?
 *   - Models contain fields we don't want exposed (e.g., password hash, internal IDs)
 *   - DTOs let us validate input separately from the domain model
 *   - DTOs can evolve independently of the DB schema
 *
 * @Valid on the controller method parameter triggers these validations.
 * If any fail, Spring throws MethodArgumentNotValidException → 400 Bad Request.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Please provide a valid 10-digit Indian mobile number")
    private String phone;
}
