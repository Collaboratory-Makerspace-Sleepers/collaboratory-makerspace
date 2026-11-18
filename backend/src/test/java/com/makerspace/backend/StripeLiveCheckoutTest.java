package com.makerspace.backend;

import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.BillingService;
import com.stripe.StripeClient;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "stripe.live", matches = "true")
class StripeLiveCheckoutTest {

    @Test
    void createsClassCheckoutSessionWithEnvTestKey() throws Exception {
        String secretKey = readEnvValue("stripe.secret.key");
        assumeTrue(secretKey != null && secretKey.startsWith("sk_test_"),
                "backend/.env must contain a sk_test_ key");

        StripeClient client = new StripeClient(secretKey);
        Customer customer = client.v1().customers().create(CustomerCreateParams.builder()
                .setName("Stripe Live Test")
                .setEmail("stripe-live-test@example.com")
                .build());
        try {
            UserProfile profile = new UserProfile();
            profile.setFirstName("Stripe");
            profile.setLastName("Test");
            User user = new User();
            user.setId(1L);
            user.setEmail("stripe-live-test@example.com");
            user.setProfile(profile);
            user.setStripeCustomerId(customer.getId());

            UserRepository userRepository = mock(UserRepository.class);
            when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

            BillingService billingService = new BillingService();
            ReflectionTestUtils.setField(billingService, "stripeClient", client);
            ReflectionTestUtils.setField(billingService, "userRepository", userRepository);
            ReflectionTestUtils.setField(billingService, "successUrl", "http://localhost:5173/billing/success");
            ReflectionTestUtils.setField(billingService, "cancelUrl", "http://localhost:5173/billing/cancel");
            ReflectionTestUtils.setField(billingService, "returnUrl", "http://localhost:5173/account");

            List<Integer> classPrices = List.of(1500, 2500, 4900);
            int priceCents = classPrices.get(ThreadLocalRandom.current().nextInt(classPrices.size()));
            String title = "Live Stripe Class " + UUID.randomUUID();

            String sessionUrl = billingService.createClassCheckoutSession(1L, 42L, priceCents, title);

            assertThat(sessionUrl).startsWith("https://checkout.stripe.com/");
        } finally {
            client.v1().customers().delete(customer.getId());
        }
    }

    private static String readEnvValue(String key) throws Exception {
        for (Path candidate : List.of(Path.of(".env"), Path.of("../backend/.env"))) {
            if (!Files.exists(candidate)) {
                continue;
            }
            for (String line : Files.readAllLines(candidate)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key + "=")) {
                    return trimmed.substring(key.length() + 1).trim();
                }
            }
        }
        return null;
    }
}
