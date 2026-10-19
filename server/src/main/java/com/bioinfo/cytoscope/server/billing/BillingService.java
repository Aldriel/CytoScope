package com.bioinfo.cytoscope.server.billing;

import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.server.HttpError;
import com.bioinfo.cytoscope.server.Json;
import com.bioinfo.cytoscope.server.workspace.Workspace;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Credits and Stripe Checkout for CytoScope: free trial of
 * {@link CreditMeter#FREE_TRIAL_FILES} own files, then an access code with a monthly balance.
 */
public final class BillingService {

    private final CreditBook book;
    private final StripeClient stripe;
    private final ConcurrentHashMap<String, AtomicInteger> trialUploads =
            new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param book ledger of access codes
     * @param stripe Stripe client; may be {@link StripeClient#disabled()}
     */
    public BillingService(CreditBook book, StripeClient stripe) {
        this.book = Objects.requireNonNull(book, "book");
        this.stripe = Objects.requireNonNull(stripe, "stripe");
    }

    /**
     * Builds a service from environment variables ({@code STRIPE_SECRET_KEY}).
     *
     * @return the service
     */
    public static BillingService fromEnvironment() {
        StripeClient client = StripeClient.fromSecret(System.getenv("STRIPE_SECRET_KEY"))
                .orElseGet(StripeClient::disabled);
        return new BillingService(new CreditBook(), client);
    }

    /**
     * Returns the credit book.
     *
     * @return the book
     */
    public CreditBook book() {
        return book;
    }

    /**
     * Starts Checkout for a Stripe price and returns the URL plus a new access code.
     *
     * @param priceId Stripe price id
     * @param email optional buyer email
     * @param successUrl success redirect
     * @param cancelUrl cancel redirect
     * @return access code and Checkout URL
     * @throws IOException if Stripe fails
     */
    public CheckoutStart startCheckout(String priceId, String email, String successUrl,
                                       String cancelUrl) throws IOException {
        requireNonBlank(priceId, "priceId");
        requireNonBlank(successUrl, "successUrl");
        requireNonBlank(cancelUrl, "cancelUrl");
        AccessAccount pending = book.createPending(email == null ? "" : email);
        String url = stripe.createCheckoutSession(priceId, pending.accessCode(), email,
                successUrl, cancelUrl);
        return new CheckoutStart(pending.accessCode(), url);
    }

    /**
     * Returns the balance for an access code.
     *
     * @param accessCode the code
     * @return the account
     * @throws HttpError 404 when unknown
     */
    public AccessAccount balance(String accessCode) {
        return book.find(accessCode)
                .orElseThrow(() -> new HttpError(404, "Unknown access code"));
    }

    /**
     * Applies a Stripe webhook JSON payload (Checkout completed or subscription deleted).
     *
     * @param payload webhook body
     * @return a short status for the HTTP response
     * @throws IOException if price metadata cannot be loaded
     */
    public Map<String, Object> handleStripeEvent(String payload) throws IOException {
        JsonNode root = Json.mapper().readTree(payload);
        String type = root.path("type").asText("");
        JsonNode object = root.path("data").path("object");
        return switch (type) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            case "customer.subscription.deleted" -> onSubscriptionDeleted(object);
            case "invoice.paid" -> onInvoicePaid(object);
            default -> Map.of("ignored", type.isEmpty() ? "unknown" : type);
        };
    }

    /**
     * Checks the free trial or debits credits before an FCS file is kept in a workspace.
     *
     * @param workspace the workspace
     * @param sample the file about to be stored
     * @param accessCode optional access code from the client
     * @throws HttpError 402 when the trial is spent and credits are missing
     */
    public void authorizeUpload(Workspace workspace, FcsSample sample, String accessCode) {
        if (workspace.example().isPresent()) {
            return;
        }
        int cost = CreditMeter.creditsFor(sample.eventCount());
        if (accessCode != null && !accessCode.isBlank()) {
            book.debit(accessCode, cost);
            return;
        }
        AtomicInteger used = trialUploads.computeIfAbsent(workspace.id(),
                id -> new AtomicInteger());
        int next = used.incrementAndGet();
        if (next > CreditMeter.FREE_TRIAL_FILES) {
            used.decrementAndGet();
            throw new HttpError(402, "Free trial is five files; subscribe for an access code");
        }
    }

    private Map<String, Object> onCheckoutCompleted(JsonNode session) throws IOException {
        String accessCode = firstNonBlank(
                session.path("client_reference_id").asText(""),
                session.path("metadata").path("access_code").asText(""));
        String email = session.path("customer_details").path("email").asText(
                session.path("customer_email").asText(""));
        String customerId = session.path("customer").asText("");
        String subscriptionId = session.path("subscription").asText("");
        String priceId = session.path("metadata").path("price_id").asText("");
        if (priceId.isEmpty()) {
            // Checkout line items are not always expanded; clients put price in metadata too.
            priceId = session.path("metadata").path("priceId").asText("");
        }
        int allowance = 0;
        String plan = "";
        String level = "";
        if (!priceId.isEmpty()) {
            Map<String, String> meta = stripe.priceMetadata(priceId);
            allowance = parseInt(meta.get("credits_monthly"), 0);
            plan = meta.getOrDefault("plan", "");
            level = meta.getOrDefault("level", "");
        }
        if (allowance <= 0) {
            allowance = parseInt(session.path("metadata").path("credits_monthly").asText(""), 0);
            plan = firstNonBlank(plan, session.path("metadata").path("plan").asText(""));
            level = firstNonBlank(level, session.path("metadata").path("level").asText(""));
        }
        if (allowance <= 0) {
            throw new HttpError(400, "Checkout session has no credits_monthly metadata");
        }
        AccessAccount granted = book.grant(accessCode.isEmpty() ? null : accessCode, email,
                allowance, plan, level, customerId, subscriptionId);
        return Map.of(
                "granted", true,
                "accessCode", granted.accessCode(),
                "credits", granted.credits(),
                "allowance", granted.allowance());
    }

    private Map<String, Object> onInvoicePaid(JsonNode invoice) throws IOException {
        String subscriptionId = invoice.path("subscription").asText("");
        String customerId = invoice.path("customer").asText("");
        String email = invoice.path("customer_email").asText("");
        JsonNode line = invoice.path("lines").path("data");
        if (!line.isArray() || line.isEmpty()) {
            return Map.of("ignored", "invoice.paid-without-lines");
        }
        JsonNode price = line.get(0).path("price");
        String priceId = price.path("id").asText("");
        Map<String, String> meta = price.path("metadata").isObject()
                ? flatten(price.path("metadata"))
                : (!priceId.isEmpty() ? stripe.priceMetadata(priceId) : Map.of());
        int allowance = parseInt(meta.get("credits_monthly"), 0);
        if (allowance <= 0) {
            return Map.of("ignored", "invoice.paid-without-credits");
        }
        String accessCode = firstNonBlank(meta.get("access_code"),
                invoice.path("metadata").path("access_code").asText(""));
        AccessAccount existing = subscriptionId.isEmpty() ? null
                : book.findBySubscription(subscriptionId).orElse(null);
        String code = existing != null ? existing.accessCode()
                : (accessCode.isEmpty() ? null : accessCode);
        AccessAccount granted = book.grant(code,
                firstNonBlank(email, existing == null ? "" : existing.email()),
                allowance,
                meta.getOrDefault("plan", existing == null ? "" : existing.plan()),
                meta.getOrDefault("level", existing == null ? "" : existing.level()),
                firstNonBlank(customerId, existing == null ? "" : existing.stripeCustomerId()),
                firstNonBlank(subscriptionId,
                        existing == null ? "" : existing.stripeSubscriptionId()));
        return Map.of("renewed", true, "accessCode", granted.accessCode(),
                "credits", granted.credits());
    }

    private Map<String, Object> onSubscriptionDeleted(JsonNode subscription) {
        String subscriptionId = subscription.path("id").asText("");
        return book.deactivateSubscription(subscriptionId)
                .<Map<String, Object>>map(a -> Map.of("deactivated", true,
                        "accessCode", a.accessCode()))
                .orElseGet(() -> Map.of("ignored", "unknown-subscription"));
    }

    private static Map<String, String> flatten(JsonNode metadata) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        metadata.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText("")));
        return out;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        return b == null ? "" : b.strip();
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /**
     * Result of starting Checkout.
     *
     * @param accessCode code to show the buyer
     * @param checkoutUrl Stripe-hosted Checkout URL
     */
    public record CheckoutStart(String accessCode, String checkoutUrl) {}
}
