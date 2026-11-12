package com.makerspace.backend.services;

import com.makerspace.backend.model.PaymentKind;
import com.makerspace.backend.model.PaymentRecord;
import com.makerspace.backend.model.PaymentStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.checkout.SessionListParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class PaymentHistoryService {
    @Autowired private StripeClient stripeClient;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRecordRepository paymentRepository;

    @Transactional
    public void synchronize(Long userId) throws StripeException {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStripeCustomerId() == null) return;

        var invoices = stripeClient.v1().invoices().list(InvoiceListParams.builder()
                .setCustomer(user.getStripeCustomerId()).setLimit(100L).build());
        for (Invoice invoice : invoices.autoPagingIterable()) {
            if (!"paid".equals(invoice.getStatus())) continue;
            String paymentIntentId = null;
            var payments = stripeClient.v1().invoicePayments().list(InvoicePaymentListParams.builder()
                    .setInvoice(invoice.getId()).setLimit(100L).build());
            for (var payment : payments.autoPagingIterable()) {
                if (payment.getPayment() != null
                        && payment.getPayment().getPaymentIntent() != null) {
                    paymentIntentId = payment.getPayment().getPaymentIntent();
                    break;
                }
            }
            PaymentRecord record = findRecord(user, paymentIntentId, invoice.getId());
            record.setStripeInvoiceId(invoice.getId());
            record.setKind(PaymentKind.MEMBERSHIP);
            record.setDescription(invoice.getDescription() == null
                    ? "Membership payment" : invoice.getDescription());
            record.setAmountCents(Math.toIntExact(invoice.getAmountPaid()));
            record.setCurrency(invoice.getCurrency());
            record.setOccurredAt(Instant.ofEpochSecond(invoice.getCreated()).atZone(ZoneOffset.UTC));
            record.setReceiptUrl(invoice.getHostedInvoiceUrl());
            record.setStatus(PaymentStatus.SUCCEEDED);
            populateCharge(record, paymentIntentId, user);
            paymentRepository.save(record);
        }

        var sessions = stripeClient.v1().checkout().sessions().list(SessionListParams.builder()
                .setCustomer(user.getStripeCustomerId()).setLimit(100L).build());
        for (Session session : sessions.autoPagingIterable()) {
            if (!"payment".equals(session.getMode()) || !"paid".equals(session.getPaymentStatus())
                    || session.getPaymentIntent() == null) continue;
            saveCheckout(user, session);
        }
    }

    @Transactional
    public void confirmCheckout(Long userId, String sessionId) throws StripeException {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Session session = stripeClient.v1().checkout().sessions().retrieve(sessionId);
        if (user.getStripeCustomerId() == null
                || !user.getStripeCustomerId().equals(session.getCustomer())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Checkout belongs to another account");
        }
        if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus())
                || !"payment".equals(session.getMode()) || session.getPaymentIntent() == null) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Checkout payment is not complete");
        }
        saveCheckout(user, session);
    }

    private void saveCheckout(User user, Session session) throws StripeException {
        PaymentRecord record = findRecord(user, session.getPaymentIntent(), session.getInvoice());
        boolean isClass = (session.getMetadata() != null
                && "CLASS".equals(session.getMetadata().get("kind")))
                || (session.getSuccessUrl() != null && session.getSuccessUrl().contains("type=class"));
        record.setKind(isClass ? PaymentKind.CLASS : PaymentKind.DAY_PASS);
        record.setDescription(isClass ? "Class booking" : "Day pass");
        record.setAmountCents(Math.toIntExact(session.getAmountTotal()));
        record.setCurrency(session.getCurrency());
        record.setOccurredAt(Instant.ofEpochSecond(session.getCreated()).atZone(ZoneOffset.UTC));
        record.setStatus(PaymentStatus.SUCCEEDED);
        populateCharge(record, session.getPaymentIntent(), user);
        paymentRepository.save(record);
    }

    private PaymentRecord findRecord(User user, String paymentIntentId, String invoiceId) {
        PaymentRecord record = paymentIntentId == null ? null
                : paymentRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (record == null && invoiceId != null) {
            record = paymentRepository.findByStripeInvoiceId(invoiceId).stream().findFirst().orElse(null);
        }
        if (record == null) record = new PaymentRecord();
        if (record.getUser() != null && !Objects.equals(record.getUser().getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment belongs to another account");
        }
        record.setUser(user);
        record.setStripePaymentIntentId(paymentIntentId);
        return record;
    }

    private void populateCharge(PaymentRecord record, String paymentIntentId, User user)
            throws StripeException {
        if (paymentIntentId == null) return;
        PaymentIntent intent = stripeClient.v1().paymentIntents().retrieve(paymentIntentId);
        if (!Objects.equals(intent.getCustomer(), user.getStripeCustomerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment belongs to another account");
        }
        if (intent.getLatestCharge() == null) return;
        Charge charge = stripeClient.v1().charges().retrieve(intent.getLatestCharge());
        if (charge.getCreated() != null) {
            record.setOccurredAt(Instant.ofEpochSecond(charge.getCreated()).atZone(ZoneOffset.UTC));
        }
        record.setStripeChargeId(charge.getId());
        if (charge.getReceiptUrl() != null) record.setReceiptUrl(charge.getReceiptUrl());
        if (Boolean.TRUE.equals(charge.getRefunded())) record.setStatus(PaymentStatus.REFUNDED);
    }
}
