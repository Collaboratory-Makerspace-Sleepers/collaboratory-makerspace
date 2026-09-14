package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.model.EventOutcome;
import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.StripeEventLogRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.MembershipService;
import com.makerspace.backend.services.StripeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeEventServiceTest {

    @Mock private StripeEventLogRepository eventLogRepository;
    @Mock private MembershipService membershipService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private StripeEventService stripeEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stripeEventService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(stripeEventService, "gracePeriodDays", 7);
    }

    // --- helpers ---

    private User user(long id, String stripeCustomerId) {
        User u = new User();
        u.setId(id);
        u.setStripeCustomerId(stripeCustomerId);
        return u;
    }

    private Membership membership(MembershipStatus status) {
        Membership m = new Membership();
        m.setStatus(status);
        m.setCurrentPeriodEnd(ZonedDateTime.now().plusMonths(1));
        return m;
    }

    /** Builds a minimal Stripe subscription payload wrapped in the event envelope. */
    private String subscriptionPayload(String customerId, String subId, String stripeStatus) {
        long now = Instant.now().getEpochSecond();
        return """
                {
                  "data": {
                    "object": {
                      "id": "%s",
                      "customer": "%s",
                      "status": "%s",
                      "current_period_start": %d,
                      "current_period_end": %d,
                      "cancel_at_period_end": false,
                      "canceled_at": null,
                      "updated": %d,
                      "items": {
                        "data": [{ "price": { "id": "price_test" } }]
                      }
                    }
                  }
                }
                """.formatted(subId, customerId, stripeStatus, now - 2592000, now + 2592000, now);
    }

    private StripeEventCommand command(String eventId, String type, String payload) {
        return new StripeEventCommand(eventId, type, "2023-10-16", true,
                ZonedDateTime.now(), "EVENTBRIDGE", payload);
    }

    // --- idempotency guard ---

    @Test
    void handle_returnsDuplicate_whenEventAlreadySeen() {
        doThrow(DataIntegrityViolationException.class)
                .when(eventLogRepository).insertReceived(anyString(), anyString(), anyString(), anyBoolean(), anyString());

        EventOutcome outcome = stripeEventService.handle(
                command("evt_dup", "customer.subscription.updated",
                        subscriptionPayload("cus_123", "sub_123", "active")));

        assertThat(outcome).isEqualTo(EventOutcome.DUPLICATE);
        verify(eventLogRepository, never()).markProcessed(any());
        verify(membershipService, never()).upsertFromSubscription(any());
    }

    @Test
    void handle_processesEvent_andMarksProcessed_forNewEvent() {
        User user = user(1L, "cus_123");
        Membership m = membership(MembershipStatus.ACTIVE);

        when(userRepository.findByStripeCustomerId("cus_123")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        EventOutcome outcome = stripeEventService.handle(
                command("evt_new", "customer.subscription.updated",
                        subscriptionPayload("cus_123", "sub_123", "active")));

        assertThat(outcome).isEqualTo(EventOutcome.PROCESSED);
        verify(eventLogRepository).markProcessed("evt_new");
    }

    @Test
    void handle_rollsBackLogInsert_whenDispatchThrows() {
        // Simulate a dispatch failure (user not found → ResponseStatusException).
        // In a real @Transactional context the whole transaction rolls back;
        // here we verify that markProcessed is never called so retry sees no log row.
        when(userRepository.findByStripeCustomerId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stripeEventService.handle(
                command("evt_fail", "customer.subscription.updated",
                        subscriptionPayload("cus_unknown", "sub_123", "active"))))
                .isInstanceOf(RuntimeException.class);

        verify(eventLogRepository, never()).markProcessed(any());
        verify(eventLogRepository, never()).markSkipped(any());
    }

    // --- dispatch: unknown event type ---

    @Test
    void handle_returnsSkipped_andMarksSkipped_forUnknownEventType() {
        EventOutcome outcome = stripeEventService.handle(
                command("evt_unknown", "some.unknown.event", "{}"));

        assertThat(outcome).isEqualTo(EventOutcome.SKIPPED);
        verify(eventLogRepository).markSkipped("evt_unknown");
        verify(eventLogRepository, never()).markProcessed(any());
    }

    // --- subscription handler ---

    @Test
    void handleSubscription_upsertsFromSnapshot_forSubscriptionUpdated() {
        User user = user(1L, "cus_abc");
        Membership m = membership(MembershipStatus.ACTIVE);

        when(userRepository.findByStripeCustomerId("cus_abc")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        stripeEventService.handle(command("evt_upd", "customer.subscription.updated",
                subscriptionPayload("cus_abc", "sub_abc", "active")));

        ArgumentCaptor<SubscriptionSnapshot> captor = ArgumentCaptor.forClass(SubscriptionSnapshot.class);
        verify(membershipService).upsertFromSubscription(captor.capture());

        SubscriptionSnapshot snap = captor.getValue();
        assertThat(snap.user()).isSameAs(user);
        assertThat(snap.subscriptionId()).isEqualTo("sub_abc");
        assertThat(snap.stripePriceId()).isEqualTo("price_test");
        assertThat(snap.status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void handleSubscription_mapsToGrace_forSubscriptionDeleted() {
        User user = user(1L, "cus_del");
        Membership m = membership(MembershipStatus.GRACE);

        when(userRepository.findByStripeCustomerId("cus_del")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        stripeEventService.handle(command("evt_del", "customer.subscription.deleted",
                subscriptionPayload("cus_del", "sub_del", "canceled")));

        ArgumentCaptor<SubscriptionSnapshot> captor = ArgumentCaptor.forClass(SubscriptionSnapshot.class);
        verify(membershipService).upsertFromSubscription(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(MembershipStatus.GRACE);
    }

    @Test
    void handleSubscription_setsGracePeriodEndsAt_onNewGraceMembership() {
        User user = user(1L, "cus_grace");
        Membership m = membership(MembershipStatus.GRACE);
        // gracePeriodEndsAt is null — simulates a freshly created GRACE row
        assertThat(m.getGracePeriodEndsAt()).isNull();

        when(userRepository.findByStripeCustomerId("cus_grace")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        stripeEventService.handle(command("evt_grace", "customer.subscription.deleted",
                subscriptionPayload("cus_grace", "sub_grace", "canceled")));

        assertThat(m.getGracePeriodEndsAt()).isNotNull();
        assertThat(m.getGracePeriodEndsAt()).isAfter(ZonedDateTime.now().plusDays(6));
    }

    @Test
    void handleSubscription_doesNotOverwriteGracePeriodEndsAt_whenAlreadySet() {
        User user = user(1L, "cus_g2");
        ZonedDateTime existing = ZonedDateTime.now().plusDays(3);
        Membership m = membership(MembershipStatus.GRACE);
        m.setGracePeriodEndsAt(existing);

        when(userRepository.findByStripeCustomerId("cus_g2")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        stripeEventService.handle(command("evt_g2", "customer.subscription.deleted",
                subscriptionPayload("cus_g2", "sub_g2", "canceled")));

        assertThat(m.getGracePeriodEndsAt()).isEqualTo(existing);
    }

    @Test
    void handleSubscription_throws_whenUserNotFound() {
        when(userRepository.findByStripeCustomerId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stripeEventService.handle(
                command("evt_nouser", "customer.subscription.updated",
                        subscriptionPayload("cus_missing", "sub_x", "active"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No user found");
    }

    // --- status mapping ---

    @Test
    void handleSubscription_mapsPastDue_correctly() {
        User user = user(1L, "cus_pd");
        Membership m = membership(MembershipStatus.PAST_DUE);

        when(userRepository.findByStripeCustomerId("cus_pd")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        stripeEventService.handle(command("evt_pd", "customer.subscription.updated",
                subscriptionPayload("cus_pd", "sub_pd", "past_due")));

        ArgumentCaptor<SubscriptionSnapshot> captor = ArgumentCaptor.forClass(SubscriptionSnapshot.class);
        verify(membershipService).upsertFromSubscription(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(MembershipStatus.PAST_DUE);
    }

    @Test
    void handleSubscription_mapsTrialing_correctly() {
        User user = user(1L, "cus_tr");
        Membership m = membership(MembershipStatus.TRIALING);

        when(userRepository.findByStripeCustomerId("cus_tr")).thenReturn(Optional.of(user));
        when(membershipService.upsertFromSubscription(any())).thenReturn(m);

        stripeEventService.handle(command("evt_tr", "customer.subscription.created",
                subscriptionPayload("cus_tr", "sub_tr", "trialing")));

        ArgumentCaptor<SubscriptionSnapshot> captor = ArgumentCaptor.forClass(SubscriptionSnapshot.class);
        verify(membershipService).upsertFromSubscription(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(MembershipStatus.TRIALING);
    }

    // --- Phase 2 event stubs ---

    @Test
    void handle_returnsProcessed_forKnownPhase2EventTypes() {
        // These are routed but not yet fully handled — must not throw or return SKIPPED
        for (String type : new String[]{
                "invoice.paid", "invoice.payment_failed", "checkout.session.completed",
                "charge.refunded", "charge.dispute.created", "customer.deleted"}) {
            EventOutcome outcome = stripeEventService.handle(command("evt_" + type, type, "{}"));
            assertThat(outcome)
                    .as("Expected PROCESSED for event type %s", type)
                    .isEqualTo(EventOutcome.PROCESSED);
        }
    }
}
