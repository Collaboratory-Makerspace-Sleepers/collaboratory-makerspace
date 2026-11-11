package com.makerspace.backend;

import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.BillingService;
import com.makerspace.backend.services.MembershipService;
import com.stripe.StripeClient;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionCollection;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionListParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingCheckoutConfirmationTest {
    private final BillingService service = new BillingService();
    private final StripeClient stripe = mock(StripeClient.class, RETURNS_DEEP_STUBS);
    private final UserRepository users = mock(UserRepository.class);
    private final MembershipService memberships = mock(MembershipService.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "stripeClient", stripe);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "membershipService", memberships);
        User user = new User();
        user.setId(1L);
        user.setStripeCustomerId("cus_owner");
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void rejectsUnpaidSessionWithoutGrantingMembership() throws Exception {
        Session session = session("cus_owner", "unpaid");
        when(stripe.v1().checkout().sessions().retrieve("cs_test")).thenReturn(session);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.confirmMembershipCheckout(1L, "cs_test"));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, error.getStatusCode());
        verify(memberships, never()).upsertFromSubscription(any());
    }

    @Test
    void rejectsAnotherCustomersPaidSession() throws Exception {
        when(stripe.v1().checkout().sessions().retrieve("cs_test"))
                .thenReturn(session("cus_other", "paid"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.confirmMembershipCheckout(1L, "cs_test"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(memberships, never()).upsertFromSubscription(any());
    }

    @Test
    void rejectsAnotherCustomersSubscription() throws Exception {
        when(stripe.v1().checkout().sessions().retrieve("cs_test"))
                .thenReturn(session("cus_owner", "paid"));
        Subscription subscription = new Subscription();
        subscription.setCustomer("cus_other");
        when(stripe.v1().subscriptions().retrieve("sub_test")).thenReturn(subscription);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.confirmMembershipCheckout(1L, "cs_test"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(memberships, never()).upsertFromSubscription(any());
    }

    @Test
    void missingSessionSearchesOnlyTheSignedInCustomersActiveSubscriptions() throws Exception {
        SubscriptionCollection subscriptions = new SubscriptionCollection();
        subscriptions.setData(List.of());
        when(stripe.v1().subscriptions().list(any(SubscriptionListParams.class)))
                .thenReturn(subscriptions);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.confirmMembershipCheckout(1L, null));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, error.getStatusCode());
        var params = org.mockito.ArgumentCaptor.forClass(SubscriptionListParams.class);
        verify(stripe.v1().subscriptions()).list(params.capture());
        assertEquals("cus_owner", params.getValue().getCustomer());
        assertEquals(SubscriptionListParams.Status.ACTIVE, params.getValue().getStatus());
        verify(memberships, never()).upsertFromSubscription(any());
    }

    private Session session(String customer, String paymentStatus) {
        Session session = new Session();
        session.setMode("subscription");
        session.setStatus("complete");
        session.setPaymentStatus(paymentStatus);
        session.setCustomer(customer);
        session.setSubscription("sub_test");
        return session;
    }
}
