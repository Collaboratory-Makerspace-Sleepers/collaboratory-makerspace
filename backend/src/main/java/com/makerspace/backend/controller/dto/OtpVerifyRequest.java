package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6)
        @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String code
) {}