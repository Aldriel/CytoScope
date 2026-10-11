package com.bioinfo.cytoscope.fcs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes samples as FCS 3.1 files in single precision, which any analysis software can open.
 *
 * <p>Used to export a gated population and to create the simulated example files. The keywords
 * that describe the acquisition (cytometer, date, compensation...) are kept; those that describe
 * the layout of the file are recomputed.
 */
public final class FcsWriter {

    private static final char DELIMITER = '/';
    private static final int OFFSET_DIGITS = 12;
    private static final Set<String> LAYOUT_KEYWORDS = Set.of(
            "$BEGINANALYSIS", "$ENDANALYSIS", "$BEGINDATA", "$ENDDATA", "$BEGINSTEXT",
            "$ENDSTEXT", "$BYTEORD", "$DATATYPE", "$MODE", "$NEXTDATA", "$PAR", "$TOT");

    private FcsWriter() {}

    /**
     * Writes every event of a sample.
     *
     * @param sample the sample to write
     * @return content of the FCS file
     */
    public static byte[] write(FcsSample sample) {
        BitSet all = new BitSet(sample.eventCount());
        all.set(0, sample.eventCount());
        return write(sample, all);
    }

    /**
     * Writes the selected events of a sample, for example a gated population.
     *
     * @param sample the sample to write
     * @param events indices of the events to keep
     * @return content of the FCS file
     */
    public static byte[] write(FcsSample sample, BitSet events) {
        int parameterCount = sample.channels().size();
        int eventCount = events.cardinality();
        ByteBuffer data = ByteBuffer.allocate(eventCount * parameterCount * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        float[][] values = sample.rawValues();
        for (int e = events.nextSetBit(0); e >= 0; e = events.nextSetBit(e + 1)) {
            for (int p = 0; p < parameterCount; p++) {
                data.putFloat(values[p][e]);
            }
        }

        Map<String, String> keywords = new LinkedHashMap<>();
        keywords.put("$BYTEORD", "1,2,3,4");
        keywords.put("$DATATYPE", "F");
        keywords.put("$MODE", "L");
        keywords.put("$NEXTDATA", "0");
        keywords.put("$PAR", Integer.toString(parameterCount));
        keywords.put("$TOT", Integer.toString(eventCount));
        for (int p = 0; p < parameterCount; p++) {
            Channel channel = sample.channels().get(p);
            String prefix = "$P" + (p + 1);
            keywords.put(prefix + "N", channel.name());
            if (!channel.label().isEmpty()) {
                keywords.put(prefix + "S", channel.label());
            }
            keywords.put(prefix + "B", "32");
            keywords.put(prefix + "E", "0,0");
            keywords.put(prefix + "R", formatRange(channel.range()));
        }
        sample.keywords().forEach((key, value) -> {
            if (!LAYOUT_KEYWORDS.contains(key) && !isParameterLayout(key)) {
                keywords.putIfAbsent(key, value);
            }
        });

        int textStart = 58;
        byte[] text = text(keywords, 0, 0);
        int dataStart = textStart + text.length;
        int dataLength = data.capacity();
        int dataEnd = dataLength == 0 ? dataStart : dataStart + dataLength - 1;
        text = text(keywords, dataStart, dataEnd);
        int textEnd = textStart + text.length - 1;

        ByteArrayOutputStream out = new ByteArrayOutputStream(dataStart + dataLength);
        String header = String.format(Locale.ROOT, "FCS3.1    %8d%8d%8d%8d%8d%8d",
                textStart, textEnd, fitsHeader(dataStart, dataEnd), fitsHeader(dataEnd, dataEnd),
                0, 0);
        out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(text);
        out.writeBytes(data.array());
        return out.toByteArray();
    }

    /** The header has 8 digits per offset; larger offsets are written as 0 and read from TEXT. */
    private static int fitsHeader(int offset, int dataEnd) {
        return dataEnd > 99_999_999 ? 0 : offset;
    }

    private static boolean isParameterLayout(String key) {
        return key.matches("\\$P\\d+[NSBERG]");
    }

    private static String formatRange(double range) {
        return range == Math.rint(range) ? Long.toString((long) range) : Double.toString(range);
    }

    /** The data offsets have a fixed width so that the length of the TEXT does not change. */
    private static byte[] text(Map<String, String> keywords, int dataStart, int dataEnd) {
        StringBuilder text = new StringBuilder();
        text.append(DELIMITER);
        append(text, "$BEGINANALYSIS", "0");
        append(text, "$ENDANALYSIS", "0");
        append(text, "$BEGINSTEXT", "0");
        append(text, "$ENDSTEXT", "0");
        append(text, "$BEGINDATA", pad(dataStart));
        append(text, "$ENDDATA", pad(dataEnd));
        keywords.forEach((key, value) -> append(text, key, value));
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String pad(int offset) {
        return String.format(Locale.ROOT, "%0" + OFFSET_DIGITS + "d", offset);
    }

    private static void append(StringBuilder text, String key, String value) {
        String delimiter = String.valueOf(DELIMITER);
        String safeValue = value.isEmpty() ? " " : value;
        text.append(key.replace(delimiter, delimiter + delimiter)).append(DELIMITER)
                .append(safeValue.replace(delimiter, delimiter + delimiter)).append(DELIMITER);
    }
}
