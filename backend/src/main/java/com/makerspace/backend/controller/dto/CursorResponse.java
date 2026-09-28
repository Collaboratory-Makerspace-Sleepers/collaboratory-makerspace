package com.makerspace.backend.controller.dto;

import java.time.ZonedDateTime;

public record CursorResponse(ZonedDateTime lastReceivedAt) {}
