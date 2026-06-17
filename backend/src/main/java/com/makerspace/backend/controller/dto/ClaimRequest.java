package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimRequest(@NotBlank String token) {}