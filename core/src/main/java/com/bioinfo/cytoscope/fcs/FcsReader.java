package com.bioinfo.cytoscope.fcs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads FCS 2.0, 3.0 and 3.1 files in list mode, the format written by flow cytometers.
 *
 * <p>An FCS file has a HEADER that gives the position of the other segments, a TEXT segment of
 * keyword/value pairs that describes the acquisition, and a DATA segment that holds the values,
 * event after event. Integer, single and double precision values are supported, in both byte
 * orders. Integer values are converted as the FCS standard prescribes: logarithmic amplification
 * ($PnE) and gain ($PnG) are applied, so that the values are those a user expects.
 */
public final class FcsReader {

    private static final int HEADER_LENGTH = 58;
    private static final int MAX_EVENTS = 50_000_000;

    private FcsReader() {}

    /**
     * Reads the first data set of an FCS file.
     *
     * @param name name given to the sample
     * @param bytes content of the file
     * @return the sample, with its keywords, channels and values
     * @throws FcsFormatException if the content is not a supported FCS file
     */
    public static FcsSample read(String name, byte[] bytes) {
        Header header = readHeader(bytes);
        Map<String, String> keywords = parseText(bytes, header.textStart(), header.textEnd());
        long supplementalStart = longKeyword(keywords, "$BEGINSTEXT", 0);
        long supplementalEnd = longKeyword(keywords, "$ENDSTEXT", 0);
        if (supplementalStart > 0 && supplementalEnd > supplementalStart) {
            Map<String, String> supplemental =
                    parseText(bytes, supplementalStart, supplementalEnd);
            supplemental.forEach(keywords::putIfAbsent);
        }

        String mode = keywords.getOrDefault("$MODE", "L").trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("L")) {
            throw new FcsFormatException(
                    "Only list mode files are supported ($MODE is " + mode + ")");
        }
        int parameterCount = (int) requiredLong(keywords, "$PAR");
        long eventCount = requiredLong(keywords, "$TOT");
        if (parameterCount <= 0 || eventCount < 0 || eventCount > MAX_EVENTS) {
            throw new FcsFormatException("Invalid number of parameters or events");
        }

        long dataStart = header.dataStart();
        long dataEnd = header.dataEnd();
        if (dataStart == 0 && dataEnd == 0) {
            dataStart = requiredLong(keywords, "$BEGINDATA");
            dataEnd = requiredLong(keywords, "$ENDDATA");
        }

        List<Channel> channels = new ArrayList<>(parameterCount);
        for (int p = 1; p <= parameterCount; p++) {
            String channelName = keywords.get("$P" + p + "N");
            if (channelName == null || channelName.isBlank()) {
                throw new FcsFormatException("The parameter " + p + " has no name ($P" + p + "N)");
            }
            channels.add(new Channel(
                    channelName.trim(),
                    keywords.get("$P" + p + "S"),
                    Math.max(1, doubleKeyword(keywords, "$P" + p + "R", 262_144))));
        }

