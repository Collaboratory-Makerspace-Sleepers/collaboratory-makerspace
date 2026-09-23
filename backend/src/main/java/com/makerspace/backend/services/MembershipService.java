package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipPlan;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.MembershipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class MembershipService {

    @Autowired private MembershipRepository membershipRepository;
    @Autowired private MembershipPlanRepository membershipPlanRepository;

    @Value("${app.billing.past-due-retains-access:true}")
    private boolean pastDueRetainsAccess;

    public boolean hasActiveMembership(Long userId) {
        return membershipRepository.findByUserId(userId)
                .map(m -> isBookingEligible(m.getStatus())
                        && m.getCurrentPeriodEnd() != null
                        && m.getCurrentPeriodEnd().isAfter(ZonedDateTime.now()))
                .orElse(false);
    }

    public MembershipStatus statusOf(Long userId) {
        return membershipRepository.findByUserId(userId)
                .map(Membership::getStatus)
                .orElse(MembershipStatus.NONE);
    }

    public Optional<Membership> currentMembership(Long userId) {
        return membershipRepository.findByUserId(userId);
    }

    @Transactional
    public Membership upsertFromSubscription(SubscriptionSnapshot snapshot) {
        MembershipPlan plan = membershipPlanRepository
                .findByStripePriceId(snapshot.stripePriceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No membership plan found for price: " + snapshot.stripePriceId()));

        // Find the live membership under a write lock, or fall back to matching by sub ID,
        // or create a new row.
        Membership membership = membershipRepository
                .findLiveByUserIdForUpdate(snapshot.user().getId(), MembershipStatus.INDEX_STATUSES)
                .orElseGet(() -> membershipRepository
                        .findByStripeSubscriptionId(snapshot.subscriptionId())
                        .orElseGet(Membership::new));

        // Out-of-order guard: compare the resource's own timestamp, not the event envelope's.
        if (membership.getStripeUpdatedAt() != null
                && snapshot.updatedAt().isBefore(membership.getStripeUpdatedAt())) {
            log.info("Stale subscription snapshot for sub {}, ignoring", snapshot.subscriptionId());
            return membership;
        }

        if (membership.getUser() == null) {
            membership.setUser(snapshot.user());
        }
        membership.setPlan(plan);
        membership.setStripeSubscriptionId(snapshot.subscriptionId());
        membership.setStatus(snapshot.status());
        membership.setCurrentPeriodStart(snapshot.currentPeriodStart());
        membership.setCurrentPeriodEnd(snapshot.currentPeriodEnd());
        membership.setCancelAtPeriodEnd(snapshot.cancelAtPeriodEnd());
        membership.setCanceledAt(snapshot.canceledAt());
        membership.setStripeUpdatedAt(snapshot.updatedAt());
        membership.setUpdatedAt(ZonedDateTime.now());

        return membershipRepository.save(membership);
    }

    @Transactional
    public void applyGracePeriodExpiry(ZonedDateTime asOf) {
        List<Membership> expired = membershipRepository
                .findByStatusAndGracePeriodEndsAtLessThanEqual(MembershipStatus.GRACE, asOf);

        for (Membership m : expired) {
            log.info("Grace period expired for membership {}, user {}", m.getId(), m.getUserId());
            m.setStatus(MembershipStatus.CANCELED);
            m.setUpdatedAt(ZonedDateTime.now());
        }
    }

    private boolean isBookingEligible(MembershipStatus status) {
        return switch (status) {
            case ACTIVE, TRIALING -> true;
            case PAST_DUE -> pastDueRetainsAccess;
            default -> false;
        };
    }
}
