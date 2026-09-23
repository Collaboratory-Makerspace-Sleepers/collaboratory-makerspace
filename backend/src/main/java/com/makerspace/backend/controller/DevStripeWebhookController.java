package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.StripeEventCommand;
import com.makerspace.backend.model.EventOutcome;
import com.makerspace.backend.services.StripeEventService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;

@Profile("dev")
@RestController
@RequestMapping("/api/dev/stripe")
public class DevStripeWebhookController {

    @Value("${stripe.webhook.secret}")
    String webhookSecret;

    @Autowired
    StripeEventService stripeEventService;

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(
            @RequestBody String payload, // needs to be exact raw bytes otherwise the signature check fails
            @RequestHeader("Stripe-Signature") String sigHeader) {

        StripeEventCommand stripeEventCommand;
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            stripeEventCommand = new StripeEventCommand(
                event.getId(),
                event.getType(),
                event.getApiVersion(),
                event.getLivemode(),
                ZonedDateTime.now(),
                "WEBHOOK_DEV",
                payload
            );

        } catch (SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
        }

        EventOutcome outcome = stripeEventService.handle(stripeEventCommand);
        if (outcome.equals(EventOutcome.DUPLICATE)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate Stripe Event");
        }
    }

}
