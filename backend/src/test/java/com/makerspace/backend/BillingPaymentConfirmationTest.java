package com.makerspace.backend;

import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.BillingService;
import com.makerspace.backend.services.PaymentHistoryService;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerUpdateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillingPaymentConfirmationTest {
    private final BillingService service = new BillingService();
    private final StripeClient stripe = mock(StripeClient.class, RETURNS_DEEP_STUBS);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentHistoryService payments = mock(PaymentHistoryService.class);
    private final Session session = new Session();

    @BeforeEach
    void setup() throws Exception {
        ReflectionTestUtils.setField(service, "stripeClient", stripe);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "paymentHistoryService", payments);
        User user = new User();
        user.setId(1L);
        user.setStripeCustomerId("cus_owner");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        session.setCustomer("cus_owner");
        session.setMode("payment");
        session.setStatus("complete");
        session.setPaymentIntent("pi_paid");
        session.setMetadata(Map.of("saveCard", "true"));
        when(stripe.v1().checkout().sessions().retrieve("cs_paid")).thenReturn(session);
        PaymentIntent intent = new PaymentIntent();
        intent.setCustomer("cus_owner");
        intent.setPaymentMethod("pm_saved");
        when(stripe.v1().paymentIntents().retrieve("pi_paid")).thenReturn(intent);
        PaymentMethod card = new PaymentMethod();
        card.setCustomer("cus_owner");
        when(stripe.v1().paymentMethods().retrieve("pm_saved")).thenReturn(card);
    }

    @Test
    void paidCheckoutPersistsPaymentAndSetsConsentedSavedCardAsDefault() throws Exception {
        service.confirmCheckout(1L, "cs_paid");
        verify(payments).confirmCheckout(1L, "cs_paid");
        verify(stripe.v1().customers()).update(eq("cus_owner"), any(CustomerUpdateParams.class));
    }

    @Test
    void checkoutWithoutCardConsentDoesNotChangeDefaultCard() throws Exception {
        session.setMetadata(Map.of("saveCard", "false"));
        service.confirmCheckout(1L, "cs_paid");
        verify(payments).confirmCheckout(1L, "cs_paid");
        verify(stripe.v1().paymentMethods(), never()).retrieve(anyString());
    }

    @Test
    void rejectsAnotherCustomersCheckoutWithoutSavingPayment() throws Exception {
        session.setCustomer("cus_other");
        assertThrows(ResponseStatusException.class, () -> service.confirmCheckout(1L, "cs_paid"));
        verifyNoInteractions(payments);
    }

    @Test
    void successfulCardSetupSetsVerifiedCardAsDefault() throws Exception {
        session.setMode("setup");
        session.setSetupIntent("seti_saved");
        SetupIntent intent = new SetupIntent();
        intent.setCustomer("cus_owner");
        intent.setStatus("succeeded");
        intent.setPaymentMethod("pm_saved");
        when(stripe.v1().setupIntents().retrieve("seti_saved")).thenReturn(intent);
        service.confirmCheckout(1L, "cs_paid");
        verify(stripe.v1().customers()).update(eq("cus_owner"), any(CustomerUpdateParams.class));
        verifyNoInteractions(payments);
    }
}
