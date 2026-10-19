package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

public record SignTrainingWaiverRequest(
        @NotBlank @Size(max = 200) String signerName,
        @NotNull @Future ZonedDateTime startTime,
        @NotNull @Future ZonedDateTime endTime,
        @NotNull @Positive int waiverVersion
) {}
