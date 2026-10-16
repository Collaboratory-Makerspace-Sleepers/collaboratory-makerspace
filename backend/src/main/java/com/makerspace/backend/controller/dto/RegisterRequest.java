package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration request.
 *
 * The account is created in {@code PRE_REGISTERED} state and receives no
 * credentials from this call — the caller must prove control of the address via
 * the OTP flow before any token is issued.
 */
public record RegisterRequest(
        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email
) {}
