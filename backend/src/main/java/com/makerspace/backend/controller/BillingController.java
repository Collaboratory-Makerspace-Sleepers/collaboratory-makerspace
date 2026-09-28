package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.*;
import com.makerspace.backend.services.BillingService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public MembershipDTO viewMembership(Authentication auth) {
        return billingService.getSubscription(userSecurity.getUserId(auth));
    }

    @GetMapping("/me/payments")
    public List<PaymentRecordDTO> showPayments(Authentication auth) {
        return billingService.getPayments(userSecurity.getUserId(auth));
    }
}
