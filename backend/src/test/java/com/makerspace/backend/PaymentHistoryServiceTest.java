package com.makerspace.backend;

import com.makerspace.backend.model.PaymentKind;
import com.makerspace.backend.model.PaymentRecord;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.PaymentHistoryService;
import com.stripe.StripeClient;
import com.stripe.model.Charge;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceCollection;
import com.stripe.model.InvoicePaymentCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.model.checkout.SessionCollection;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.checkout.SessionListParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

class PaymentHistoryServiceTest {
    private final PaymentHistoryService service = new PaymentHistoryService();
    private final StripeClient stripe = mock(StripeClient.class, RETURNS_DEEP_STUBS);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentRecordRepository records = mock(PaymentRecordRepository.class);
    private final InvoiceCollection invoices = mock(InvoiceCollection.class);
    private final SessionCollection sessions = mock(SessionCollection.class);
    private final Map<String, PaymentRecord> saved = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        ReflectionTestUtils.setField(service, "stripeClient", stripe);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "paymentRepository", records);
        User user = new User();
        user.setId(1L);
        user.setStripeCustomerId("cus_test");
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(stripe.v1().invoices().list(any(InvoiceListParams.class))).thenReturn(invoices);
        when(stripe.v1().checkout().sessions().list(any(SessionListParams.class))).thenReturn(sessions);
        when(invoices.autoPagingIterable()).thenReturn(List.of());
        when(sessions.autoPagingIterable()).thenReturn(List.of());
        when(records.findByStripePaymentIntentId(anyString()))
                .thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        when(records.findByStripeInvoiceId(anyString())).thenAnswer(call -> {
            PaymentRecord record = saved.get(call.getArgument(0));
            return record == null ? List.of() : List.of(record);
        });
        when(records.save(any())).thenAnswer(call -> {
            PaymentRecord record = call.getArgument(0);
            saved.put(record.getStripePaymentIntentId() == null
                    ? record.getStripeInvoiceId() : record.getStripePaymentIntentId(), record);
            return record;
        });
    }

    @Test
    void paidInvoiceHasReceiptAndRepeatedSyncDoesNotDuplicateIt() throws Exception {
        Invoice invoice = new Invoice();
        invoice.setId("in_test");
        invoice.setStatus("paid");
        invoice.setAmountPaid(2500L);
        invoice.setCurrency("usd");
        invoice.setCreated(1700000000L);
        invoice.setHostedInvoiceUrl("https://invoice.stripe.com/test");
        InvoicePaymentCollection payments = mock(InvoicePaymentCollection.class);
        when(payments.autoPagingIterable()).thenReturn(List.of());
        when(stripe.v1().invoicePayments().list(any(InvoicePaymentListParams.class))).thenReturn(payments);
        when(invoices.autoPagingIterable()).thenReturn(List.of(invoice));

        service.synchronize(1L);
        service.synchronize(1L);

        assertEquals(1, saved.size());
        PaymentRecord record = saved.get("in_test");
        assertEquals(PaymentKind.MEMBERSHIP, record.getKind());
        assertEquals(2500, record.getAmountCents());
        assertEquals(invoice.getHostedInvoiceUrl(), record.getReceiptUrl());
    }

    @Test
    void classCheckoutUpdatesExistingRecordWithChargeReceipt() throws Exception {
        Session session = new Session();
        session.setMode("payment");
        session.setPaymentStatus("paid");
        session.setPaymentIntent("pi_test");
        session.setAmountTotal(1500L);
        session.setCurrency("usd");
        session.setCreated(1700000000L);
        session.setMetadata(Map.of("kind", "CLASS"));
        PaymentIntent intent = new PaymentIntent();
        intent.setCustomer("cus_test");
        intent.setLatestCharge("ch_test");
        Charge charge = new Charge();
        charge.setId("ch_test");
        charge.setReceiptUrl("https://pay.stripe.com/receipts/test");
        when(sessions.autoPagingIterable()).thenReturn(List.of(session));
        when(stripe.v1().paymentIntents().retrieve("pi_test")).thenReturn(intent);
        when(stripe.v1().charges().retrieve("ch_test")).thenReturn(charge);

        service.synchronize(1L);
        service.synchronize(1L);

        assertEquals(1, saved.size());
        assertEquals(PaymentKind.CLASS, saved.get("pi_test").getKind());
        assertEquals(charge.getReceiptUrl(), saved.get("pi_test").getReceiptUrl());
    }

    @Test
    void unpaidCheckoutIsNotRecordedAsSuccessful() throws Exception {
        Session session = new Session();
        session.setMode("payment");
        session.setPaymentStatus("unpaid");
        session.setPaymentIntent("pi_unpaid");
        when(sessions.autoPagingIterable()).thenReturn(List.of(session));

        service.synchronize(1L);

        assertEquals(0, saved.size());
    }

    @Test
    void rejectsPaymentIntentOwnedByAnotherCustomer() throws Exception {
        Session session = new Session();
        session.setMode("payment");
        session.setPaymentStatus("paid");
        session.setPaymentIntent("pi_other");
        session.setAmountTotal(1500L);
        session.setCurrency("usd");
        session.setCreated(1700000000L);
        session.setMetadata(Map.of());
        PaymentIntent intent = new PaymentIntent();
        intent.setCustomer("cus_other");
        when(sessions.autoPagingIterable()).thenReturn(List.of(session));
        when(stripe.v1().paymentIntents().retrieve("pi_other")).thenReturn(intent);

        assertThrows(ResponseStatusException.class, () -> service.synchronize(1L));
        assertEquals(0, saved.size());
    }
}
