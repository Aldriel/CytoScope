package com.bioinfo.cytoscope.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.fcs.FcsReader;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.fcs.FcsWriter;
import com.bioinfo.cytoscope.server.api.PlotView;
import com.bioinfo.cytoscope.server.billing.BillingService;
import com.bioinfo.cytoscope.server.billing.CreditBook;
import com.bioinfo.cytoscope.server.billing.StripeClient;
import com.bioinfo.cytoscope.server.workspace.WorkspaceRegistry;
import com.bioinfo.cytoscope.service.CytoService;
import com.bioinfo.cytoscope.service.Example;
import com.bioinfo.cytoscope.service.ExampleLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Functional tests of the REST API: a real server on a free port, called over HTTP as the React
 * client does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiServerTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private HttpServer server;
    private String base;

    @BeforeAll
    void start() throws IOException {
        BillingService billing = new BillingService(new CreditBook(), new StripeClient() {
            @Override
            public String createCheckoutSession(String priceId, String accessCode,
                                                String customerEmail, String successUrl,
                                                String cancelUrl) {
                return "https://checkout.test/session/" + accessCode;
            }

            @Override
            public java.util.Map<String, String> priceMetadata(String priceId) {
                return java.util.Map.of("credits_monthly", "500", "plan", "individual",
                        "level", "basic");
            }
        });
        server = ApiServer.create("127.0.0.1", 0, new CytoService(8), new WorkspaceRegistry(),
                billing);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    void stop() {
        server.stop(0);
    }

    @Test
    void startsWithAnExplicitPortArgument() throws Exception {
        HttpServer ephemeral = ApiServer.start("0");
        try {
            assertTrue(ephemeral.getAddress().getPort() > 0);
        } finally {
            ephemeral.stop(0);
        }
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null ? BodyPublishers.noBody()
                : BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json").method(method, publisher).build(),
                BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> sendForBytes(String method, String path, String body)
            throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base + path))
                .method(method, BodyPublishers.ofString(body == null ? "" : body)).build(),
                BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> upload(String workspace, String name, byte[] bytes)
            throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base + "/api/workspaces/"
                        + workspace + "/samples?name=" + name))
                .header("Content-Type", "application/octet-stream")
                .POST(BodyPublishers.ofByteArray(bytes)).build(), BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws IOException {
        return Json.mapper().readTree(response.body());
    }

    private static JsonNode ok(HttpResponse<String> response) throws IOException {
        assertEquals(200, response.statusCode(), response.body());
        return json(response);
    }

    private static void assertError(HttpResponse<String> response, int status, String kind)
            throws IOException {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(kind, json(response).get("kind").asText());
    }

    private JsonNode open(String example, String language) throws Exception {
        return ok(send("POST", "/api/workspaces", "{\"example\":\"" + example
                + "\",\"language\":\"" + language + "\"}"));
    }

    @Test
    void reportsItsHealthAndExamples() throws Exception {
        JsonNode health = ok(send("GET", "/api/health", null));
        assertEquals("UP", health.get("status").asText());
        JsonNode examples = ok(send("GET", "/api/examples", null));
        assertEquals(3, examples.get("examples").size());
        assertEquals(24, examples.get("examples").get(2).get("tubes").asInt());
        assertTrue(examples.get("attribution").asText().contains("licenses/by/4.0"));
        assertError(send("GET", "/api/examples/x", null), 404, "NOT_FOUND");
        assertError(send("POST", "/api/examples", "{}"), 405, "INVALID_INPUT");
        HttpResponse<String> options = send("OPTIONS", "/api/examples", null);
        assertEquals(204, options.statusCode());
        assertTrue(options.headers().firstValue("Access-Control-Allow-Methods").orElseThrow()
                .contains("PUT"));
    }

    @Test
    void startsCheckoutAndGrantsCreditsFromStripeWebhooks() throws Exception {
        JsonNode checkout = ok(send("POST", "/api/billing/checkout",
                "{\"priceId\":\"price_test\",\"email\":\"lab@example.com\","
                        + "\"successUrl\":\"https://ok\",\"cancelUrl\":\"https://cancel\"}"));
        String code = checkout.get("accessCode").asText();
        assertTrue(checkout.get("checkoutUrl").asText().contains(code));
        JsonNode pending = ok(send("GET", "/api/billing/accounts/" + code, null));
        assertEquals(0, pending.get("credits").asInt());

        ok(send("POST", "/api/billing/stripe/webhook",
                "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                        + "\"client_reference_id\":\"" + code + "\","
                        + "\"customer_email\":\"lab@example.com\","
                        + "\"customer\":\"cus_1\",\"subscription\":\"sub_1\","
                        + "\"metadata\":{\"price_id\":\"price_test\",\"credits_monthly\":\"500\","
                        + "\"plan\":\"individual\",\"level\":\"basic\"}}}}"));
        JsonNode balance = ok(send("GET", "/api/billing/accounts/" + code, null));
        assertEquals(500, balance.get("credits").asInt());
        assertEquals("individual", balance.get("plan").asText());
        assertTrue(balance.get("active").asBoolean());
        assertError(send("GET", "/api/billing/nope", null), 404, "NOT_FOUND");
        assertError(send("GET", "/api/billing", null), 404, "NOT_FOUND");
        assertError(send("GET", "/api/billing/accounts", null), 404, "NOT_FOUND");
        assertError(send("POST", "/api/billing/checkout",
                "{\"priceId\":\"\",\"email\":\"a\",\"successUrl\":\"https://ok\","
                        + "\"cancelUrl\":\"https://cancel\"}"), 400, "INVALID_INPUT");
        assertError(send("GET", "/api/billing/checkout", null), 405, "INVALID_INPUT");
        assertError(send("POST", "/api/billing/stripe/other", "{}"), 404, "NOT_FOUND");
        ok(send("POST", "/api/billing/stripe/webhook",
                "{\"type\":\"invoice.paid\",\"data\":{\"object\":{"
                        + "\"subscription\":\"sub_1\",\"customer\":\"cus_1\","
                        + "\"customer_email\":\"lab@example.com\","
                        + "\"lines\":{\"data\":[{\"price\":{\"id\":\"price_test\"}}]}}}}"));
    }

    @Test
    void loadsFilesIntoAnEmptyWorkspace() throws Exception {
        JsonNode created = ok(send("POST", "/api/workspaces", "{\"language\":\"fr-CA\"}"));
        String id = created.get("id").asText();
        assertEquals(32, id.length());
        assertEquals("fr", created.get("language").asText());
        assertEquals(30, created.get("timeToLiveMinutes").asInt());
        assertEquals("Tous les événements",
                created.get("experiment").get("gating").get("name").asText());

        FcsSample tube = ExampleLibrary.samples(Example.CADMIUM).get(0);
        JsonNode loaded = ok(upload(id, "Cd%200uM_1.fcs", FcsWriter.write(tube)));
        assertEquals(1, loaded.get("samples").size());
        JsonNode sample = loaded.get("samples").get(0);
        assertEquals("Cd 0uM_1", sample.get("name").asText());
        assertEquals("s1", sample.get("id").asText());
        assertEquals("FILE", loaded.get("experiment").get("compensation").asText());
        ObjectNode none = (ObjectNode) loaded.get("experiment");
        none.put("compensation", "NONE").putNull("matrix");
        ok(send("PUT", "/api/workspaces/" + id + "/experiment", none.toString()));
        JsonNode fromFile = ok(send("POST", "/api/workspaces/" + id + "/compensation/file", null));
        assertEquals("FILE", fromFile.get("experiment").get("compensation").asText());
        assertTrue(fromFile.get("experiment").get("matrix").get("channels").size() > 1);
        assertEquals("0 µM", loaded.get("experiment").get("controlGroup").asText());
        assertEquals(0, loaded.get("experiment").get("samples").get(0).get("dose").asDouble());
        JsonNode channel = sample.get("channels").get(0);
        assertEquals("scatter", channel.get("kind").asText());

        assertError(upload(id, "bad.fcs", "not an FCS file".getBytes(StandardCharsets.UTF_8)),
                400, "PARSING");
        assertError(upload("0123", "x.fcs", new byte[] {1}), 410, "EXPIRED");

        assertError(send("DELETE", "/api/workspaces/" + id + "/samples/s9", null), 404,
                "NOT_FOUND");
        JsonNode emptied = ok(send("DELETE", "/api/workspaces/" + id + "/samples/s1", null));
        assertEquals(0, emptied.get("samples").size());
        assertEquals(0, emptied.get("experiment").get("samples").size());
        assertError(send("POST", "/api/workspaces/" + id + "/compensation/file", null), 400,
                "INVALID_INPUT");

        ok(send("DELETE", "/api/workspaces/" + id, null));
        assertError(send("GET", "/api/workspaces/" + id, null), 410, "EXPIRED");
    }

    @Test
    void analyzesTheCadmiumExample() throws Exception {
        JsonNode workspace = open("cadmium", "en");
        String id = workspace.get("id").asText();
        assertEquals("cadmium", workspace.get("example").asText());
        assertTrue(workspace.get("simulated").asBoolean());
        assertEquals(24, workspace.get("samples").size());

        JsonNode results = ok(send("GET", "/api/workspaces/" + id + "/results", null));
        assertEquals(24, results.get("samples").size());
        assertEquals(8.08, results.get("doseResponse").get("fit").get("ec50").asDouble(), 0.15);
        JsonNode groups = results.get("comparison").get("groups");
        assertEquals("0 µM", groups.get(0).get("name").asText());
        assertTrue(groups.get(0).get("adjustedP").isNull());
        assertEquals("***", groups.get(6).get("stars").asText());
        JsonNode control = results.get("samples").get(21);
        assertTrue(control.get("cellCycle").isNull());
        assertTrue(results.get("samples").get(0).get("apoptosis").get("LIVE").asDouble() > 80);

        HttpResponse<byte[]> pdf = sendForBytes("POST", "/api/workspaces/" + id + "/report",
                "{\"title\":\"Cadmium / HepG2\",\"analyst\":\"M. Ethier\",\"language\":\"fr\","
                        + "\"timeZone\":\"Europe/Paris\"}");
        assertEquals(200, pdf.statusCode());
        assertEquals("%PDF", new String(pdf.body(), 0, 4, StandardCharsets.US_ASCII));
        assertTrue(pdf.headers().firstValue("Content-Disposition").orElseThrow()
                .contains("Cadmium%20_%20HepG2.pdf"));
        assertError(send("POST", "/api/workspaces/" + id + "/report",
                "{\"timeZone\":\"Mars/Olympus\"}"), 400, "INVALID_INPUT");

        HttpResponse<byte[]> fcs = sendForBytes("GET", "/api/workspaces/" + id
                + "/samples/s1/fcs?population=p1", null);
        assertEquals(200, fcs.statusCode());
        FcsSample exported = FcsReader.read("export", fcs.body());
        int cells = results.get("samples").get(0).get("populations").get(1).get("count").asInt();
        assertEquals(cells, exported.eventCount());
        assertError(send("GET", "/api/workspaces/" + id + "/samples/s1/fcs?population=p9",
                null), 400, "INVALID_INPUT");
        assertError(send("GET", "/api/workspaces/" + id + "/samples/s99/fcs", null), 404,
                "NOT_FOUND");
        assertError(send("GET", "/api/workspaces/" + id + "/samples/s1/csv", null), 404,
                "NOT_FOUND");
    }

    @Test
    void drawsPlots() throws Exception {
        String id = open("u87-cell-cycle", "fr").get("id").asText();
        String plot = "/api/workspaces/" + id + "/plot";
        JsonNode density = ok(send("POST", plot,
                "{\"sampleId\":\"s1\",\"x\":\"PI-A\",\"y\":\"PI-W\",\"bins\":64}"));
        assertEquals(64 * 64, density.get("density").get("counts").size());
        assertEquals("LINEAR", density.get("x").get("scale").asText());
        assertEquals(257, density.get("x").get("values").size());
        assertTrue(density.get("x").get("ticks").size() > 2);
        assertEquals("all", density.get("populationId").asText());
        assertEquals(2, density.get("populations").size());
        assertTrue(density.get("histogram").isNull());

        JsonNode histogram = ok(send("POST", plot, "{\"sampleId\":\"s1\",\"x\":\"PI-A\","
                + "\"populationId\":\"p1\",\"xScale\":\"LOGICLE\"}"));
        assertEquals(PlotView.DEFAULT_BINS, histogram.get("histogram").size());
        assertEquals("LOGICLE", histogram.get("x").get("scale").asText());
        assertTrue(histogram.get("y").isNull());

        assertError(send("POST", plot, "{\"sampleId\":\"s1\",\"x\":\"APC-A\"}"), 400,
                "INVALID_INPUT");
        assertError(send("POST", plot, "{\"sampleId\":\"s1\",\"x\":\"PI-A\","
                + "\"populationId\":\"p7\"}"), 400, "INVALID_INPUT");
        assertError(send("POST", plot, "{\"sampleId\":\"s1\",\"x\":\"PI-A\",\"bins\":4}"),
                400, "INVALID_INPUT");
        assertError(send("POST", plot, "{\"sampleId\":\"s1\"}"), 400, "INVALID_INPUT");
        assertError(send("POST", plot, "{\"x\":\"PI-A\"}"), 400, "INVALID_INPUT");
        assertError(send("POST", plot, "not json"), 400, "INVALID_INPUT");
        assertError(send("GET", plot, null), 405, "INVALID_INPUT");
    }

    @Test
    void startsOnTheGivenPort() throws Exception {
        HttpServer started = ApiServer.start("0");
        try {
            assertTrue(started.getAddress().getPort() > 0);
        } finally {
            started.stop(0);
        }
    }

    @Test
    void readsTheCellCycleWithDefaults() throws Exception {
        JsonNode workspace = ok(send("POST", "/api/workspaces",
                "{\"example\":\"u87-cell-cycle\"}"));
        String id = workspace.get("id").asText();
        assertEquals("en", workspace.get("language").asText());
        assertEquals("en", ok(send("POST", "/api/workspaces", "{\"example\":\"\"}"))
                .get("language").asText());

        JsonNode results = ok(send("GET", "/api/workspaces/" + id + "/results", null));
        JsonNode cycle = results.get("samples").get(0).get("cellCycle");
        assertEquals(4, cycle.get("bounds").size());
        assertTrue(cycle.get("percents").get("G0_G1").asDouble() > 40);
        assertTrue(results.get("doseResponse").isNull());
        assertEquals("TOO_FEW_DOSES", results.get("doseResponseIssue").asText());
        assertTrue(results.get("samples").get(0).get("apoptosis").isNull());

        HttpResponse<byte[]> pdf = sendForBytes("POST", "/api/workspaces/" + id + "/report",
                "{\"timeZone\":\" \"}");
        assertEquals(200, pdf.statusCode());
        assertTrue(pdf.headers().firstValue("Content-Disposition").orElseThrow()
                .contains("CytoScope.pdf"));

        ObjectNode experiment = (ObjectNode) workspace.get("experiment");
        ObjectNode quadrant = Json.mapper().createObjectNode();
        quadrant.put("type", "quadrant").put("xChannel", "PI-A").put("yChannel", "PI-W")
                .put("xThreshold", 1).put("yThreshold", 2).put("quadrant", "POSITIVE_NEGATIVE");
        ObjectNode region = Json.mapper().createObjectNode();
        region.put("id", "p2").put("name", "Q").set("gate", quadrant);
        ((ObjectNode) experiment.get("gating").get("children").get(0)).withArray("children")
                .add(region);
        experiment.remove("compensation");
        ((ObjectNode) experiment.get("samples").get(0)).putNull("dose");
        JsonNode edited = ok(send("PUT", "/api/workspaces/" + id + "/experiment",
                experiment.toString()));
        assertEquals("NONE", edited.get("experiment").get("compensation").asText());
        assertEquals("quadrant", edited.get("experiment").get("gating").get("children").get(0)
                .get("children").get(0).get("gate").get("type").asText());
        ObjectNode noSamples = experiment.deepCopy();
        noSamples.remove("samples");
        assertError(send("PUT", "/api/workspaces/" + id + "/experiment", noSamples.toString()),
                400, "INVALID_INPUT");
    }

    @Test
    void editsTheExperiment() throws Exception {
        JsonNode workspace = open("u87-apoptosis", "en");
        String id = workspace.get("id").asText();
        String path = "/api/workspaces/" + id;
        assertEquals("CONTROLS", workspace.get("experiment").get("compensation").asText());

        ObjectNode experiment = (ObjectNode) workspace.get("experiment");
        ((ObjectNode) experiment.get("samples").get(0)).put("group", "Renamed");
        ObjectNode cells = (ObjectNode) experiment.get("gating").get("children").get(0);
        ((ObjectNode) cells.get("gate")).put("xMin", 50_000);
        JsonNode edited = ok(send("PUT", path + "/experiment", experiment.toString()));
        assertEquals("Renamed", edited.get("experiment").get("samples").get(0).get("group")
                .asText());
        assertEquals(50_000, edited.get("experiment").get("gating").get("children").get(0)
                .get("gate").get("xMin").asDouble());

        ObjectNode polygon = Json.mapper().createObjectNode();
        polygon.put("type", "polygon").put("xChannel", "FITC-A").put("yChannel", "PI-A");
        polygon.putArray("xs").add(0).add(1000).add(0);
        polygon.putArray("ys").add(0).add(0).add(1000);
        ObjectNode child = Json.mapper().createObjectNode();
        child.put("id", "p9").put("name", "Corner").set("gate", polygon);
        child.putArray("children");
        cells.withArray("children").add(child);
        ObjectNode interval = Json.mapper().createObjectNode();
        interval.put("type", "interval").put("xChannel", "PI-A").put("min", 0).put("max", 10);
        ObjectNode intervalChild = Json.mapper().createObjectNode();
        intervalChild.put("id", "p10").put("name", "PI low").set("gate", interval);
        cells.withArray("children").add(intervalChild);
        JsonNode withPolygon = ok(send("PUT", path + "/experiment", experiment.toString()));
        assertEquals("polygon", withPolygon.get("experiment").get("gating").get("children")
                .get(0).get("children").get(0).get("gate").get("type").asText());

        ObjectNode missing = experiment.deepCopy();
        missing.withArray("samples").remove(0);
        assertError(send("PUT", path + "/experiment", missing.toString()), 400,
                "INVALID_INPUT");
        ObjectNode noGating = experiment.deepCopy();
        noGating.remove("gating");
        assertError(send("PUT", path + "/experiment", noGating.toString()), 400,
                "INVALID_INPUT");
        ObjectNode badGate = experiment.deepCopy();
        ((ObjectNode) badGate.get("gating").get("children").get(0).get("gate"))
                .remove("xChannel");
        assertError(send("PUT", path + "/experiment", badGate.toString()), 400,
                "INVALID_INPUT");
        ObjectNode badPolygon = experiment.deepCopy();
        ((ObjectNode) badPolygon.get("gating").get("children").get(0).get("children").get(0)
                .get("gate")).remove("xs");
        assertError(send("PUT", path + "/experiment", badPolygon.toString()), 400,
                "INVALID_INPUT");
        ObjectNode badMetric = experiment.deepCopy();
        ((ObjectNode) badMetric.get("metric")).put("kind", "CELL_FATE").put("target", "DEAD");
        assertError(send("PUT", path + "/experiment", badMetric.toString()), 400,
                "INVALID_INPUT");

        JsonNode settings = edited.get("experiment").get("apoptosis");
        JsonNode suggested = ok(send("POST", path + "/apoptosis/suggest", settings.toString()));
        assertNotEquals(settings.get("annexinThreshold").asDouble(),
                suggested.get("experiment").get("apoptosis").get("annexinThreshold").asDouble());

        ObjectNode file = experiment.deepCopy();
        file.put("compensation", "NONE").putNull("matrix");
        assertEquals("NONE", ok(send("PUT", path + "/experiment", file.toString()))
                .get("experiment").get("compensation").asText());
        JsonNode computed = ok(send("POST", path + "/compensation/controls", null));
        assertEquals("CONTROLS", computed.get("experiment").get("compensation").asText());
        assertEquals(2, computed.get("experiment").get("matrix").get("channels").size());

        assertError(send("POST", path + "/compensation", null), 404, "NOT_FOUND");
        assertError(send("GET", path + "/compensation/controls", null), 405, "INVALID_INPUT");
        assertError(send("POST", path + "/unknown", null), 404, "NOT_FOUND");
        assertError(send("POST", path, null), 405, "INVALID_INPUT");
        assertError(send("PUT", "/api/workspaces", "{}"), 405, "INVALID_INPUT");
        assertError(send("POST", "/api/workspaces", "{\"example\":\"x\"}"), 400,
                "INVALID_INPUT");
    }
}
