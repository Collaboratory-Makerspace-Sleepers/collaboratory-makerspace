package com.makerspace.backend;

import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipPlan;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.MembershipRepository;
import com.makerspace.backend.services.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipPlanRepository membershipPlanRepository;

    @InjectMocks
    private MembershipService membershipService;

    // Mirrors the config default: app.billing.past-due-retains-access=true
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(membershipService, "pastDueRetainsAccess", true);
    }

    // --- helpers ---

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private MembershipPlan plan() {
        MembershipPlan p = new MembershipPlan();
        p.setId(1L);
        p.setCode("MONTHLY");
        p.setStripePriceId("price_test");
        return p;
    }

    private Membership membership(MembershipStatus status, ZonedDateTime periodEnd) {
        Membership m = new Membership();
        m.setStatus(status);
        m.setCurrentPeriodEnd(periodEnd);
        return m;
    }

    private SubscriptionSnapshot snapshot(User user, MembershipStatus status) {
        return new SubscriptionSnapshot(
                user,
                "sub_123",
                "price_test",
                status,
                ZonedDateTime.now().minusMonths(1),
                ZonedDateTime.now().plusMonths(1),
                false,
                null,
                ZonedDateTime.now()
        );
    }

    // --- hasActiveMembership: no membership row ---

    @Test
    void hasActiveMembership_returnsFalse_whenNoMembershipExists() {
        when(membershipRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    // --- hasActiveMembership: §6 status table ---

    @Test
    void hasActiveMembership_returnsTrue_forActiveWithFuturePeriodEnd() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.ACTIVE, ZonedDateTime.now().plusDays(10))));

        assertThat(membershipService.hasActiveMembership(1L)).isTrue();
    }

    @Test
    void hasActiveMembership_returnsTrue_forTrialingWithFuturePeriodEnd() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.TRIALING, ZonedDateTime.now().plusDays(5))));

        assertThat(membershipService.hasActiveMembership(1L)).isTrue();
    }

    @Test
    void hasActiveMembership_returnsTrue_forPastDueWhenFlagEnabled() {
        ReflectionTestUtils.setField(membershipService, "pastDueRetainsAccess", true);
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.PAST_DUE, ZonedDateTime.now().plusDays(3))));

        assertThat(membershipService.hasActiveMembership(1L)).isTrue();
    }

    @Test
    void hasActiveMembership_returnsFalse_forPastDueWhenFlagDisabled() {
        ReflectionTestUtils.setField(membershipService, "pastDueRetainsAccess", false);
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.PAST_DUE, ZonedDateTime.now().plusDays(3))));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    @Test
    void hasActiveMembership_returnsFalse_forGrace() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.GRACE, ZonedDateTime.now().plusDays(1))));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    @Test
    void hasActiveMembership_returnsFalse_forCanceled() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.CANCELED, ZonedDateTime.now().plusDays(1))));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    @Test
    void hasActiveMembership_returnsFalse_forIncomplete() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.INCOMPLETE, ZonedDateTime.now().plusDays(1))));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    @Test
    void hasActiveMembership_returnsFalse_forPaused() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.PAUSED, ZonedDateTime.now().plusDays(1))));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    @Test
    void hasActiveMembership_returnsFalse_whenPeriodEndIsInPast() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.ACTIVE, ZonedDateTime.now().minusDays(1))));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    @Test
    void hasActiveMembership_returnsFalse_whenPeriodEndIsNull() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.ACTIVE, null)));

        assertThat(membershipService.hasActiveMembership(1L)).isFalse();
    }

    // --- statusOf ---

    @Test
    void statusOf_returnsNone_whenNoMembershipExists() {
        when(membershipRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.statusOf(1L)).isEqualTo(MembershipStatus.NONE);
    }

    @Test
    void statusOf_returnsMembershipStatus_whenExists() {
        when(membershipRepository.findByUserId(1L))
                .thenReturn(Optional.of(membership(MembershipStatus.ACTIVE, ZonedDateTime.now().plusDays(10))));

        assertThat(membershipService.statusOf(1L)).isEqualTo(MembershipStatus.ACTIVE);
    }

    // --- currentMembership ---

    @Test
    void currentMembership_returnsEmpty_whenNoMembershipExists() {
        when(membershipRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(membershipService.currentMembership(1L)).isEmpty();
    }

    @Test
    void currentMembership_returnsMembership_whenExists() {
        Membership m = membership(MembershipStatus.ACTIVE, ZonedDateTime.now().plusDays(10));
        when(membershipRepository.findByUserId(1L)).thenReturn(Optional.of(m));

        assertThat(membershipService.currentMembership(1L)).contains(m);
    }

    // --- upsertFromSubscription ---

    @Test
    void upsertFromSubscription_throws422_whenPlanNotFound() {
        User user = user(1L);
        when(membershipPlanRepository.findByStripePriceId("price_test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.upsertFromSubscription(snapshot(user, MembershipStatus.ACTIVE)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No membership plan found");
    }

    @Test
    void upsertFromSubscription_createsNewMembership_whenNoneExists() {
        User user = user(1L);
        when(membershipPlanRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(plan()));
        when(membershipRepository.findLiveByUserIdForUpdate(eq(1L), any())).thenReturn(Optional.empty());
        when(membershipRepository.findByStripeSubscriptionId("sub_123")).thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Membership result = membershipService.upsertFromSubscription(snapshot(user, MembershipStatus.ACTIVE));

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(result.getStripeSubscriptionId()).isEqualTo("sub_123");
        assertThat(result.getUser()).isSameAs(user);
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void upsertFromSubscription_updatesExistingLiveMembership() {
        User user = user(1L);
        Membership existing = membership(MembershipStatus.ACTIVE, ZonedDateTime.now().plusDays(10));
        existing.setStripeUpdatedAt(ZonedDateTime.now().minusHours(2));

        when(membershipPlanRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(plan()));
        when(membershipRepository.findLiveByUserIdForUpdate(eq(1L), any())).thenReturn(Optional.of(existing));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionSnapshot staleSnapshot = new SubscriptionSnapshot(
                user, "sub_123", "price_test", MembershipStatus.PAST_DUE,
                ZonedDateTime.now().minusMonths(1), ZonedDateTime.now().plusDays(5),
                false, null, ZonedDateTime.now()
        );

        Membership result = membershipService.upsertFromSubscription(staleSnapshot);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PAST_DUE);
        verify(membershipRepository).save(existing);
    }

    @Test
    void upsertFromSubscription_ignoresStaleEvent_whenSnapshotUpdatedAtIsBeforeStored() {
        User user = user(1L);
        Membership existing = membership(MembershipStatus.ACTIVE, ZonedDateTime.now().plusDays(10));
        existing.setStripeUpdatedAt(ZonedDateTime.now());

        when(membershipPlanRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(plan()));
        when(membershipRepository.findLiveByUserIdForUpdate(eq(1L), any())).thenReturn(Optional.of(existing));

        SubscriptionSnapshot staleSnapshot = new SubscriptionSnapshot(
                user, "sub_123", "price_test", MembershipStatus.CANCELED,
                ZonedDateTime.now().minusMonths(2), ZonedDateTime.now().minusDays(1),
                false, ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().minusHours(1)  // updatedAt before existing.stripeUpdatedAt
        );

        Membership result = membershipService.upsertFromSubscription(staleSnapshot);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVE);  // unchanged
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void upsertFromSubscription_doesNotSetUser_whenMembershipAlreadyHasOne() {
        User originalUser = user(1L);
        User snapshotUser = user(2L);

        Membership existing = membership(MembershipStatus.ACTIVE, ZonedDateTime.now().plusDays(10));
        existing.setUser(originalUser);
        existing.setStripeUpdatedAt(ZonedDateTime.now().minusHours(1));

        when(membershipPlanRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(plan()));
        when(membershipRepository.findLiveByUserIdForUpdate(eq(2L), any())).thenReturn(Optional.of(existing));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionSnapshot snap = new SubscriptionSnapshot(
                snapshotUser, "sub_123", "price_test", MembershipStatus.ACTIVE,
                ZonedDateTime.now().minusMonths(1), ZonedDateTime.now().plusMonths(1),
                false, null, ZonedDateTime.now()
        );

        Membership result = membershipService.upsertFromSubscription(snap);

        assertThat(result.getUser()).isSameAs(originalUser);
    }

    // --- applyGracePeriodExpiry ---

    @Test
    void applyGracePeriodExpiry_doesNothing_whenNoExpiredGraceMemberships() {
        when(membershipRepository.findByStatusAndGracePeriodEndsAtLessThanEqual(
                eq(MembershipStatus.GRACE), any())).thenReturn(List.of());

        membershipService.applyGracePeriodExpiry(ZonedDateTime.now());

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void applyGracePeriodExpiry_setsCanceled_forExpiredGraceMembership() {
        Membership m = membership(MembershipStatus.GRACE, ZonedDateTime.now().minusDays(1));
        when(membershipRepository.findByStatusAndGracePeriodEndsAtLessThanEqual(
                eq(MembershipStatus.GRACE), any())).thenReturn(List.of(m));

        membershipService.applyGracePeriodExpiry(ZonedDateTime.now());

        assertThat(m.getStatus()).isEqualTo(MembershipStatus.CANCELED);
    }

    @Test
    void applyGracePeriodExpiry_cancelsAllExpiredMemberships() {
        Membership m1 = membership(MembershipStatus.GRACE, ZonedDateTime.now().minusDays(2));
        Membership m2 = membership(MembershipStatus.GRACE, ZonedDateTime.now().minusDays(1));
        when(membershipRepository.findByStatusAndGracePeriodEndsAtLessThanEqual(
                eq(MembershipStatus.GRACE), any())).thenReturn(List.of(m1, m2));

        membershipService.applyGracePeriodExpiry(ZonedDateTime.now());

        assertThat(m1.getStatus()).isEqualTo(MembershipStatus.CANCELED);
        assertThat(m2.getStatus()).isEqualTo(MembershipStatus.CANCELED);
    }
}
