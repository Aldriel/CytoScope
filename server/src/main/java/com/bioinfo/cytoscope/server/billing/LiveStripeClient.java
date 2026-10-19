package com.bioinfo.cytoscope.server.billing;

import com.bioinfo.cytoscope.server.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** Stripe API over the JDK HTTP client (Checkout + Prices). */
final class LiveStripeClient implements StripeClient {

    private static final URI API = URI.create("https://api.stripe.com/v1/");

    private final String secretKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    LiveStripeClient(String secretKey) {
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey");
    }

    @Override
    public String createCheckoutSession(String priceId, String accessCode, String customerEmail,
                                        String successUrl, String cancelUrl) throws IOException {
        Map<String, String> priceMeta = priceMetadata(priceId);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "subscription");
        form.put("success_url", successUrl);
        form.put("cancel_url", cancelUrl);
        form.put("line_items[0][price]", priceId);
        form.put("line_items[0][quantity]", "1");
        form.put("client_reference_id", accessCode);
        form.put("metadata[access_code]", accessCode);
        form.put("metadata[price_id]", priceId);
        form.put("metadata[plan]", priceMeta.getOrDefault("plan", ""));
        form.put("metadata[level]", priceMeta.getOrDefault("level", ""));
        form.put("metadata[credits_monthly]", priceMeta.getOrDefault("credits_monthly", ""));
        form.put("subscription_data[metadata][access_code]", accessCode);
        form.put("subscription_data[metadata][plan]", priceMeta.getOrDefault("plan", ""));
        form.put("subscription_data[metadata][level]", priceMeta.getOrDefault("level", ""));
        form.put("subscription_data[metadata][credits_monthly]",
                priceMeta.getOrDefault("credits_monthly", ""));
        if (customerEmail != null && !customerEmail.isBlank()) {
            form.put("customer_email", customerEmail.strip());
        }
        JsonNode session = post("checkout/sessions", form);
        String url = text(session, "url");
        if (url.isEmpty()) {
            throw new IOException("Stripe Checkout session has no url");
        }
        return url;
    }

    @Override
    public Map<String, String> priceMetadata(String priceId) throws IOException {
        JsonNode price = get("prices/" + encode(priceId));
        JsonNode metadata = price.get("metadata");
        if (metadata == null || !metadata.isObject()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        metadata.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText("")));
        return Map.copyOf(out);
    }

    private JsonNode get(String path) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(API.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + secretKey)
                .GET().build();
        return send(request);
    }

    private JsonNode post(String path, Map<String, String> form) throws IOException {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(encode(k) + "=" + encode(v)));
        HttpRequest request = HttpRequest.newBuilder(API.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = Json.mapper().readTree(response.body());
            if (response.statusCode() >= 400) {
                String message = node.path("error").path("message").asText("Stripe error "
                        + response.statusCode());
                throw new IOException(message);
            }
            return node;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Stripe call interrupted", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
