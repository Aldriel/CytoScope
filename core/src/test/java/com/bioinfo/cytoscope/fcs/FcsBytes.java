package com.bioinfo.cytoscope.fcs;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Builds FCS files byte by byte, to test the reader on layouts that no example file has. */
final class FcsBytes {

    private final Map<String, String> keywords = new LinkedHashMap<>();
    private String version = "FCS3.0";
    private char delimiter = '/';
    private byte[] data = new byte[0];
    private boolean dataInHeader = true;
    private Charset charset = StandardCharsets.UTF_8;

    FcsBytes version(String value) {
        version = value;
        return this;
    }

    FcsBytes delimiter(char value) {
        delimiter = value;
        return this;
    }

    FcsBytes keyword(String key, String value) {
        keywords.put(key, value);
        return this;
    }

    FcsBytes data(byte[] value) {
        data = value;
        return this;
    }

    FcsBytes dataOffsetsInTextOnly() {
        dataInHeader = false;
        return this;
    }

    FcsBytes charset(Charset value) {
        charset = value;
        return this;
    }

    byte[] build() {
        int textStart = 58;
        byte[] text = text(0, 0);
        int dataStart = textStart + text.length;
        int dataEnd = dataStart + Math.max(0, data.length - 1);
        text = text(dataStart, dataEnd);
        int textEnd = textStart + text.length - 1;
        String header = String.format(Locale.ROOT, "%-6s    %8d%8d%8d%8d%8d%8d", version,
                textStart, textEnd, dataInHeader ? dataStart : 0, dataInHeader ? dataEnd : 0, 0,
                0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(text);
        out.writeBytes(data);
        return out.toByteArray();
    }

    private byte[] text(int dataStart, int dataEnd) {
        StringBuilder text = new StringBuilder().append(delimiter);
        Map<String, String> all = new LinkedHashMap<>(keywords);
        all.put("$BEGINDATA", String.format(Locale.ROOT, "%010d", dataStart));
        all.put("$ENDDATA", String.format(Locale.ROOT, "%010d", dataEnd));
        String d = String.valueOf(delimiter);
        all.forEach((k, v) -> text.append(k.replace(d, d + d)).append(delimiter)
                .append(v.replace(d, d + d)).append(delimiter));
        return text.toString().getBytes(charset);
    }
}
