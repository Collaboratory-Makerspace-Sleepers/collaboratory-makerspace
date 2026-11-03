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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;

@Profile("dev")
@RestController
@RequestMapping("/api/dev/stripe")
public class DevStripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(DevStripeWebhookController.class);

    @Value("${stripe.webhook.secret}")
    String webhookSecret;

    @Autowired
    StripeEventService stripeEventService;

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(
            @RequestBody String payload, // needs to be exact raw bytes otherwise the signature check fails
            @RequestHeader("Stripe-Signature") String sigHeader) {

        System.out.println("\n\n🔔 ============ WEBHOOK ENDPOINT CALLED ============ 🔔");
        System.out.println("Payload length: " + payload.length());
        System.out.println("Signature header: " + sigHeader);
        System.out.println("Configured secret: " + webhookSecret);
        System.out.println("Payload preview: " + payload.substring(0, Math.min(100, payload.length())));
        System.out.println("===============================================\n\n");
        
        logger.info("=== WEBHOOK RECEIVED ===");
        logger.info("Payload length: {}", payload.length());
        logger.info("Signature header: {}", sigHeader);
        logger.info("Configured webhook secret: {}", webhookSecret);

        StripeEventCommand stripeEventCommand;
        Event event;
        try {
            logger.info("Attempting to construct event...");
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            
            logger.info("✅ Event constructed successfully");
            logger.info("Event ID: {}", event.getId());
            logger.info("Event Type: {}", event.getType());

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
            logger.error("❌ Signature verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
        } catch (Exception e) {
            logger.error("❌ Exception during webhook processing: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook processing failed");
        }

        logger.info("Handling event with StripeEventService...");
        EventOutcome outcome = stripeEventService.handle(stripeEventCommand);
        logger.info("Event outcome: {}", outcome);
        
        if (outcome == EventOutcome.DUPLICATE) {
            logger.warn("Duplicate event detected");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate Stripe Event");
        }
        
        logger.info("✅ Webhook processing complete");
    }

}
