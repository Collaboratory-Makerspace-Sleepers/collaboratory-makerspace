package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.MembershipDTO;
import com.makerspace.backend.controller.dto.PaymentMethodDTO;
import com.makerspace.backend.controller.dto.PaymentRecordDTO;
import com.makerspace.backend.controller.dto.PlanChangePreviewDTO;
import com.makerspace.backend.controller.dto.PlanDTO;
import com.makerspace.backend.controller.dto.SubscriptionSnapshot;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.UserRepository;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoiceCreatePreviewParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

    @Autowired
    PaymentHistoryService paymentHistoryService;

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

        // Block if the user already has an active subscription-based membership.
        if (plan.get().getBillingInterval() != null && membershipService.hasActiveMembership(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an active membership");
        }

        Optional<User> user = userRepository.findByIdForUpdate(userId);

        if (user.isEmpty()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        // Student plan requires a .edu email address.
        if ("STUDENT".equals(planCode) && !user.get().getEmail().endsWith(".edu")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The student plan requires a .edu email address");
        }

        UserProfile userProfile = user.get().getProfile();
        String stripeCustomerId;

        if (user.get().getStripeCustomerId() == null) {
            stripeCustomerId = createStripeCustomer(user.get(), userProfile, userId);
        } else {
            // Verify the stored customer still exists (e.g. test-clock customers can be deleted).
            stripeCustomerId = verifyOrRecreateCustomer(user.get(), userProfile, userId);
        }

        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1L)
                .build();

        SessionCreateParams.Mode mode = (plan.get().getBillingInterval() == null)
                ? SessionCreateParams.Mode.PAYMENT
                : SessionCreateParams.Mode.SUBSCRIPTION;

        String typedSuccessUrl = successUrl + (mode == SessionCreateParams.Mode.SUBSCRIPTION
                ? "?type=membership&session_id={CHECKOUT_SESSION_ID}"
                : "?type=daypass&session_id={CHECKOUT_SESSION_ID}");

        SessionCreateParams.Builder sessionBuilder = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setMode(mode)
                .setSuccessUrl(typedSuccessUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("kind", mode == SessionCreateParams.Mode.SUBSCRIPTION ? "MEMBERSHIP" : "DAY_PASS")
                .putMetadata("saveCard", Boolean.toString(saveCard))
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

    public void confirmCheckout(Long userId, String sessionId) throws StripeException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout session is required");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Session session = stripeClient.v1().checkout().sessions().retrieve(sessionId);
        if (user.getStripeCustomerId() == null
                || !user.getStripeCustomerId().equals(session.getCustomer())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Checkout belongs to another account");
        }
        if (!"complete".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Checkout is not complete");
        }
        switch (session.getMode()) {
            case "payment" -> paymentHistoryService.confirmCheckout(userId, sessionId);
            case "subscription" -> {
                confirmMembershipCheckout(userId, sessionId);
                paymentHistoryService.synchronize(userId);
            }
            case "setup" -> {
                if (session.getSetupIntent() == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Card setup is not available");
                }
                var setup = stripeClient.v1().setupIntents().retrieve(session.getSetupIntent());
                if (!user.getStripeCustomerId().equals(setup.getCustomer())
                        || !"succeeded".equals(setup.getStatus()) || setup.getPaymentMethod() == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Card setup could not be verified");
                }
                setDefaultPaymentMethod(userId, setup.getPaymentMethod());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported checkout mode");
        }
        if ("payment".equals(session.getMode()) && session.getMetadata() != null
                && "true".equals(session.getMetadata().get("saveCard"))) {
            var intent = stripeClient.v1().paymentIntents().retrieve(session.getPaymentIntent());
            if (!user.getStripeCustomerId().equals(intent.getCustomer()) || intent.getPaymentMethod() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Saved card could not be verified");
            }
            setDefaultPaymentMethod(userId, intent.getPaymentMethod());
        }
    }

    public MembershipDTO confirmMembershipCheckout(Long userId, String stripeSessionId)
            throws StripeException {
        if (membershipService.hasActiveMembership(userId)) {
            return getSubscription(userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStripeCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "An active membership or paid day pass is required");
        }

        String subscriptionId;
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            SubscriptionListParams params = SubscriptionListParams.builder()
                    .setCustomer(user.getStripeCustomerId())
                    .setStatus(SubscriptionListParams.Status.ACTIVE)
                    .build();
            Subscription subscription = stripeClient.v1().subscriptions().list(params).getData().stream()
                    .max(java.util.Comparator.comparing(s -> Optional.ofNullable(s.getCreated()).orElse(0L)))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                            "No active membership was found for this account"));
            subscriptionId = subscription.getId();
        } else {
            Session session = stripeClient.v1().checkout().sessions().retrieve(stripeSessionId);
            if (!"subscription".equals(session.getMode())
                    || !"complete".equals(session.getStatus())
                    || (!"paid".equals(session.getPaymentStatus())
                        && !"no_payment_required".equals(session.getPaymentStatus()))) {
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                        "Membership payment has not been completed");
            }
            if (!user.getStripeCustomerId().equals(session.getCustomer())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Checkout session does not belong to this account");
            }
            subscriptionId = session.getSubscription();
            if (subscriptionId == null || subscriptionId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Checkout session has no subscription");
            }
        }

        Subscription subscription = stripeClient.v1().subscriptions().retrieve(subscriptionId);
        synchronizeSubscription(user, subscription);
        if (!membershipService.hasActiveMembership(userId)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Membership subscription is not active");
        }
        return getSubscription(userId);
    }

    public MembershipDTO synchronizeMembership(Long userId) throws StripeException {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getStripeCustomerId() != null) {
            var subscriptions = stripeClient.v1().subscriptions().list(SubscriptionListParams.builder()
                    .setCustomer(user.getStripeCustomerId()).setStatus(SubscriptionListParams.Status.ALL)
                    .setLimit(100L).build());
            Subscription latest = null;
            for (Subscription subscription : subscriptions.autoPagingIterable()) {
                boolean eligible = List.of("active", "trialing", "past_due").contains(subscription.getStatus());
                boolean previousEligible = latest != null
                        && List.of("active", "trialing", "past_due").contains(latest.getStatus());
                if (latest == null || (eligible && !previousEligible)
                        || (eligible == previousEligible && subscription.getCreated() > latest.getCreated())) {
                    latest = subscription;
                }
            }
            if (latest != null) synchronizeSubscription(user, latest);
            else if (membershipService.currentMembership(userId).isEmpty()) {
                paymentHistoryService.synchronize(userId);
            }
        }
        return getSubscription(userId);
    }

    private void synchronizeSubscription(User user, Subscription subscription) {
        if (!user.getStripeCustomerId().equals(subscription.getCustomer())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Subscription does not belong to this account");
        }

        List<com.stripe.model.SubscriptionItem> items = subscription.getItems().getData();
        if (items.isEmpty() || items.get(0).getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subscription does not contain a membership plan");
        }

        String stripeStatus = subscription.getStatus();
        MembershipStatus status = switch (stripeStatus) {
            case "active" -> MembershipStatus.ACTIVE;
            case "trialing" -> MembershipStatus.TRIALING;
            case "past_due" -> MembershipStatus.PAST_DUE;
            case "canceled", "unpaid" -> MembershipStatus.GRACE;
            case "incomplete" -> MembershipStatus.INCOMPLETE;
            case "incomplete_expired" -> MembershipStatus.INCOMPLETE_EXPIRED;
            case "paused" -> MembershipStatus.PAUSED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Unrecognized subscription status");
        };
        var item = items.get(0);
        ZonedDateTime periodStart = item.getCurrentPeriodStart() == null ? null
                : Instant.ofEpochSecond(item.getCurrentPeriodStart()).atZone(ZoneOffset.UTC);
        ZonedDateTime periodEnd = item.getCurrentPeriodEnd() == null ? null
                : Instant.ofEpochSecond(item.getCurrentPeriodEnd()).atZone(ZoneOffset.UTC);
        if (periodEnd == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subscription billing period is not available yet");
        }

        Long canceledAtEpoch = subscription.getCanceledAt();
        ZonedDateTime canceledAt = canceledAtEpoch == null ? null
                : Instant.ofEpochSecond(canceledAtEpoch).atZone(ZoneOffset.UTC);
        Long createdEpoch = subscription.getCreated();
        ZonedDateTime updatedAt = createdEpoch == null ? ZonedDateTime.now(ZoneOffset.UTC)
                : Instant.ofEpochSecond(createdEpoch).atZone(ZoneOffset.UTC);
        // A fresh Stripe read must not be rejected when the SDK omits the resource update timestamp.
        ZonedDateTime savedTimestamp = membershipService.currentMembership(user.getId())
                .map(Membership::getStripeUpdatedAt).orElse(null);
        if (savedTimestamp != null && savedTimestamp.isAfter(updatedAt)) updatedAt = savedTimestamp;

        membershipService.upsertFromSubscription(new SubscriptionSnapshot(
                user,
                subscription.getId(),
                item.getPrice().getId(),
                status,
                periodStart,
                periodEnd,
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                canceledAt,
                updatedAt
        ));

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
        Subscription updated = stripeClient.v1().subscriptions().update(subscriptionId, params);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        synchronizeSubscription(user, updated);
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
                .setSuccessUrl(successUrl + "?type=setup&session_id={CHECKOUT_SESSION_ID}")
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
        Membership membership = membershipService.currentMembership(userId).orElse(null);
        if (membership == null || !membershipService.hasActiveMembership(userId)) {
            var dayPass = paymentRecordRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
                    .filter(payment -> payment.getKind() == PaymentKind.DAY_PASS
                            && payment.getStatus() == PaymentStatus.SUCCEEDED
                            && payment.getOccurredAt().plusHours(24).isAfter(ZonedDateTime.now()))
                    .findFirst();
            if (dayPass.isPresent()) {
                PaymentRecord payment = dayPass.get();
                return new MembershipDTO(MembershipStatus.ACTIVE, "DAY_PASS", "Day Pass",
                        payment.getAmountCents(), null, payment.getOccurredAt(),
                        payment.getOccurredAt().plusHours(24), false, null,
                        payment.getOccurredAt(), null);
            }
        }
        if (membership == null) return null;

        MembershipPlan plan = membership.getPlan();
        return new MembershipDTO(
                membership.getStatus(),
                plan.getCode(),
                plan.getDisplayName(),
                plan.getAmountCents(),
                plan.getBillingInterval(),
                membership.getCurrentPeriodStart(),
                membership.getCurrentPeriodEnd(),
                membership.isCancelAtPeriodEnd(),
                membership.getGracePeriodEndsAt(),
                membership.getCreatedAt(),
                membership.getStripeSubscriptionId()
        );
    }

    public PlanChangePreviewDTO previewPlanChange(Long userId, String planCode) throws StripeException {
        MembershipPlan targetPlan = membershipPlanRepository.findByCode(planCode)
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));

        Membership membership = membershipService.currentMembership(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active membership found"));

        MembershipPlan currentPlan = membership.getPlan();
        validatePlanChangeGuards(currentPlan, planCode, membership);
        validateTargetPlan(userId, targetPlan, membership);

        boolean isUpgrade = targetPlan.getAmountCents() > currentPlan.getAmountCents();
        String subscriptionId = membership.getStripeSubscriptionId();

        Subscription subscription = stripeClient.v1().subscriptions().retrieve(subscriptionId);
        String itemId = subscription.getItems().getData().get(0).getId();

        if (isUpgrade) {
            InvoiceCreatePreviewParams params = InvoiceCreatePreviewParams.builder()
                    .setSubscription(subscriptionId)
                    .setSubscriptionDetails(
                            InvoiceCreatePreviewParams.SubscriptionDetails.builder()
                                    .setProrationBehavior(InvoiceCreatePreviewParams.SubscriptionDetails.ProrationBehavior.ALWAYS_INVOICE)
                                    .addItem(InvoiceCreatePreviewParams.SubscriptionDetails.Item.builder()
                                            .setId(itemId)
                                            .setPrice(targetPlan.getStripePriceId())
                                            .build())
                                    .build())
                    .build();
            Invoice upcoming = stripeClient.v1().invoices().createPreview(params);
            return new PlanChangePreviewDTO("UPGRADE", upcoming.getAmountDue().intValue(),
                    upcoming.getCurrency(), null);
        } else {
            return new PlanChangePreviewDTO("DOWNGRADE", 0,
                    currentPlan.getCurrency(), ZonedDateTime.now());
        }
    }

    public void changePlan(Long userId, String planCode) throws StripeException {
        MembershipPlan targetPlan = membershipPlanRepository.findByCode(planCode)
                .filter(MembershipPlan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));

        Membership membership = membershipService.currentMembership(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active membership found"));

        MembershipPlan currentPlan = membership.getPlan();
        validatePlanChangeGuards(currentPlan, planCode, membership);
        validateTargetPlan(userId, targetPlan, membership);

        boolean isUpgrade = targetPlan.getAmountCents() > currentPlan.getAmountCents();
        String subscriptionId = membership.getStripeSubscriptionId();

        Subscription subscription = stripeClient.v1().subscriptions().retrieve(subscriptionId);
        String itemId = subscription.getItems().getData().get(0).getId();

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setProrationBehavior(isUpgrade
                        ? SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE
                        : SubscriptionUpdateParams.ProrationBehavior.NONE)
                .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                .addItem(SubscriptionUpdateParams.Item.builder()
                        .setId(itemId)
                        .setPrice(targetPlan.getStripePriceId())
                        .build())
                .build();

        Subscription updated = stripeClient.v1().subscriptions().update(subscriptionId, params);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        synchronizeSubscription(user, updated);
    }

    private void validateTargetPlan(Long userId, MembershipPlan target, Membership current) {
        if (target.getBillingInterval() == null
                || !target.getBillingInterval().equals(current.getPlan().getBillingInterval())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Choose a recurring plan with the same billing interval");
        }
        if (!membershipService.hasActiveMembership(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active subscription to change");
        }
        if ("STUDENT".equals(target.getCode())) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            if (!user.getEmail().toLowerCase(java.util.Locale.ROOT).endsWith(".edu")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The student plan requires a .edu email");
            }
        }
    }

    private void validatePlanChangeGuards(MembershipPlan currentPlan, String targetPlanCode,
            Membership membership) {
        if (currentPlan.getCode().equals(targetPlanCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already on this plan");
        }
        if (membership.getStripeSubscriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No Stripe subscription linked to this membership");
        }
        if (membership.isCancelAtPeriodEnd()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot change plan while cancellation is pending");
        }
    }

    public List<PlanDTO> getPlans() {
        return membershipPlanRepository.findByActiveTrue()
                .stream()
                .sorted(java.util.Comparator.comparingInt(MembershipPlan::getAmountCents))
                .map(p -> new PlanDTO(p.getCode(), p.getDisplayName(), p.getAmountCents(), p.getBillingInterval()))
                .toList();
    }

    private String createStripeCustomer(User user, UserProfile profile, Long userId) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(profile.getFirstName() + " " + profile.getLastName())
                .setEmail(user.getEmail())
                .build();
        // Include the parameters in the key: Stripe rejects a reused key with different params.
        int paramsHash = java.util.Objects.hash(params.getName(), params.getEmail());
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("cust-create-" + userId + "-" + Integer.toHexString(paramsHash))
                .build();
        Customer customer = stripeClient.v1().customers().create(params, options);
        user.setStripeCustomerId(customer.getId());
        return customer.getId();
    }

    private String verifyOrRecreateCustomer(User user, UserProfile profile, Long userId) throws StripeException {
        try {
            stripeClient.v1().customers().retrieve(user.getStripeCustomerId());
            return user.getStripeCustomerId();
        } catch (InvalidRequestException e) {
            if ("resource_missing".equals(e.getCode())) {
                log.warn("Stripe customer {} no longer exists for user {}, creating new one",
                        user.getStripeCustomerId(), userId);
                user.setStripeCustomerId(null);
                // Idempotency key must differ from the original create to avoid replaying
                // the old (now-deleted) customer ID from Stripe's idempotency cache.
                CustomerCreateParams params = CustomerCreateParams.builder()
                        .setName(profile.getFirstName() + " " + profile.getLastName())
                        .setEmail(user.getEmail())
                        .build();
                Customer customer = stripeClient.v1().customers().create(params);
                user.setStripeCustomerId(customer.getId());
                return customer.getId();
            }
            throw e;
        }
    }

    public String createClassCheckoutSession(Long userId, Long enrollmentId, int priceCents, String title)
            throws StripeException {
        Optional<User> userOpt = userRepository.findByIdForUpdate(userId);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        User user = userOpt.get();
        UserProfile profile = user.getProfile();

        String stripeCustomerId;
        if (user.getStripeCustomerId() == null) {
            stripeCustomerId = createStripeCustomer(user, profile, userId);
        } else {
            stripeCustomerId = verifyOrRecreateCustomer(user, profile, userId);
        }

        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency("usd")
                        .setUnitAmount((long) priceCents)
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(title)
                                .build())
                        .build())
                .build();

        String typedSuccessUrl = successUrl + "?type=class&enrollmentId=" + enrollmentId
                + "&session_id={CHECKOUT_SESSION_ID}";

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(typedSuccessUrl)
                .setCancelUrl(cancelUrl)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(lineItem)
                .putMetadata("kind", "CLASS")
                .build();

        Session session = stripeClient.v1().checkout().sessions().create(params);
        return session.getUrl();
    }

    public void verifyClassPayment(String stripeSessionId) throws StripeException {
        Session session = stripeClient.v1().checkout().sessions().retrieve(stripeSessionId);
        if (!"paid".equals(session.getPaymentStatus()) && !"no_payment_required".equals(session.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Payment not confirmed for session: " + stripeSessionId);
        }
    }

    public List<PaymentRecordDTO> getPayments(Long userId) {
        return paymentRecordRepository.findByUserIdOrderByOccurredAtDesc(userId)
                .stream()
                .map(pr -> {
                    String piId = pr.getStripePaymentIntentId() != null
                            ? pr.getStripePaymentIntentId() : pr.getStripeInvoiceId();
                    String referenceId = piId != null
                            ? piId.substring(Math.max(0, piId.length() - 8))
                            : null;
                    return new PaymentRecordDTO(
                            pr.getKind(),
                            pr.getStatus(),
                            pr.getAmountCents(),
                            pr.getCurrency(),
                            pr.getDescription(),
                            pr.getOccurredAt(),
                            pr.getReceiptUrl(),
                            referenceId
                    );
                })
                .toList();
    }

    public void synchronizePayments(Long userId) throws StripeException {
        paymentHistoryService.synchronize(userId);
    }
}
