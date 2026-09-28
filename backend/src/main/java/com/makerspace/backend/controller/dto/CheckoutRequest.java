package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.MembershipPlan;
import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(@NotBlank String planCode) {}
