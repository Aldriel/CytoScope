package com.bioinfo.cytoscope.server.billing;

import com.bioinfo.cytoscope.server.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Maps Metabolix plan / level / period / tier to the Stripe price ids created for CAD.
 */
public final class PriceCatalog {

    private final JsonNode prices;

    /**
     * Loads the bundled CAD price table.
     *
     * @return the catalog
     */
    public static PriceCatalog cad() {
        try (InputStream in = PriceCatalog.class.getResourceAsStream("stripe-prices-cad.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing stripe-prices-cad.json");
            }
            return new PriceCatalog(Json.mapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    PriceCatalog(JsonNode root) {
        this.prices = Objects.requireNonNull(root, "root").path("prices");
    }

    /**
     * Resolves a subscription price id.
     *
     * @param plan individual, group or unlimited
     * @param level basic, premium or ultimate
     * @param period monthly or annual
     * @param tier standard or academic
     * @return the Stripe price id
     * @throws IllegalArgumentException when the combination is unknown
     */
    public String priceId(String plan, String level, String period, String tier) {
        String key = String.join("_",
                require(plan, "plan").toLowerCase(Locale.ROOT),
                require(level, "level").toLowerCase(Locale.ROOT),
                require(period, "period").toLowerCase(Locale.ROOT),
                require(tier, "tier").toLowerCase(Locale.ROOT));
        JsonNode id = prices.get(key);
        if (id == null || id.asText("").isBlank()) {
            throw new IllegalArgumentException("Unknown price " + key);
        }
        return id.asText();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
