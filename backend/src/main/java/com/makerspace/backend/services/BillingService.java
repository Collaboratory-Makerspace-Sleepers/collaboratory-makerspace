package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.MembershipDTO;
import com.makerspace.backend.controller.dto.PaymentRecordDTO;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
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

    public String createCheckoutSession(Long userId, String planCode)  throws StripeException {
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

        SessionCreateParams sessionParams = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setMode(mode)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(lineItem)
                .build();

        Session session = stripeClient.v1().checkout().sessions().create(sessionParams);

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
