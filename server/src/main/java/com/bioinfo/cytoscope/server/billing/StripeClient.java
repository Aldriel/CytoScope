package com.bioinfo.cytoscope.server.billing;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Narrow Stripe surface used by CytoScope billing: Checkout sessions and price metadata.
 */
public interface StripeClient {

    /**
     * Creates a subscription Checkout session.
     *
     * @param priceId Stripe price id
     * @param accessCode access code stored in session metadata
     * @param customerEmail optional email prefilled in Checkout
     * @param successUrl URL Stripe redirects to after payment
     * @param cancelUrl URL Stripe redirects to when the buyer cancels
     * @return the hosted Checkout URL
     * @throws IOException if Stripe cannot be reached
     */
    String createCheckoutSession(String priceId, String accessCode, String customerEmail,
                                 String successUrl, String cancelUrl) throws IOException;

    /**
     * Reads metadata of a price (plan, level, credits).
     *
     * @param priceId Stripe price id
     * @return metadata map, empty when the price is unknown
     * @throws IOException if Stripe cannot be reached
     */
    Map<String, String> priceMetadata(String priceId) throws IOException;

    /**
     * A client that refuses live calls; used when Stripe is not configured.
     *
     * @return the client
     */
    static StripeClient disabled() {
        return new StripeClient() {
            @Override
            public String createCheckoutSession(String priceId, String accessCode,
                                                String customerEmail, String successUrl,
                                                String cancelUrl) {
                throw new IllegalStateException("Stripe is not configured (STRIPE_SECRET_KEY)");
            }

            @Override
            public Map<String, String> priceMetadata(String priceId) {
                return Map.of();
            }
        };
    }

    /**
     * Builds a live HTTP client when {@code secretKey} is a test or live secret.
     *
     * @param secretKey Stripe secret key
     * @return the client, empty when the key is blank
     */
    static Optional<StripeClient> fromSecret(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new LiveStripeClient(secretKey.strip()));
    }
}
