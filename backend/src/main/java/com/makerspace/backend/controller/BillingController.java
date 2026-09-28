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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    @Autowired private BillingService billingService;
    @Autowired private UserSecurity userSecurity;

    @PostMapping("/checkout-session")
    public CheckoutSessionResponse createCheckoutSession(
            @Valid @RequestBody CheckoutRequest req, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        try {
            return new CheckoutSessionResponse(
                    billingService.createCheckoutSession(userId, req.planCode())
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

    @GetMapping("/me/subscription")
    public MembershipDTO viewMembership(Authentication auth) {
        return billingService.getSubscription(userSecurity.getUserId(auth));
    }

    @GetMapping("/me/payments")
    public List<PaymentRecordDTO> showPayments(Authentication auth) {
        return billingService.getPayments(userSecurity.getUserId(auth));
    }
}
