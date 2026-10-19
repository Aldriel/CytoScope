package com.bioinfo.cytoscope.server.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.server.HttpError;
import com.bioinfo.cytoscope.server.workspace.WorkspaceRegistry;
import com.bioinfo.cytoscope.service.Example;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BillingServiceTest {

    @Test
    void allowsFiveTrialUploadsThenRequiresCredits() {
        BillingService billing = new BillingService(new CreditBook(), StripeClient.disabled());
        var workspace = new WorkspaceRegistry().create(null, "en",
                Experiment.empty("All events"), Map.of());
        FcsSample sample = tube(100_000);
        for (int i = 0; i < CreditMeter.FREE_TRIAL_FILES; i++) {
            billing.authorizeUpload(workspace, sample, "");
        }
        HttpError blocked = assertThrows(HttpError.class,
                () -> billing.authorizeUpload(workspace, sample, ""));
        assertEquals(402, blocked.status());

        AccessAccount account = billing.book().grant(null, "a@b.c", 5, "individual", "basic",
                "", "");
        billing.authorizeUpload(workspace, sample, account.accessCode());
        assertEquals(4, billing.balance(account.accessCode()).credits());
        assertEquals(404, assertThrows(HttpError.class, () -> billing.balance("MISSING"))
                .status());
    }

    @Test
    void ignoresUploadsOnExampleWorkspaces() {
        BillingService billing = new BillingService(new CreditBook(), StripeClient.disabled());
        var workspace = new WorkspaceRegistry().create(Example.CADMIUM, "en",
                Experiment.empty("All events"), Map.of());
        for (int i = 0; i < 20; i++) {
            billing.authorizeUpload(workspace, tube(1), "");
        }
    }

    @Test
    void grantsCreditsFromCheckoutAndInvoiceWebhooks() throws IOException {
        CreditBook book = new CreditBook();
        BillingService billing = new BillingService(book, fakeStripe());
        assertThrows(IllegalArgumentException.class,
                () -> billing.startCheckout("", "a@b.c", "https://ok", "https://no"));
        assertThrows(IllegalStateException.class,
                () -> new BillingService(book, StripeClient.disabled())
                        .startCheckout("price_x", "a@b.c", "https://ok", "https://no"));

        BillingService.CheckoutStart started = billing.startCheckout("price_x", "lab@example.com",
                "https://ok", "https://cancel");
        assertTrue(started.checkoutUrl().contains(started.accessCode()));

        Map<String, Object> granted = billing.handleStripeEvent("""
                {"type":"checkout.session.completed","data":{"object":{
                  "client_reference_id":"%s",
                  "customer_email":"lab@example.com",
                  "customer":"cus_1",
                  "subscription":"sub_1",
                  "metadata":{"price_id":"price_x"}
                }}}
                """.formatted(started.accessCode()));
        assertEquals(true, granted.get("granted"));
        assertEquals(500, billing.balance(started.accessCode()).credits());

        Map<String, Object> renewed = billing.handleStripeEvent(
                "{\"type\":\"invoice.paid\",\"data\":{\"object\":{"
                        + "\"subscription\":\"sub_1\",\"customer\":\"cus_1\","
                        + "\"customer_email\":\"lab@example.com\","
                        + "\"lines\":{\"data\":[{\"price\":{\"id\":\"price_x\","
                        + "\"metadata\":{\"credits_monthly\":\"750\",\"plan\":\"individual\","
                        + "\"level\":\"premium\"}}}]}}}}");
        assertEquals(true, renewed.get("renewed"));
        assertEquals(750, billing.balance(started.accessCode()).credits());

        assertEquals("invoice.paid-without-lines", billing.handleStripeEvent(
                "{\"type\":\"invoice.paid\",\"data\":{\"object\":{\"lines\":{\"data\":[]}}}}")
                .get("ignored"));
        assertEquals("invoice.paid-without-credits", billing.handleStripeEvent(
                "{\"type\":\"invoice.paid\",\"data\":{\"object\":{"
                        + "\"lines\":{\"data\":[{\"price\":{\"id\":\"price_x\","
                        + "\"metadata\":{}}}]}}}}").get("ignored"));

        billing.handleStripeEvent("""
                {"type":"customer.subscription.deleted","data":{"object":{"id":"sub_1"}}}
                """);
        assertFalse(billing.balance(started.accessCode()).active());
        assertEquals("unknown-subscription", billing.handleStripeEvent(
                "{\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"x\"}}}")
                .get("ignored"));
        assertEquals("other", billing.handleStripeEvent(
                "{\"type\":\"other\",\"data\":{\"object\":{}}}").get("ignored"));
    }

    @Test
    void checkoutWithoutCreditsMetadataFails() throws IOException {
        BillingService billing = new BillingService(new CreditBook(), new StripeClient() {
            @Override
            public String createCheckoutSession(String priceId, String accessCode,
                                                String customerEmail, String successUrl,
                                                String cancelUrl) {
                return "https://checkout.test/" + accessCode;
            }

            @Override
            public Map<String, String> priceMetadata(String priceId) {
                return Map.of();
            }
        });
        BillingService.CheckoutStart started = billing.startCheckout("price_x", "",
                "https://ok", "https://cancel");
        HttpError error = assertThrows(HttpError.class, () -> billing.handleStripeEvent("""
                {"type":"checkout.session.completed","data":{"object":{
                  "client_reference_id":"%s",
                  "metadata":{}
                }}}
                """.formatted(started.accessCode())));
        assertEquals(400, error.status());
    }

    @Test
    void stripeClientHelpers() throws IOException {
        assertTrue(StripeClient.fromSecret(null).isEmpty());
        assertTrue(StripeClient.fromSecret(" ").isEmpty());
        assertTrue(StripeClient.fromSecret("sk_test_x").isPresent());
        Exception disabled = assertThrows(Exception.class,
                () -> StripeClient.disabled().createCheckoutSession("p", "c", "", "s", "n"));
        assertTrue(disabled instanceof IllegalStateException);
        assertEquals(Map.of(), StripeClient.disabled().priceMetadata("p"));
    }

    @Test
    void accessAccountRejectsBlankCodes() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccessAccount(" ", "", 0, 0, "", "", "", "", true));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessAccount("ABC", "", -1, 0, "", "", "", "", true));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessAccount("ABC", "", 0, -1, "", "", "", "", true));
        AccessAccount account = new AccessAccount("ABC", null, 1, 1, null, null, null, null, true);
        assertEquals("", account.email());
        assertEquals(0, account.withCredits(0).credits());
        assertFalse(account.deactivated().active());
    }

    @Test
    void buildsFromEnvironmentWithoutStripeKey() {
        BillingService service = BillingService.fromEnvironment();
        assertEquals(0, service.book().size());
    }

    @Test
    void checkoutCanMintAccessCodeWhenWebhookOmitsIt() throws IOException {
        BillingService billing = new BillingService(new CreditBook(), fakeStripe());
        Map<String, Object> granted = billing.handleStripeEvent(
                "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                        + "\"customer_details\":{\"email\":\"new@lab.com\"},"
                        + "\"customer\":\"cus_9\",\"subscription\":\"sub_9\","
                        + "\"metadata\":{\"credits_monthly\":\"2000\",\"plan\":\"group\","
                        + "\"level\":\"basic\"}}}}");
        assertEquals(true, granted.get("granted"));
        String code = granted.get("accessCode").toString();
        assertEquals(2000, billing.balance(code).credits());
        assertEquals("group", billing.balance(code).plan());

        Map<String, Object> fromPriceId = billing.handleStripeEvent(
                "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                        + "\"metadata\":{\"priceId\":\"price_x\",\"access_code\":\"\"},"
                        + "\"customer_email\":\"solo@lab.com\"}}}");
        assertEquals(500, fromPriceId.get("credits"));

        Map<String, Object> invoiceNew = billing.handleStripeEvent(
                "{\"type\":\"invoice.paid\",\"data\":{\"object\":{"
                        + "\"subscription\":\"sub_new\",\"customer\":\"cus_new\","
                        + "\"customer_email\":\"renew@lab.com\","
                        + "\"lines\":{\"data\":[{\"price\":{\"id\":\"price_x\"}}]}}}}");
        assertEquals(true, invoiceNew.get("renewed"));
        assertEquals(500, billing.balance(invoiceNew.get("accessCode").toString()).credits());
    }

    @Test
    void blankAccessCodeUsesTheFreeTrial() {
        BillingService billing = new BillingService(new CreditBook(), StripeClient.disabled());
        var workspace = new WorkspaceRegistry().create(null, "en",
                Experiment.empty("All events"), Map.of());
        billing.authorizeUpload(workspace, tube(1), "  ");
    }

    private static StripeClient fakeStripe() {
        return new StripeClient() {
            @Override
            public String createCheckoutSession(String priceId, String accessCode,
                                                String customerEmail, String successUrl,
                                                String cancelUrl) {
                return "https://checkout.test/" + accessCode;
            }

            @Override
            public Map<String, String> priceMetadata(String priceId) {
                return Map.of("credits_monthly", "500", "plan", "individual", "level", "basic");
            }
        };
    }

    private static FcsSample tube(int events) {
        float[] values = new float[events];
        return new FcsSample("t", Map.of("$TOT", Integer.toString(events)),
                List.of(new Channel("FSC-A", "", 262144)), new float[][] {values});
    }
}
