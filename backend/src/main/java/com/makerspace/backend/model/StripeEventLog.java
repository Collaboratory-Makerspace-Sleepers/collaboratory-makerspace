package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "stripe_event_log",
    indexes = {
        @Index(name = "idx_stripe_event_log_status", columnList = "status, received_at"),
        @Index(name = "idx_stripe_event_log_type",   columnList = "event_type, received_at")
    }
)
public class StripeEventLog {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "livemode", nullable = false)
    private boolean livemode;

    @Column(name = "received_at", nullable = false, updatable = false)
    private ZonedDateTime receivedAt = ZonedDateTime.now();

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventProcessingStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "source", nullable = false, length = 30)
    private String source;
}
