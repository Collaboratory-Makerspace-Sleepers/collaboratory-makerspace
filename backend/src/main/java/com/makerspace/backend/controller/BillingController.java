package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.*;
import com.makerspace.backend.services.BillingService;
import org.springframework.security.access.prepost.PreAuthorize;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.makerspace.backend.controller.dto.PaymentMethodDTO;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    @Autowired private BillingService billingService;
    @Autowired private UserSecurity userSecurity;

    @GetMapping("/plans")
    public List<PlanDTO> listPlans() {
        return billingService.getPlans();
    }

    @PostMapping("/checkout-session")
    public CheckoutSessionResponse createCheckoutSession(
            @Valid @RequestBody CheckoutRequest req, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return new CheckoutSessionResponse(
                    billingService.createCheckoutSession(userId, req.planCode(), req.saveCard())
            );
        } catch (StripeException e) {
            log.error("Stripe error for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @PostMapping("/checkout-session/confirm-membership")
    public MembershipDTO confirmMembershipCheckout(
            @Valid @RequestBody CheckoutSessionConfirmationRequest req, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return billingService.confirmMembershipCheckout(userId, req.sessionId());
        } catch (StripeException e) {
            log.error("Stripe error confirming membership checkout for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }

    }

    @PostMapping("/checkout-session/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmCheckout(
            @Valid @RequestBody CheckoutSessionConfirmationRequest req, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            billingService.confirmCheckout(userId, req.sessionId());
        } catch (StripeException e) {
            log.error("Stripe error confirming checkout for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not verify checkout. Do not pay again; retry confirmation.");
        }
    }

    @PostMapping("/portal-session")
    public PortalSessionResponse createPortalSession(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return new PortalSessionResponse(
                    billingService.createPortalSession(userId)
            );
        } catch (StripeException e) {
            log.error("Stripe error for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMembership(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            billingService.cancelMembership(userId);
        } catch (StripeException e) {
            log.error("Stripe error cancelling membership for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @PostMapping("/payment-methods/setup")
    public CheckoutSessionResponse createSetupSession(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return new CheckoutSessionResponse(billingService.createSetupSession(userId));
        } catch (StripeException e) {
            log.error("Stripe error creating setup session for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @GetMapping("/payment-methods")
    public List<PaymentMethodDTO> listPaymentMethods(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return billingService.listPaymentMethods(userId);
        } catch (StripeException e) {
            log.error("Stripe error listing payment methods for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @DeleteMapping("/payment-methods/{pmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePaymentMethod(@PathVariable String pmId, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            billingService.deletePaymentMethod(userId, pmId);
        } catch (StripeException e) {
            log.error("Stripe error deleting payment method for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @PatchMapping("/payment-methods/{pmId}/set-default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefaultPaymentMethod(@PathVariable String pmId, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            billingService.setDefaultPaymentMethod(userId, pmId);
        } catch (StripeException e) {
            log.error("Stripe error setting default payment method for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @GetMapping("/me/subscription")
    public ResponseEntity<MembershipDTO> viewMembership(Authentication auth) {
        MembershipDTO membership = billingService.getSubscription(userSecurity.getUserId(auth));
        return membership == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(membership);
    }

    @PostMapping("/me/subscription/sync")
    public ResponseEntity<MembershipDTO> synchronizeMembership(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            MembershipDTO membership = billingService.synchronizeMembership(userId);
            return membership == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(membership);
        } catch (StripeException e) {
            log.error("Subscription synchronization failed for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not synchronize membership");
        }
    }

    @GetMapping("/me/payments")
    public List<PaymentRecordDTO> showPayments(Authentication auth) {
        return billingService.getPayments(userSecurity.getUserId(auth));
    }

    @PostMapping("/me/payments/sync")
    public List<PaymentRecordDTO> synchronizePayments(Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            billingService.synchronizePayments(userId);
            return billingService.getPayments(userId);
        } catch (StripeException e) {
            log.error("Stripe payment history sync failed for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not synchronize Stripe payments. Please retry.");
        }
    }

    @GetMapping("/me/subscription/plan-change-preview")
    public PlanChangePreviewDTO previewPlanChange(@RequestParam String planCode, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return billingService.previewPlanChange(userId, planCode);
        } catch (StripeException e) {
            log.error("Stripe error previewing plan change for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @PostMapping("/me/subscription/plan-change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePlan(@Valid @RequestBody PlanChangeRequest req, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            billingService.changePlan(userId, req.planCode());
        } catch (StripeException e) {
            log.error("Stripe error changing plan for user {}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    @GetMapping("/admin/users/{userId}/payments")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public List<PaymentRecordDTO> getAdminUserPayments(@PathVariable Long userId) {
        return billingService.getPayments(userId);
    }
}
