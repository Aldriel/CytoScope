package com.bioinfo.cytoscope.server.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreditBookPersistenceTest {

    @TempDir
    Path temp;

    @Test
    void reloadsBalancesAfterARestart() throws Exception {
        Path store = temp.resolve("credits.json");
        CreditBook first = new CreditBook(store);
        AccessAccount granted = first.grant(null, "lab@example.com", 500, "individual", "basic",
                "cus", "sub");
        first.debit(granted.accessCode(), 40);
        assertTrue(Files.isRegularFile(store));

        CreditBook reloaded = new CreditBook(store);
        AccessAccount again = reloaded.find(granted.accessCode()).orElseThrow();
        assertEquals(460, again.credits());
        assertEquals(500, again.allowance());
        assertEquals("individual", again.plan());
        assertEquals(1, reloaded.size());
        assertEquals(store, reloaded.store().orElseThrow());
    }
}
