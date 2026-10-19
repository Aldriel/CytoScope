package com.bioinfo.cytoscope.server.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PriceCatalogTest {

    @Test
    void resolvesCadSubscriptionPrices() {
        PriceCatalog catalog = PriceCatalog.cad();
        assertTrue(catalog.priceId("individual", "basic", "monthly", "standard")
                .startsWith("price_"));
        assertEquals(catalog.priceId("GROUP", "Premium", "annual", "academic"),
                catalog.priceId("group", "premium", "annual", "academic"));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.priceId("solo", "basic", "monthly", "standard"));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.priceId("", "basic", "monthly", "standard"));
    }
}
