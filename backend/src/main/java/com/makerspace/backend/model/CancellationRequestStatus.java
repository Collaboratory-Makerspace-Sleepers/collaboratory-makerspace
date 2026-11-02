package com.makerspace.backend.model;

public enum CancellationRequestStatus {
    PENDING,
    APPROVED_WITH_REFUND,
    APPROVED_NO_REFUND,
    REJECTED,
    COMPLETED
}
