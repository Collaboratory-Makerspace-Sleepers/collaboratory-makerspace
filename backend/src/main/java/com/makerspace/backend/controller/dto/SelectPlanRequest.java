package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.Pattern;

public record SelectPlanRequest(
        @Pattern(regexp = "GUEST|MONTHLY|ANNUAL|STUDENT|DAY_PASS", message = "Unknown plan code")
        String planCode
) {}