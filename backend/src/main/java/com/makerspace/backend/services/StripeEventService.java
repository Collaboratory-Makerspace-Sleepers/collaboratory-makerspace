package com.makerspace.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.model.EventOutcome;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.StripeEventLogRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Slf4j
@Service
public class StripeEventService {

    private final StripeEventLogRepository eventLogRepository;
    private final MembershipService membershipService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.billing.grace-period-days:7}")
    private int gracePeriodDays;

    public StripeEventService(StripeEventLogRepository eventLogRepository,
                              MembershipService membershipService,
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.eventLogRepository = eventLogRepository;
        this.membershipService = membershipService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Entry point for all inbound Stripe events. Insert-first idempotency guard:
     * a PK violation means the event was already seen, so we return DUPLICATE immediately.
     * If dispatch throws, the transaction rolls back including the log insert — the retry
     * will see no log row and process cleanly.
     */
    @Transactional
    public EventOutcome handle(StripeEventCommand cmd) {
        try {
            eventLogRepository.insertReceived(
                    cmd.eventId(), cmd.type(), cmd.apiVersion(), cmd.livemode(), cmd.source());
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate Stripe event {}", cmd.eventId());
            return EventOutcome.DUPLICATE;
        }

        EventOutcome outcome = dispatch(cmd);

        if (outcome == EventOutcome.SKIPPED) {
            eventLogRepository.markSkipped(cmd.eventId());
        } else {
            eventLogRepository.markProcessed(cmd.eventId());
        }

        return outcome;
    }

    private EventOutcome dispatch(StripeEventCommand cmd) {
        return switch (cmd.type()) {
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> {
                handleSubscription(cmd);
                yield EventOutcome.PROCESSED;
            }
            // Invoice and checkout handlers are implemented in Phase 2 alongside
            // BillingService and PaymentRecord creation.
            case "invoice.paid",
                 "invoice.payment_failed",
                 "invoice.payment_action_required",
                 "invoice.upcoming",
                 "checkout.session.completed",
                 "checkout.session.async_payment_succeeded",
                 "checkout.session.async_payment_failed",
                 "charge.refunded",
                 "charge.dispute.created",
                 "payment_intent.payment_failed",
                 "customer.deleted" -> {
                log.info("Stripe event {} type {} received, handler pending Phase 2",
                        cmd.eventId(), cmd.type());
                yield EventOutcome.PROCESSED;
            }
            default -> {
                log.warn("Unhandled Stripe event type {}, event {} — config drift suspected",
                        cmd.type(), cmd.eventId());
                yield EventOutcome.SKIPPED;
            }
        };
    }

    private void handleSubscription(StripeEventCommand cmd) {
        JsonNode sub = parseObject(cmd);
        boolean isDeleted = "customer.subscription.deleted".equals(cmd.type());

        String stripeCustomerId = sub.path("customer").asText();
        User user = userRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No user found for Stripe customer: " + stripeCustomerId));

        String subscriptionId = sub.path("id").asText();
        String stripePriceId  = sub.path("items").path("data").path(0)
                                   .path("price").path("id").asText();
        String stripeStatus   = sub.path("status").asText();

        MembershipStatus status = mapStripeStatus(stripeStatus, isDeleted);

        ZonedDateTime periodStart    = epochToUtc(sub.path("current_period_start").asLong());
        ZonedDateTime periodEnd      = epochToUtc(sub.path("current_period_end").asLong());
        boolean cancelAtPeriodEnd    = sub.path("cancel_at_period_end").asBoolean(false);
        ZonedDateTime canceledAt     = sub.path("canceled_at").isNull() ? null
                                       : epochToUtc(sub.path("canceled_at").asLong());
        ZonedDateTime updatedAt      = epochToUtc(sub.path("updated").asLong());

        SubscriptionSnapshot snapshot = new SubscriptionSnapshot(
                user, subscriptionId, stripePriceId, status,
                periodStart, periodEnd, cancelAtPeriodEnd, canceledAt, updatedAt);

        var membership = membershipService.upsertFromSubscription(snapshot);

        // Set grace-period boundary on cancellation so the expiry sweep knows when to fire.
        if (status == MembershipStatus.GRACE && membership.getGracePeriodEndsAt() == null) {
            membership.setGracePeriodEndsAt(ZonedDateTime.now().plusDays(gracePeriodDays));
        }

        log.info("Stripe event {} processed: sub {} → status {}", cmd.eventId(), subscriptionId, status);
    }

    private MembershipStatus mapStripeStatus(String stripeStatus, boolean isDeleted) {
        if (isDeleted || "canceled".equals(stripeStatus) || "unpaid".equals(stripeStatus)) {
            return MembershipStatus.GRACE;
        }
        return switch (stripeStatus) {
            case "trialing"            -> MembershipStatus.TRIALING;
            case "active"              -> MembershipStatus.ACTIVE;
            case "past_due"            -> MembershipStatus.PAST_DUE;
            case "incomplete"          -> MembershipStatus.INCOMPLETE;
            case "incomplete_expired"  -> MembershipStatus.INCOMPLETE_EXPIRED;
            case "paused"              -> MembershipStatus.PAUSED;
            default -> {
                log.warn("Unknown Stripe subscription status: {}", stripeStatus);
                yield MembershipStatus.NONE;
            }
        };
    }

    private JsonNode parseObject(StripeEventCommand cmd) {
        try {
            return objectMapper.readTree(cmd.payload()).path("data").path("object");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Malformed event payload for event " + cmd.eventId());
        }
    }

    private ZonedDateTime epochToUtc(long epochSeconds) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
