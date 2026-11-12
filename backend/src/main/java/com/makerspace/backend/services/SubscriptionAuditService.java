package com.makerspace.backend.services;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.SubscriptionAuditMismatch;
import com.makerspace.backend.repository.MembershipRepository;
import com.makerspace.backend.repository.SubscriptionAuditMismatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class SubscriptionAuditService {

    @Autowired private MembershipRepository membershipRepository;
    @Autowired private SubscriptionAuditMismatchRepository auditRepository;
    @Autowired private StripeClient stripeClient;

    @Scheduled(cron = "0 0 3 * * SUN")   // 3:00 AM every Sunday
    @Transactional
    public void audit() {
        ZonedDateTime runAt = ZonedDateTime.now(ZoneOffset.UTC);
        log.info("Subscription audit job starting at {}", runAt);

        List<Membership> live = membershipRepository.findByStatusIn(
                List.of(MembershipStatus.ACTIVE, MembershipStatus.TRIALING,
                        MembershipStatus.PAST_DUE, MembershipStatus.GRACE))
                .stream()
                .filter(m -> m.getStripeSubscriptionId() != null)
                .toList();

        int mismatches = 0;
        for (Membership m : live) {
            try {
                Subscription sub = stripeClient.v1().subscriptions().retrieve(m.getStripeSubscriptionId());
                mismatches += compare(m, sub, runAt);
            } catch (StripeException e) {
                log.warn("Audit: could not retrieve subscription {} for membership {}: {}",
                        m.getStripeSubscriptionId(), m.getId(), e.getMessage());
            }
        }

        if (mismatches > 0) {
            log.error("Subscription audit ALERT: {} mismatch(es) found — check /api/internal/billing/audit/mismatches",
                    mismatches);
        } else {
            log.info("Subscription audit complete — all {} subscriptions match Stripe", live.size());
        }
    }

    private int compare(Membership m, Subscription sub, ZonedDateTime runAt) {
        int count = 0;

        // status
        String stripeStatusMapped = mapStripeStatus(sub.getStatus(), "canceled".equals(sub.getStatus()));
        if (!m.getStatus().name().equalsIgnoreCase(stripeStatusMapped)) {
            count += record(m, runAt, "status", m.getStatus().name(), stripeStatusMapped);
        }

        // currentPeriodEnd lives on the first subscription item (Stripe API 2024-09-30+)
        long stripePeriodEnd = (sub.getItems() != null && !sub.getItems().getData().isEmpty())
                ? sub.getItems().getData().get(0).getCurrentPeriodEnd()
                : 0L;
        long localPeriodEnd  = m.getCurrentPeriodEnd() != null
                ? m.getCurrentPeriodEnd().toEpochSecond() : 0L;
        if (Math.abs(stripePeriodEnd - localPeriodEnd) > 60) {
            count += record(m, runAt, "currentPeriodEnd",
                    String.valueOf(localPeriodEnd), String.valueOf(stripePeriodEnd));
        }

        // cancelAtPeriodEnd
        if (m.isCancelAtPeriodEnd() != sub.getCancelAtPeriodEnd()) {
            count += record(m, runAt, "cancelAtPeriodEnd",
                    String.valueOf(m.isCancelAtPeriodEnd()),
                    String.valueOf(sub.getCancelAtPeriodEnd()));
        }

        // priceId
        String localPriceId  = m.getPlan().getStripePriceId();
        String stripePriceId = sub.getItems().getData().isEmpty() ? null
                : sub.getItems().getData().get(0).getPrice().getId();
        if (!Objects.equals(localPriceId, stripePriceId)) {
            count += record(m, runAt, "priceId", localPriceId, stripePriceId);
        }

        return count;
    }

    private int record(Membership m, ZonedDateTime runAt,
                       String field, String local, String stripe) {
        log.warn("Audit mismatch: membership {} user {} field={} local={} stripe={}",
                m.getId(), m.getUserId(), field, local, stripe);
        SubscriptionAuditMismatch row = new SubscriptionAuditMismatch();
        row.setAuditRunAt(runAt);
        row.setUserId(m.getUserId());
        row.setStripeSubscriptionId(m.getStripeSubscriptionId());
        row.setFieldName(field);
        row.setLocalValue(local);
        row.setStripeValue(stripe);
        auditRepository.save(row);
        return 1;
    }

    private String mapStripeStatus(String s, boolean isDeleted) {
        if (isDeleted || "canceled".equals(s) || "unpaid".equals(s)) return "GRACE";
        return switch (s) {
            case "active"   -> "ACTIVE";
            case "trialing" -> "TRIALING";
            case "past_due" -> "PAST_DUE";
            default -> s.toUpperCase();
        };
    }
}
