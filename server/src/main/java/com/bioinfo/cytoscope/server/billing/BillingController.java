package com.bioinfo.cytoscope.server.billing;

import com.bioinfo.cytoscope.server.HttpError;
import com.bioinfo.cytoscope.server.JsonHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Billing routes:
 *
 * <ul>
 *   <li>{@code POST /api/billing/checkout}: starts Stripe Checkout and mints an access code;
 *   <li>{@code GET /api/billing/accounts/{code}}: returns the credit balance;
 *   <li>{@code POST /api/billing/stripe/webhook}: applies Stripe events.
 * </ul>
 */
public final class BillingController extends JsonHandler {

    private final BillingService billing;

    /**
     * Creates the controller.
     *
     * @param billing the billing service
     */
    public BillingController(BillingService billing) {
        super("/api/billing");
        this.billing = Objects.requireNonNull(billing, "billing");
    }

    /** Body of {@code POST /api/billing/checkout}. */
    public record CheckoutRequest(String priceId, String email, String successUrl,
                                  String cancelUrl) {}

    @Override
    protected Object handle(Request request) throws IOException {
        List<String> path = request.segments();
        if (path.isEmpty()) {
            throw notFound(request);
        }
        return switch (path.get(0)) {
            case "checkout" -> {
                requireMethod(request, "POST");
                requireRoute(request, path, 1, "checkout", "POST");
                yield checkout(request.json(CheckoutRequest.class));
            }
            case "accounts" -> {
                requireMethod(request, "GET");
                if (path.size() != 2) {
                    throw notFound(request);
                }
                yield accountView(billing.balance(path.get(1)));
            }
            case "stripe" -> {
                requireRoute(request, path, 2, "webhook", "POST");
                byte[] raw = request.content().readNBytes(2 * 1024 * 1024 + 1);
                if (raw.length > 2 * 1024 * 1024) {
                    throw new HttpError(413, "Webhook body too large");
                }
                yield billing.handleStripeEvent(new String(raw, StandardCharsets.UTF_8));
            }
            default -> throw notFound(request);
        };
    }

    private Map<String, Object> checkout(CheckoutRequest body) throws IOException {
        BillingService.CheckoutStart started = billing.startCheckout(body.priceId(), body.email(),
                body.successUrl(), body.cancelUrl());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessCode", started.accessCode());
        response.put("checkoutUrl", started.checkoutUrl());
        return response;
    }

    private static Map<String, Object> accountView(AccessAccount account) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessCode", account.accessCode());
        response.put("email", account.email());
        response.put("credits", account.credits());
        response.put("allowance", account.allowance());
        response.put("plan", account.plan());
        response.put("level", account.level());
        response.put("active", account.active());
        return response;
    }

    /**
     * Ensures the path has the expected last segment and method.
     *
     * @param request current request
     * @param path path segments
     * @param size expected segment count
     * @param last expected last segment
     * @param method expected method
     */
    private static void requireRoute(Request request, List<String> path, int size, String last,
                                     String method) {
        requireMethod(request, method);
        if (path.size() != size || !path.get(size - 1).equals(last)) {
            throw notFound(request);
        }
    }
}
