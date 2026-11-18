package com.makerspace.backend;

import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.services.BillingService;
import com.makerspace.backend.services.MembershipService;
import com.stripe.StripeClient;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
import com.stripe.param.SubscriptionUpdateParams;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MembershipDisplayTest {
    @Test
    void planChangesPersistTheConfirmedStripePlanWithoutWaitingForWebhook() throws Exception {
        BillingService service = new BillingService();
        var memberships = mock(MembershipService.class);
        var plans = mock(MembershipPlanRepository.class);
        var users = mock(UserRepository.class);
        var stripe = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(service, "membershipService", memberships);
        ReflectionTestUtils.setField(service, "membershipPlanRepository", plans);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "stripeClient", stripe);
        User user = new User();
        user.setId(1L);
        user.setStripeCustomerId("cus_owner");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        MembershipPlan currentPlan = new MembershipPlan();
        currentPlan.setCode("STUDIO_SMALL");
        currentPlan.setBillingInterval("MONTH");
        currentPlan.setAmountCents(35000);
        Membership current = new Membership();
        current.setPlan(currentPlan);
        current.setStripeSubscriptionId("sub_owner");
        current.setStripeUpdatedAt(ZonedDateTime.now().minusDays(1));
        when(memberships.currentMembership(1L)).thenReturn(Optional.of(current));
        when(memberships.hasActiveMembership(1L)).thenReturn(true);
        Subscription subscription = new Subscription();
        subscription.setId("sub_owner");
        subscription.setCustomer("cus_owner");
        subscription.setStatus("active");
        subscription.setCreated(ZonedDateTime.now().minusDays(10).toEpochSecond());
        SubscriptionItem item = new SubscriptionItem();
        item.setId("si_owner");
        item.setCurrentPeriodStart(ZonedDateTime.now().minusDays(10).toEpochSecond());
        item.setCurrentPeriodEnd(ZonedDateTime.now().plusDays(20).toEpochSecond());
        Price price = new Price();
        price.setId("price_target");
        item.setPrice(price);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        subscription.setItems(items);
        when(stripe.v1().subscriptions().retrieve("sub_owner")).thenReturn(subscription);
        when(stripe.v1().subscriptions().update(eq("sub_owner"), any(SubscriptionUpdateParams.class)))
                .thenReturn(subscription);

        MembershipPlan target = new MembershipPlan();
        target.setCode("MONTHLY");
        target.setActive(true);
        target.setBillingInterval("MONTH");
        target.setStripePriceId("price_target");
        target.setAmountCents(9500);
        when(plans.findByCode("MONTHLY")).thenReturn(Optional.of(target));
        service.changePlan(1L, "MONTHLY");
        var params = org.mockito.ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripe.v1().subscriptions()).update(eq("sub_owner"), params.capture());
        assertThat(params.getValue().getProrationBehavior()).isEqualTo(SubscriptionUpdateParams.ProrationBehavior.NONE);
        var snapshot = org.mockito.ArgumentCaptor.forClass(SubscriptionSnapshot.class);
        verify(memberships).upsertFromSubscription(snapshot.capture());
        assertThat(snapshot.getValue().stripePriceId()).isEqualTo("price_target");
        assertThat(snapshot.getValue().updatedAt()).isEqualTo(current.getStripeUpdatedAt());

        clearInvocations(stripe.v1().subscriptions(), memberships);
        target.setAmountCents(75000);
        service.changePlan(1L, "MONTHLY");
        verify(stripe.v1().subscriptions()).update(eq("sub_owner"), params.capture());
        assertThat(params.getValue().getProrationBehavior())
                .isEqualTo(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE);
        verify(memberships).upsertFromSubscription(any());
    }

    @Test
    void activeDayPassIsDisplayedAndExpiredDayPassIsNotAnActiveMembership() {
        BillingService service = new BillingService();
        MembershipService memberships = mock(MembershipService.class);
        PaymentRecordRepository payments = mock(PaymentRecordRepository.class);
        ReflectionTestUtils.setField(service, "membershipService", memberships);
        ReflectionTestUtils.setField(service, "paymentRecordRepository", payments);
        when(memberships.currentMembership(1L)).thenReturn(Optional.empty());
        PaymentRecord pass = new PaymentRecord();
        pass.setKind(PaymentKind.DAY_PASS);
        pass.setStatus(PaymentStatus.SUCCEEDED);
        pass.setAmountCents(2500);
        pass.setOccurredAt(ZonedDateTime.now().minusHours(1));
        when(payments.findByUserIdOrderByOccurredAtDesc(1L)).thenReturn(List.of(pass));

        var current = service.getSubscription(1L);
        assertThat(current.planCode()).isEqualTo("DAY_PASS");
        assertThat(current.stripeSubscriptionId()).isNull();
        assertThat(current.currentPeriodEnd()).isEqualTo(pass.getOccurredAt().plusHours(24));

        pass.setOccurredAt(ZonedDateTime.now().minusHours(25));
        assertThat(service.getSubscription(1L)).isNull();
        pass.setOccurredAt(ZonedDateTime.now());
        pass.setStatus(PaymentStatus.REFUNDED);
        assertThat(service.getSubscription(1L)).isNull();
    }
}
