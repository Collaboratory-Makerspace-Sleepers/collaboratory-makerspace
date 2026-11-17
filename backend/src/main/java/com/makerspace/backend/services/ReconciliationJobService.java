package com.makerspace.backend.services;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.param.EventListParams;
import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.model.EventOutcome;
import com.makerspace.backend.repository.StripeEventLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Slf4j
@Service
public class ReconciliationJobService {

    @Autowired private StripeClient stripeClient;
    @Autowired private StripeEventService stripeEventService;
    @Autowired private StripeEventLogRepository eventLogRepository;

    // Reprocess any Stripe events from the last 25 hours not already in our log.
    // 25h window gives 1h of overlap with the previous run to cover restarts/timezone edges.
    @Scheduled(cron = "0 0 2 * * *")   // 2:00 AM daily
    public void reconcile() {
        ZonedDateTime since = ZonedDateTime.now(ZoneOffset.UTC).minusHours(25);
        log.info("Reconciliation job starting, replaying events since {}", since);

        EventListParams params = EventListParams.builder()
                .setCreated(EventListParams.Created.builder()
                        .setGte(since.toEpochSecond())
                        .build())
                .setLimit(100L)
                .build();

        int recovered = 0;
        try {
            for (Event event : stripeClient.v1().events().list(params).autoPagingIterable()) {
                if (eventLogRepository.existsByEventId(event.getId())) continue;

                StripeEventCommand cmd = new StripeEventCommand(
                        event.getId(),
                        event.getType(),
                        event.getApiVersion(),
                        event.getLivemode(),
                        ZonedDateTime.ofInstant(
                                Instant.ofEpochSecond(event.getCreated()), ZoneOffset.UTC),
                        "RECONCILIATION",
                        event.toJson());

                EventOutcome outcome = stripeEventService.handle(cmd);
                if (outcome == EventOutcome.PROCESSED) {
                    recovered++;
                    log.warn("Reconciliation recovered missed event {} type {}",
                            event.getId(), event.getType());
                }
            }
        } catch (StripeException e) {
            log.error("Reconciliation job failed to list Stripe events: {}", e.getMessage(), e);
            return;
        }

        if (recovered > 0) {
            log.error("Reconciliation ALERT: {} missed event(s) recovered — investigate delivery gap",
                    recovered);
        } else {
            log.info("Reconciliation job complete — no missed events detected");
        }
    }
}
