package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.CursorResponse;
import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.controller.dto.SweepRequest;
import com.makerspace.backend.model.EventOutcome;
import com.makerspace.backend.repository.StripeEventLogRepository;
import com.makerspace.backend.services.MembershipService;
import com.makerspace.backend.services.StripeEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

/**
 * Internal API consumed by the Lambda layer. Not exposed to end users.
 *
 * Status contract (load-bearing for the Lambda retry/DLQ logic):
 *   200 — processed or skipped; Lambda deletes the SQS message.
 *   409 — duplicate event, already processed; Lambda treats as success and deletes.
 *   422 — permanently unprocessable; Lambda routes straight to DLQ, no retry.
 *   5xx / timeout — Lambda raises, SQS retries up to maxReceiveCount.
 */
@RestController
@RequestMapping("/api/internal/billing")
public class InternalBillingController {

    @Autowired private StripeEventService stripeEventService;
    @Autowired private MembershipService membershipService;
    @Autowired private StripeEventLogRepository eventLogRepository;

    /**
     * Ingest a Stripe event forwarded by the Lambda consumer.
     * 409 means "already processed" — the Lambda must treat this as success
     * and delete the SQS message. Getting this wrong causes an infinite retry loop.
     */
    @PostMapping("/events")
    public ResponseEntity<Void> receiveEvent(@RequestBody StripeEventCommand cmd) {
        EventOutcome outcome = stripeEventService.handle(cmd);
        return switch (outcome) {
            case PROCESSED, SKIPPED -> ResponseEntity.ok().build();
            case DUPLICATE -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }

    /**
     * Fires grace-period expiry side effects. Called hourly by the expiry-sweep Lambda.
     * Defaults asOf to now() if the Lambda omits the field.
     */
    @PostMapping("/sweep")
    @ResponseStatus(HttpStatus.OK)
    public void sweep(@RequestBody(required = false) SweepRequest req) {
        ZonedDateTime asOf = (req != null && req.asOf() != null) ? req.asOf() : ZonedDateTime.now();
        membershipService.applyGracePeriodExpiry(asOf);
    }

    /**
     * Returns the timestamp of the most recently received event.
     * Used by the reconciliation Lambda to determine where to start replaying.
     */
    @GetMapping("/cursor")
    public CursorResponse cursor() {
        return new CursorResponse(eventLogRepository.findMaxReceivedAt().orElse(null));
    }
}