        float[][] values = readData(bytes, keywords, channels, (int) eventCount,
                dataStart, dataEnd);
        return new FcsSample(name, keywords, channels, values);
    }

    private record Header(String version, long textStart, long textEnd,
                          long dataStart, long dataEnd) {}

    private static Header readHeader(byte[] bytes) {
        if (bytes.length < HEADER_LENGTH) {
            throw new FcsFormatException("The file is too short to be an FCS file");
        }
        String version = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
        if (!version.matches("FCS[23]\\.[01]")) {
            throw new FcsFormatException("Not an FCS file (the header starts with \""
                    + version.replaceAll("\\p{Cntrl}", "?") + "\")");
        }
        return new Header(version,
                headerOffset(bytes, 10), headerOffset(bytes, 18),
                headerOffset(bytes, 26), headerOffset(bytes, 34));
    }

    private static long headerOffset(byte[] bytes, int position) {
        String field = new String(bytes, position, 8, StandardCharsets.US_ASCII).trim();
        if (field.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(field);
        } catch (NumberFormatException e) {
            throw new FcsFormatException("Invalid offset in the FCS header: " + field);
        }
    }

    /**
     * Splits a TEXT segment into keyword/value pairs. The first byte is the delimiter; a
     * delimiter doubled inside a keyword or a value stands for the character itself.
     */
    static Map<String, String> parseText(byte[] bytes, long start, long end) {
        if (start < HEADER_LENGTH || end < start || end >= bytes.length) {
            throw new FcsFormatException("The TEXT segment is outside the file");
        }
        String text = decode(bytes, (int) start, (int) (end - start + 1));
        char delimiter = text.charAt(0);
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        int i = 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == delimiter) {
                if (i + 1 < text.length() && text.charAt(i + 1) == delimiter) {
                    token.append(delimiter);
                    i += 2;
                    continue;
                }
                tokens.add(token.toString());
                token.setLength(0);
            } else {
                token.append(c);
            }
            i++;
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        Map<String, String> keywords = new LinkedHashMap<>();
        for (int k = 0; k + 1 < tokens.size(); k += 2) {
            keywords.put(tokens.get(k).trim().toUpperCase(Locale.ROOT), tokens.get(k + 1));
        }
        return keywords;
    }

    /** FCS 3.1 text is UTF-8; older files are ASCII, often with Latin-1 characters. */
    private static String decode(byte[] bytes, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
        }
    }

    private static float[][] readData(byte[] bytes, Map<String, String> keywords,
                                      List<Channel> channels, int eventCount,
                                      long dataStart, long dataEnd) {
        String type = keywords.getOrDefault("$DATATYPE", "").trim().toUpperCase(Locale.ROOT);
        int parameterCount = channels.size();
        int[] widths = new int[parameterCount];
        for (int p = 0; p < parameterCount; p++) {
            widths[p] = switch (type) {
                case "F" -> 32;
                case "D" -> 64;
                case "I" -> (int) requiredLong(keywords, "$P" + (p + 1) + "B");
                default -> throw new FcsFormatException(
                        "Unsupported data type: " + (type.isEmpty() ? "none" : type));
            };
            if (widths[p] != 8 && widths[p] != 16 && widths[p] != 32 && widths[p] != 64) {
                throw new FcsFormatException(
                        "Unsupported number of bits for parameter " + (p + 1) + ": " + widths[p]);
            }
        }
        long eventBytes = 0;
        for (int width : widths) {
            eventBytes += width / 8;
        }
        long needed = eventBytes * eventCount;
        if (dataStart < 0 || dataStart + needed > bytes.length
                || (dataEnd > 0 && dataStart + needed > dataEnd + 1)) {
            throw new FcsFormatException("The DATA segment is shorter than "
                    + eventCount + " events of " + parameterCount + " parameters");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes, (int) dataStart, (int) needed);
        buffer.order(byteOrder(keywords.getOrDefault("$BYTEORD", "1,2,3,4")));
        double[] masks = new double[parameterCount];
        double[][] amplification = new double[parameterCount][];
        double[] gains = new double[parameterCount];
        for (int p = 0; p < parameterCount; p++) {
            masks[p] = channels.get(p).range();
            amplification[p] = amplification(keywords.get("$P" + (p + 1) + "E"));
            gains[p] = doubleKeyword(keywords, "$P" + (p + 1) + "G", 1);
        }

        float[][] values = new float[parameterCount][eventCount];
        for (int e = 0; e < eventCount; e++) {
            for (int p = 0; p < parameterCount; p++) {
                double raw = switch (type) {
                    case "F" -> buffer.getFloat();
                    case "D" -> buffer.getDouble();
                    default -> integer(buffer, widths[p], masks[p]);
                };
                values[p][e] = (float) convert(raw, type, channels.get(p).range(),
                        amplification[p], gains[p]);
            }
        }
        return values;
    }

    private static ByteOrder byteOrder(String byteOrder) {
        String order = byteOrder.replace(" ", "");
        if (order.startsWith("1,2") || order.equals("1")) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        if (order.startsWith("4,3") || order.startsWith("2,1")
                || order.startsWith("8,7")) {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new FcsFormatException("Unsupported byte order: " + byteOrder);
    }

    /** Reads an unsigned integer and keeps only the bits below the range, as the standard says. */
    private static double integer(ByteBuffer buffer, int bits, double range) {
        long value = switch (bits) {
            case 8 -> buffer.get() & 0xFFL;
            case 16 -> buffer.getShort() & 0xFFFFL;
            case 32 -> buffer.getInt() & 0xFFFFFFFFL;
            default -> buffer.getLong();
        };
        long r = (long) range;
        if (r > 0 && (r & (r - 1)) == 0) {
            value &= r - 1;
        }
        return value;
    }

    private static double[] amplification(String value) {
        if (value == null) {
            return new double[] {0, 0};
        }
        String[] parts = value.split(",");
        try {
            double decades = Double.parseDouble(parts[0].trim());
            double offset = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 0;
            return new double[] {decades, offset};
        } catch (NumberFormatException e) {
            return new double[] {0, 0};
        }
    }

    /**
     * Converts a stored integer to a measured value: channels recorded on a logarithmic
     * amplifier span {@code decades} decades from {@code offset}; linear channels are divided by
     * their gain. Floating-point values are already measured values and are kept as they are.
     */
    private static double convert(double raw, String type, double range,
                                  double[] amplification, double gain) {
        if (!type.equals("I")) {
            return raw;
        }
        double decades = amplification[0];
        if (decades > 0) {
            double offset = amplification[1] > 0 ? amplification[1] : 1;
            return offset * Math.pow(10, decades * raw / range);
        }
        return gain > 0 ? raw / gain : raw;
    }

    private static long requiredLong(Map<String, String> keywords, String keyword) {
        String value = keywords.get(keyword);
        if (value == null) {
            throw new FcsFormatException("Missing keyword " + keyword);
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new FcsFormatException("Invalid value for " + keyword + ": " + value);
        }
    }

    private static long longKeyword(Map<String, String> keywords, String keyword, long fallback) {
        try {
            return Long.parseLong(keywords.getOrDefault(keyword, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleKeyword(Map<String, String> keywords, String keyword,
                                        double fallback) {
        try {
            return Double.parseDouble(keywords.getOrDefault(keyword, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
