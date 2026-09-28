package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.MembershipDTO;
import com.makerspace.backend.controller.dto.PaymentMethodDTO;
import com.makerspace.backend.controller.dto.PaymentRecordDTO;
import com.makerspace.backend.controller.dto.PlanDTO;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class BillingService {

    @Autowired
    StripeClient stripeClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    MembershipPlanRepository membershipPlanRepository;

    @Autowired
    MembershipService membershipService;

    @Autowired
    PaymentRecordRepository paymentRecordRepository;

    @Value("${app.billing.success-url}")
    String successUrl;

    @Value("${app.billing.cancel-url}")
    String cancelUrl;

    @Value("${app.billing.portal-return-url}")
    String returnUrl;

    public String createCheckoutSession(Long userId, String planCode, boolean saveCard) throws StripeException {
        Optional<MembershipPlan> plan = membershipPlanRepository.findByCode(planCode);

        if (plan.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
        }

        if (!plan.get().isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan is not active");
        }

        String priceId = plan.get().getStripePriceId();

        Optional<User> user = userRepository.findByIdForUpdate(userId);

        if (user.isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        UserProfile userProfile = user.get().getProfile();
        String stripeCustomerId;

        if (user.get().getStripeCustomerId() == null) {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setName(userProfile.getFirstName()+ " " + userProfile.getLastName())
                    .setEmail(user.get().getEmail())
                    .build();

            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("cust-create-" + userId)
                    .build();

            Customer customer = stripeClient.v1().customers().create(customerParams, options);
            stripeCustomerId = customer.getId();

            user.get().setStripeCustomerId(stripeCustomerId);
        } else {
            stripeCustomerId = user.get().getStripeCustomerId();
        }

        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build();

        SessionCreateParams.Mode mode = (plan.get().getBillingInterval() == null)
                ? SessionCreateParams.Mode.PAYMENT
                : SessionCreateParams.Mode.SUBSCRIPTION;

        String typedSuccessUrl = successUrl + (mode == SessionCreateParams.Mode.SUBSCRIPTION
                ? "?type=membership" : "?type=daypass");

        SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setMode(mode)
                .setSuccessUrl(typedSuccessUrl)
                .setCancelUrl(cancelUrl)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(lineItem);

        // For one-time payments, optionally vault the card for future use.
        // Subscriptions already save the payment method automatically.
        if (mode == SessionCreateParams.Mode.PAYMENT && saveCard) {
            sessionBuilder.setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                            .setSetupFutureUsage(
                                    SessionCreateParams.PaymentIntentData.SetupFutureUsage.OFF_SESSION)
                            .build());
        }

        Session session = stripeClient.v1().checkout().sessions().create(sessionBuilder.build());

        return session.getUrl();
    }

    public String createPortalSession(Long userId) throws StripeException {
        Optional<User> user = userRepository.findById(userId);

        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        String stripeCustomerId = user.get().getStripeCustomerId();

        if (stripeCustomerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No billing account found. Please complete a checkout first");
        }

        com.stripe.param.billingportal.SessionCreateParams sessionParams = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(returnUrl)
                .build();


        com.stripe.model.billingportal.Session session = stripeClient.v1().billingPortal().sessions().create(sessionParams);

        return session.getUrl();
    }

    public void cancelMembership(Long userId) throws StripeException {
        Membership membership = membershipService.currentMembership(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active membership found"));

        String subscriptionId = membership.getStripeSubscriptionId();
        if (subscriptionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No Stripe subscription linked to this membership");
        }

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        stripeClient.v1().subscriptions().update(subscriptionId, params);
    }

    public String createSetupSession(Long userId) throws StripeException {
        Optional<User> userOpt = userRepository.findByIdForUpdate(userId);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        User user = userOpt.get();

        String stripeCustomerId;
        if (user.getStripeCustomerId() == null) {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setName(user.getProfile().getFirstName() + " " + user.getProfile().getLastName())
                    .setEmail(user.getEmail())
                    .build();
            Customer customer = stripeClient.v1().customers().create(customerParams);
            stripeCustomerId = customer.getId();
            user.setStripeCustomerId(stripeCustomerId);
        } else {
            stripeCustomerId = user.getStripeCustomerId();
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SETUP)
                .setCustomer(stripeCustomerId)
                .setCurrency("usd")
                .setSuccessUrl(successUrl + "?type=setup")
                .setCancelUrl(cancelUrl)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .build();

        Session session = stripeClient.v1().checkout().sessions().create(params);
        return session.getUrl();
    }

    public List<PaymentMethodDTO> listPaymentMethods(Long userId) throws StripeException {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (userOpt.get().getStripeCustomerId() == null) {
            return List.of();
        }
        String customerId = userOpt.get().getStripeCustomerId();

        Customer customer = stripeClient.v1().customers().retrieve(customerId);
        String defaultPmId = customer.getInvoiceSettings() != null
                ? customer.getInvoiceSettings().getDefaultPaymentMethod()
                : null;

        PaymentMethodListParams listParams = PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build();

        return stripeClient.v1().paymentMethods().list(listParams).getData().stream()
                .map(pm -> new PaymentMethodDTO(
                        pm.getId(),
                        pm.getCard().getBrand(),
                        pm.getCard().getLast4(),
                        pm.getCard().getExpMonth().intValue(),
                        pm.getCard().getExpYear().intValue(),
                        pm.getId().equals(defaultPmId)))
                .toList();
    }

    public void deletePaymentMethod(Long userId, String paymentMethodId) throws StripeException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No billing account found");
        }

        PaymentMethod pm = stripeClient.v1().paymentMethods().retrieve(paymentMethodId);
        if (!user.getStripeCustomerId().equals(pm.getCustomer())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment method not found");
        }

        stripeClient.v1().paymentMethods().detach(paymentMethodId);
    }

    public void setDefaultPaymentMethod(Long userId, String paymentMethodId) throws StripeException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No billing account found");
        }

        PaymentMethod pm = stripeClient.v1().paymentMethods().retrieve(paymentMethodId);
        if (!user.getStripeCustomerId().equals(pm.getCustomer())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment method not found");
        }

        CustomerUpdateParams params = CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build())
                .build();
        stripeClient.v1().customers().update(user.getStripeCustomerId(), params);
    }

    public MembershipDTO getSubscription(Long userId) {
        Membership membership = membershipService.currentMembership(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Membership found"));

        return new MembershipDTO(
                membership.getStatus(),
                membership.getPlan().getCode(),
                membership.getCurrentPeriodEnd(),
                membership.isCancelAtPeriodEnd(),
                membership.getGracePeriodEndsAt()
        );
    }

    public List<PlanDTO> getPlans() {
        return membershipPlanRepository.findByActiveTrue()
                .stream()
                .sorted(java.util.Comparator.comparingInt(MembershipPlan::getAmountCents))
                .map(p -> new PlanDTO(p.getCode(), p.getDisplayName(), p.getAmountCents(), p.getBillingInterval()))
                .toList();
    }

    public List<PaymentRecordDTO> getPayments(Long userId) {
        return paymentRecordRepository.findByUserIdOrderByOccurredAtDesc(userId)
                .stream()
                .map(paymentRecord -> new PaymentRecordDTO(
                        paymentRecord.getKind(),
                        paymentRecord.getStatus(),
                        paymentRecord.getAmountCents(),
                        paymentRecord.getCurrency(),
                        paymentRecord.getDescription(),
                        paymentRecord.getOccurredAt()
                ))
                .toList();
    }
}
