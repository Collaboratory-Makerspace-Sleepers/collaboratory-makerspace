package com.makerspace.backend.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a single {@link StripeClient} bean backed by the secret key from config.
 *
 * The deprecated static {@code Stripe.apiKey} is intentionally NOT used — it is
 * global mutable state that breaks concurrent test suites and multi-tenant setups.
 */
@Configuration
public class StripeConfig {

    @Value("${stripe.secret.key}")
    private String secretKey;

    @Bean
    public StripeClient stripeClient() {
        return new StripeClient(secretKey);
    }
}
