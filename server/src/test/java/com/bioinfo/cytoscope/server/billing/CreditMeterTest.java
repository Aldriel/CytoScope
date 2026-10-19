package com.bioinfo.cytoscope.server.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CreditMeterTest {

    @Test
    void roundsEventsUpToTheNextCredit() {
        assertEquals(0, CreditMeter.creditsFor(0));
        assertEquals(1, CreditMeter.creditsFor(1));
        assertEquals(1, CreditMeter.creditsFor(100_000));
        assertEquals(2, CreditMeter.creditsFor(100_001));
        assertEquals(10, CreditMeter.creditsFor(1_000_000));
        assertThrows(IllegalArgumentException.class, () -> CreditMeter.creditsFor(-1));
    }
}
