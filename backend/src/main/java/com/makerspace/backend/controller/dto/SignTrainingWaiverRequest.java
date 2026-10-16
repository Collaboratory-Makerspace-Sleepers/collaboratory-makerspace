package com.makerspace.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignTrainingWaiverRequest(
        @NotBlank @Size(max = 200) String signerName
) {}