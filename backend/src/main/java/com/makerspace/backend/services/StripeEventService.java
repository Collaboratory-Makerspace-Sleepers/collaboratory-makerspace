package com.makerspace.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.model.*;
import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.StudioAssignment;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.MembershipRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.StripeEventLogRepository;
import com.makerspace.backend.repository.StudioAssignmentRepository;
import com.makerspace.backend.repository.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private StripeEventLogRepository eventLogRepository;
    @Autowired private CancellationRequestService cancellationRequestService;
    @Autowired private MembershipService membershipService;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StripeEventLogWriter eventLogWriter;
    @Autowired private EmailService emailService;
    @Autowired private StripeClient stripeClient;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private StudioAssignmentRepository studioAssignmentRepository;

    @Value("${app.billing.grace-period-days:7}")
    private int gracePeriodDays;

    @Value("${app.billing.portal-return-url}")
    private String portalReturnUrl;

    /**
     * Entry point for all inbound Stripe events. Insert-first idempotency guard:
     * a PK violation means the event was already seen, so we return DUPLICATE immediately.
     * If dispatch throws, the transaction rolls back including the log insert — the retry
     * will see no log row and process cleanly.
     */
    @Transactional
    public EventOutcome handle(StripeEventCommand cmd) {
        if (!eventLogWriter.tryInsert(cmd.eventId(), cmd.type(), cmd.apiVersion(),
                cmd.livemode(), cmd.source())) {
            log.debug("Duplicate Stripe event {}", cmd.eventId());
            return EventOutcome.DUPLICATE;
        }

        EventOutcome outcome;
        try {
            outcome = dispatch(cmd);
        } catch (Exception e) {
            // Mark the event as FAILED in its own committed transaction so Stripe retries
            // are allowed to reprocess it (tryInsert will reset FAILED → RECEIVED).
            eventLogWriter.markFailed(cmd.eventId());
            log.error("Stripe event {} type {} failed processing, marked FAILED for retry: {}",
                    cmd.eventId(), cmd.type(), e.getMessage(), e);
            throw e;
        }

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
            case "invoice.paid" -> {
                handleInvoicePaid(cmd);
                yield EventOutcome.PROCESSED;
            }
            case "checkout.session.completed" -> {
                handleCheckoutSessionCompleted(cmd);
                yield EventOutcome.PROCESSED;
            }
            case "charge.refunded" -> {
                handleChargeRefunded(cmd);
                yield EventOutcome.PROCESSED;
            }
            case "invoice.payment_failed" -> {
                handleInvoicePaymentFailed(cmd);
                yield EventOutcome.PROCESSED;
            }
            // Remaining handlers pending Phase 3
            case "invoice.payment_action_required",
                 "invoice.upcoming",
                 "checkout.session.async_payment_succeeded",
                 "checkout.session.async_payment_failed",
                 "charge.dispute.created",
                 "payment_intent.payment_failed",
                 "customer.deleted" -> {
                log.info("Stripe event {} type {} received, handler pending Phase 3",
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

        // current_period_start/end are at the subscription root in most Stripe API versions;
        // newer versions (2024-09-30+) place them on subscription items instead.
        JsonNode firstItem = sub.path("items").path("data").path(0);
        long periodStartEpoch = sub.path("current_period_start").asLong(0);
        if (periodStartEpoch == 0) periodStartEpoch = firstItem.path("current_period_start").asLong(0);
        long periodEndEpoch = sub.path("current_period_end").asLong(0);
        if (periodEndEpoch == 0) periodEndEpoch = firstItem.path("current_period_end").asLong(0);

        ZonedDateTime periodStart    = periodStartEpoch > 0 ? epochToUtc(periodStartEpoch) : null;
        ZonedDateTime periodEnd      = periodEndEpoch   > 0 ? epochToUtc(periodEndEpoch)   : null;
        boolean cancelAtPeriodEnd    = sub.path("cancel_at_period_end").asBoolean(false);
        ZonedDateTime canceledAt     = sub.path("canceled_at").isNull() ? null
                                       : epochToUtc(sub.path("canceled_at").asLong());
        // Use subscription's own `updated` timestamp as an ordering signal for stale-event guard.
        // Fall back to `created` for events on older API versions that omit `updated`.
        long updatedEpoch = sub.path("updated").asLong(0);
        if (updatedEpoch == 0) updatedEpoch = sub.path("created").asLong();
        ZonedDateTime updatedAt      = epochToUtc(updatedEpoch);

        SubscriptionSnapshot snapshot = new SubscriptionSnapshot(
                user, subscriptionId, stripePriceId, status,
                periodStart, periodEnd, cancelAtPeriodEnd, canceledAt, updatedAt);

        var membership = membershipService.upsertFromSubscription(snapshot);

        // Set grace-period boundary on cancellation so the expiry sweep knows when to fire.
        if (status == MembershipStatus.GRACE && membership.getGracePeriodEndsAt() == null) {
            membership.setGracePeriodEndsAt(ZonedDateTime.now().plusDays(gracePeriodDays));
        }

        // Studio plan handling: assign a room on activation, release it on cancellation.
        String planCode = membership.getPlan().getCode();
        if (planCode.startsWith("STUDIO_")) {
            if (status == MembershipStatus.ACTIVE || status == MembershipStatus.TRIALING) {
                assignStudioRoom(user, membership, planCode, cmd.eventId());
            } else if (status == MembershipStatus.GRACE || status == MembershipStatus.CANCELED
                    || status == MembershipStatus.EXPIRED) {
                studioAssignmentRepository.deleteByMembershipId(membership.getId());
                log.info("Studio assignment released for membership {} user {}", membership.getId(), user.getId());
            }
        }

        log.info("Stripe event {} processed: sub {} → status {}", cmd.eventId(), subscriptionId, status);
    }

    private void assignStudioRoom(User user, Membership membership, String planCode, String eventId) {
        // Idempotent: skip if the user already holds an assignment for this membership.
        if (studioAssignmentRepository.existsByUserId(user.getId())) {
            return;
        }
        // planCode matches the equipment category (STUDIO_SMALL, STUDIO_MEDIUM, STUDIO_LARGE).
        Equipment room = equipmentRepository.findByCategory(planCode).stream()
                .filter(e -> !studioAssignmentRepository.existsByEquipmentId(e.getId()))
                .findFirst()
                .orElse(null);

        if (room == null) {
            log.warn("No available {} room for user {} (event {}). Staff must assign manually.",
                    planCode, user.getId(), eventId);
            return;
        }

        StudioAssignment assignment = new StudioAssignment();
        assignment.setUser(user);
        assignment.setEquipment(room);
        assignment.setMembership(membership);
        studioAssignmentRepository.save(assignment);
        log.info("Studio room {} assigned to user {} for membership {} (event {})",
                room.getId(), user.getId(), membership.getId(), eventId);
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

    private void handleInvoicePaid(StripeEventCommand cmd) {
        JsonNode invoice = parseObject(cmd);
        String stripeCustomerId = invoice.path("customer").asText();

        User user = userRepository.findByStripeCustomerId(stripeCustomerId).orElse(null);
        if (user == null) {
            log.warn("No user found for Stripe customer {} in invoice.paid event {}", stripeCustomerId, cmd.eventId());
            return;
        }

        int amountPaid    = invoice.path("amount_paid").asInt(0);
        String currency   = invoice.path("currency").asText("usd");
        String invoiceId  = invoice.path("id").asText(null);
        String piId       = invoice.path("payment_intent").asText(null);
        String chargeId   = invoice.path("charge").asText(null);
        String description = invoice.path("lines").path("data").path(0)
                .path("description").asText("Membership payment");
        String subscriptionId = invoice.path("subscription").asText(null);

        Membership membership = subscriptionId != null
                ? membershipRepository.findByStripeSubscriptionId(subscriptionId).orElse(null)
                : null;

        // Persist payment record (idempotent via unique payment_intent constraint).
        if (piId != null && paymentRecordRepository.findByStripePaymentIntentId(piId).isEmpty()) {
            PaymentRecord record = new PaymentRecord();
            record.setUser(user);
            record.setMembership(membership);
            record.setStripeInvoiceId(invoiceId);
            record.setStripePaymentIntentId(piId);
            if (chargeId != null && !chargeId.isEmpty()) {
                record.setStripeChargeId(chargeId);
                try {
                    Charge charge = stripeClient.v1().charges().retrieve(chargeId);
                    record.setReceiptUrl(charge.getReceiptUrl());
                } catch (StripeException e) {
                    log.warn("Could not retrieve charge {} for receipt URL in event {}: {}",
                            chargeId, cmd.eventId(), e.getMessage());
                }
            }
            record.setKind(PaymentKind.MEMBERSHIP);
            record.setAmountCents(amountPaid);
            record.setCurrency(currency);
            record.setStatus(PaymentStatus.SUCCEEDED);
            record.setDescription(description);
            record.setOccurredAt(ZonedDateTime.now());
            paymentRecordRepository.save(record);
        }

        String fullName = user.getProfile() != null
                ? user.getProfile().getFirstName() + " " + user.getProfile().getLastName()
                : user.getEmail();
        try {
            emailService.sendPaymentReceipt(user.getEmail(), fullName, description, amountPaid, currency);
        } catch (Exception e) {
            log.warn("Failed to send payment receipt for invoice event {}: {}", cmd.eventId(), e.getMessage());
        }
    }

    private void handleCheckoutSessionCompleted(StripeEventCommand cmd) {
        JsonNode session = parseObject(cmd);
        String mode = session.path("mode").asText();
        String stripeCustomerId = session.path("customer").asText();

        User user = userRepository.findByStripeCustomerId(stripeCustomerId).orElse(null);
        if (user == null) {
            log.warn("No user found for Stripe customer {} in checkout.session.completed event {}",
                    stripeCustomerId, cmd.eventId());
            return;
        }

        String fullName = user.getProfile() != null
                ? user.getProfile().getFirstName() + " " + user.getProfile().getLastName()
                : user.getEmail();

        switch (mode) {
            case "payment" -> {
                int amountTotal = session.path("amount_total").asInt(0);
                String currency = session.path("currency").asText("usd");
                String piId = session.path("payment_intent").asText(null);
                boolean classPayment = "CLASS".equals(session.path("metadata").path("kind").asText())
                        || session.path("success_url").asText("").contains("type=class");

                if (piId != null && paymentRecordRepository.findByStripePaymentIntentId(piId).isEmpty()) {
                    PaymentRecord record = new PaymentRecord();
                    record.setUser(user);
                    record.setStripePaymentIntentId(piId);
                    // Retrieve charge to get receipt URL
                    try {
                        PaymentIntent pi = stripeClient.v1().paymentIntents().retrieve(piId);
                        String latestChargeId = pi.getLatestCharge();
                        if (latestChargeId != null) {
                            Charge charge = stripeClient.v1().charges().retrieve(latestChargeId);
                            record.setStripeChargeId(latestChargeId);
                            record.setReceiptUrl(charge.getReceiptUrl());
                        }
                    } catch (StripeException e) {
                        log.warn("Could not retrieve charge for PI {} receipt URL in event {}: {}",
                                piId, cmd.eventId(), e.getMessage());
                    }
                    record.setKind(classPayment ? PaymentKind.CLASS : PaymentKind.DAY_PASS);
                    record.setAmountCents(amountTotal);
                    record.setCurrency(currency);
                    record.setStatus(PaymentStatus.SUCCEEDED);
                    record.setDescription(classPayment ? "Class booking" : "Day pass");
                    record.setOccurredAt(ZonedDateTime.now());
                    paymentRecordRepository.save(record);
                }

                try {
                    emailService.sendPaymentReceipt(user.getEmail(), fullName,
                            classPayment ? "Class booking" : "Day pass", amountTotal, currency);
                } catch (Exception e) {
                    log.warn("Failed to send payment receipt for checkout event {}: {}", cmd.eventId(), e.getMessage());
                }
            }
            case "setup" -> {
                String setupIntentId = session.path("setup_intent").asText(null);
                if (setupIntentId != null) {
                    promoteSetupIntentPm(stripeCustomerId, setupIntentId, cmd.eventId());
                }
            }
            case "subscription" -> {
                String subscriptionId = session.path("subscription").asText(null);
                if (subscriptionId != null) {
                    promoteSubscriptionPm(stripeCustomerId, subscriptionId, cmd.eventId());
                }
            }
            default -> log.warn("Unrecognised checkout mode '{}' in event {}", mode, cmd.eventId());
        }
    }

    private void promoteSetupIntentPm(String customerId, String setupIntentId, String eventId) {
        try {
            SetupIntent si = stripeClient.v1().setupIntents().retrieve(setupIntentId);
            String pmId = si.getPaymentMethod();
            if (pmId != null) {
                setCustomerDefaultPm(customerId, pmId);
                log.info("Promoted PM {} to default for customer {} via setup session event {}", pmId, customerId, eventId);
            }
        } catch (StripeException e) {
            log.warn("Failed to promote PM from setup intent {} event {}: {}", setupIntentId, eventId, e.getMessage());
        }
    }

    private void promoteSubscriptionPm(String customerId, String subscriptionId, String eventId) {
        try {
            Subscription sub = stripeClient.v1().subscriptions().retrieve(subscriptionId);
            String pmId = sub.getDefaultPaymentMethod();
            if (pmId != null) {
                setCustomerDefaultPm(customerId, pmId);
                log.info("Promoted PM {} to default for customer {} via subscription checkout event {}", pmId, customerId, eventId);
            }
        } catch (StripeException e) {
            log.warn("Failed to promote PM from subscription {} event {}: {}", subscriptionId, eventId, e.getMessage());
        }
    }

    private void setCustomerDefaultPm(String customerId, String pmId) throws StripeException {
        CustomerUpdateParams params = CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(pmId)
                        .build())
                .build();
        stripeClient.v1().customers().update(customerId, params);
    }

    private void handleInvoicePaymentFailed(StripeEventCommand cmd) {
        JsonNode invoice = parseObject(cmd);
        String stripeCustomerId = invoice.path("customer").asText();

        User user = userRepository.findByStripeCustomerId(stripeCustomerId).orElse(null);
        if (user == null) {
            log.warn("No user found for Stripe customer {} in invoice.payment_failed event {}",
                    stripeCustomerId, cmd.eventId());
            return;
        }

        String subscriptionId = invoice.path("subscription").asText(null);
        if (subscriptionId != null) {
            membershipRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(m -> {
                m.setStatus(MembershipStatus.PAST_DUE);
                membershipRepository.save(m);
                log.info("Membership {} set to PAST_DUE after payment failure (event {})",
                        m.getId(), cmd.eventId());
            });
        }

        String fullName = user.getProfile() != null
                ? user.getProfile().getFirstName() + " " + user.getProfile().getLastName()
                : user.getEmail();

        String portalUrl = null;
        try {
            com.stripe.param.billingportal.SessionCreateParams portalParams =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(stripeCustomerId)
                            .setReturnUrl(portalReturnUrl)
                            .build();
            com.stripe.model.billingportal.Session portalSession =
                    stripeClient.v1().billingPortal().sessions().create(portalParams);
            portalUrl = portalSession.getUrl();
        } catch (StripeException e) {
            log.warn("Could not create portal session for user {} in payment_failed event {}: {}",
                    user.getId(), cmd.eventId(), e.getMessage());
        }

        try {
            emailService.sendPaymentFailed(user.getEmail(), fullName, portalUrl);
        } catch (Exception e) {
            log.warn("Failed to send payment-failed email for event {}: {}", cmd.eventId(), e.getMessage());
        }
    }

    private void handleChargeRefunded(StripeEventCommand cmd) {
        JsonNode charge = parseObject(cmd);
        String chargeId = charge.path("id").asText(null);
        if (chargeId == null) {
            log.warn("charge.refunded event {} missing charge id", cmd.eventId());
            return;
        }
        cancellationRequestService.handleStripeRefundConfirmed(chargeId);
    }

    private ZonedDateTime epochToUtc(long epochSeconds) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
