package com.bioinfo.cytoscope.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bioinfo.cytoscope.experiment.MissingSampleException;
import com.bioinfo.cytoscope.fcs.FcsFormatException;
import com.bioinfo.cytoscope.server.JsonHandler.Failure;
import com.bioinfo.cytoscope.server.JsonHandler.Kind;
import com.bioinfo.cytoscope.server.workspace.CapacityException;
import com.bioinfo.cytoscope.server.workspace.WorkspaceGoneException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class JsonHandlerTest {

    @Test
    void translatesExceptionsIntoStatusCodes() {
        assertEquals(new Failure(413, Kind.TOO_LARGE, "big"),
                JsonHandler.describe(new HttpError(413, "big")));
        assertEquals(Kind.UNEXPECTED, JsonHandler.describe(new HttpError(418, "tea")).kind());
        assertEquals(Kind.PARSING, JsonHandler.describe(new FcsFormatException("x")).kind());
        assertEquals(404, JsonHandler.describe(new MissingSampleException("s1")).status());
        assertEquals(410, JsonHandler.describe(new WorkspaceGoneException("w")).status());
        assertEquals(503, JsonHandler.describe(new CapacityException("full")).status());
        assertEquals(400, JsonHandler.describe(new IllegalArgumentException("bad")).status());
        assertEquals(new Failure(500, Kind.UNEXPECTED, "Internal server error"),
                JsonHandler.describe(new IOException("disk")));
    }

    @Test
    void decodesQueriesAndNamesAttachments() {
        assertEquals(Map.of("name", "NC 1.fcs", "flag", ""),
                JsonHandler.parseQuery("name=NC+1.fcs&&flag&name=other"));
        assertEquals(Map.of(), JsonHandler.parseQuery(null));
        assertEquals(Map.of(), JsonHandler.parseQuery(""));
        assertEquals("attachment; filename=\"Rapport_n__1.pdf\"; "
                + "filename*=UTF-8''Rapport%20n%C2%B0%201.pdf",
                JsonHandler.contentDisposition("Rapport n° 1.pdf"));
    }

    @Test
    void limitsTheRequestBodies() throws IOException {
        JsonHandler.Request big = new JsonHandler.Request("POST", List.of(), Map.of(),
                new ByteArrayInputStream(new byte[JsonHandler.MAX_BODY_BYTES + 1]));
        assertEquals(413, assertThrows(HttpError.class, () -> big.json(Map.class)).status());
        JsonHandler.Request empty = new JsonHandler.Request("POST", List.of(), Map.of(),
                new ByteArrayInputStream("null".getBytes()));
        assertEquals(400, assertThrows(HttpError.class, () -> empty.json(Map.class)).status());
        JsonHandler.Request upload = new JsonHandler.Request("POST", List.of(), Map.of(),
                new ByteArrayInputStream(new byte[JsonHandler.MAX_UPLOAD_BYTES + 1]));
        assertEquals(413, assertThrows(HttpError.class, upload::upload).status());
        JsonHandler.Request params = new JsonHandler.Request("GET", List.of("a"),
                Map.of("x", " 1 ", "y", " "), new ByteArrayInputStream(new byte[0]));
        assertEquals("1", params.param("x", "d"));
        assertEquals("d", params.param("y", "d"));
        assertEquals("d", params.param("z", "d"));
    }

    @Test
    void writesNumbersThatJavaScriptCanRead() throws IOException {
        assertEquals("{\"a\":null,\"b\":null,\"c\":1.5}", Json.mapper().writeValueAsString(
                new TreeMap<>(Map.of("a", Double.NaN, "b",
                        Double.POSITIVE_INFINITY, "c", 1.5))));
        record Point(double x) {}
        assertEquals("{\"x\":null}", Json.mapper().writeValueAsString(new Point(Double.NaN)));
    }
}
