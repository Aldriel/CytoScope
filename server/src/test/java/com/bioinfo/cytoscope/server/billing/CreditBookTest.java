package com.bioinfo.cytoscope.server.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.server.HttpError;
import org.junit.jupiter.api.Test;

class CreditBookTest {

    @Test
    void grantsDebitsAndDeactivatesSubscriptions() {
        CreditBook book = new CreditBook();
        AccessAccount pending = book.createPending("buyer@example.com");
        assertEquals(0, pending.credits());
        assertTrue(book.find(pending.accessCode().toLowerCase()).isPresent());

        AccessAccount granted = book.grant(pending.accessCode(), "buyer@example.com", 500,
                "individual", "basic", "cus_1", "sub_1");
        assertEquals(500, granted.credits());
        assertEquals(490, book.debit(pending.accessCode(), 10).credits());

        HttpError shortfall = assertThrows(HttpError.class,
                () -> book.debit(pending.accessCode(), 10_000));
        assertEquals(402, shortfall.status());

        assertTrue(book.findBySubscription("sub_1").isPresent());
        assertEquals(false, book.deactivateSubscription("sub_1").orElseThrow().active());
        HttpError inactive = assertThrows(HttpError.class,
                () -> book.debit(pending.accessCode(), 1));
        assertEquals(402, inactive.status());

        assertFalse(book.deactivateSubscription("missing").isPresent());
        assertFalse(book.deactivateSubscription(" ").isPresent());
        assertFalse(book.findBySubscription(" ").isPresent());
        assertFalse(book.find(" ").isPresent());
        assertFalse(book.find(null).isPresent());
        assertEquals(404, assertThrows(HttpError.class, () -> book.debit("NOPE", 1)).status());
        assertThrows(IllegalArgumentException.class, () -> book.debit(pending.accessCode(), -1));
        assertThrows(IllegalArgumentException.class,
                () -> book.grant(null, "", -1, "", "", "", ""));
        AccessAccount minted = book.grant(null, "x@y.z", 10, "group", "premium", "cus", "sub");
        assertEquals(10, book.debit(minted.accessCode(), 0).credits());
        assertTrue(book.size() >= 2);
    }
}
