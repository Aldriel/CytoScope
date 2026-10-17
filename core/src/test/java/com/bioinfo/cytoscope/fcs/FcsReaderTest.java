package com.bioinfo.cytoscope.fcs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FcsReaderTest {

    static byte[] resource(String path) throws IOException {
        try (InputStream in = FcsReaderTest.class.getResourceAsStream(
                "/com/bioinfo/cytoscope/examples/" + path)) {
            return in.readAllBytes();
        }
    }

    /** Values checked against flowio 1.3 (as_array without preprocessing). */
    @Test
    void readsBigEndianFloatFileLikeFlowio() throws IOException {
        FcsSample sample = FcsReader.read("NC 1",
                resource("u87-apoptosis/081120_Tube_001 NC 1.fcs"));
        assertEquals(10_000, sample.eventCount());
        assertEquals(List.of("FSC-A", "SSC-A", "FITC-A", "PI-A", "Time"),
                sample.channels().stream().map(Channel::name).toList());
        assertEquals(154023.421875, sample.value(0, 0), 1e-3);
        assertEquals(638.719970703125, sample.value(3, 0), 1e-4);
        assertEquals(1022.9000244140625, sample.value(4, 9_999), 1e-4);
        double[] sums = {1244884595.3305664, 894711771.1004639, 11909729.010984898,
            11932868.849488735, 5140418.198113039};
        for (int c = 0; c < sums.length; c++) {
            double sum = 0;
            for (float v : sample.column(c)) {
                sum += v;
            }
            assertEquals(sums[c], sum, Math.abs(sums[c]) * 1e-9);
        }
        assertTrue(sample.keyword("spill").orElseThrow().startsWith("2,FITC-A,PI-A"));
    }

    @Test
    void readsCellCycleFileLikeFlowio() throws IOException {
        FcsSample sample = FcsReader.read("NC 1",
                resource("u87-cell-cycle/071220_1 NC 1.fcs"));
        assertEquals(20_000, sample.eventCount());
        assertEquals(6, sample.channels().size());
        assertEquals(63002.50390625, sample.value(sample.indexOf("PI-W"), 0), 1e-3);
        assertEquals(-20.479999542236328, sample.value(sample.indexOf("PI-A"), 19_999), 1e-4);
    }

    private static byte[] ints(ByteOrder order, int bytesPerValue, long... values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * bytesPerValue).order(order);
        for (long v : values) {
            switch (bytesPerValue) {
                case 1 -> buffer.put((byte) v);
                case 2 -> buffer.putShort((short) v);
                case 4 -> buffer.putInt((int) v);
                default -> buffer.putLong(v);
            }
        }
        return buffer.array();
    }

    private static FcsBytes twoParameters(String bits) {
        return new FcsBytes().version("FCS2.0").keyword("$MODE", "L").keyword("$PAR", "2")
                .keyword("$TOT", "3").keyword("$DATATYPE", "I")
                .keyword("$P1N", "FSC-H").keyword("$P1B", bits).keyword("$P1R", "1024")
                .keyword("$P2N", "FL1-H").keyword("$P2S", "CD4").keyword("$P2B", bits)
                .keyword("$P2R", "1024");
    }

    @Test
    void readsLittleEndianIntegersAndAppliesTheRangeMask() {
        byte[] bytes = twoParameters("16").keyword("$BYTEORD", "1,2")
                .data(ints(ByteOrder.LITTLE_ENDIAN, 2, 10, 20, 1024 + 30, 40, 50, 60)).build();
        FcsSample sample = FcsReader.read("t", bytes);
        assertArrayEquals(new float[] {10, 30, 50}, sample.column(0));
        assertArrayEquals(new float[] {20, 40, 60}, sample.column(1));
        assertEquals("CD4", sample.channels().get(1).label());
    }

    @Test
    void readsBigEndian8And32BitIntegers() {
        FcsSample eight = FcsReader.read("t", twoParameters("8").keyword("$BYTEORD", "2,1")
                .data(ints(ByteOrder.BIG_ENDIAN, 1, 1, 2, 3, 4, 255, 6)).build());
        assertArrayEquals(new float[] {1, 3, 255}, eight.column(0));
        FcsSample thirtyTwo = FcsReader.read("t", twoParameters("32")
                .keyword("$BYTEORD", "4,3,2,1")
                .data(ints(ByteOrder.BIG_ENDIAN, 4, 7, 8, 9, 10, 11, 12)).build());
        assertArrayEquals(new float[] {8, 10, 12}, thirtyTwo.column(1));
    }

    @Test
    void reads64BitIntegersAndDoubles() {
        FcsSample longs = FcsReader.read("t", twoParameters("64").keyword("$BYTEORD", "1,2,3,4")
                .data(ints(ByteOrder.LITTLE_ENDIAN, 8, 1, 2, 3, 4, 5, 6)).build());
        assertArrayEquals(new float[] {2, 4, 6}, longs.column(1));
        ByteBuffer doubles = ByteBuffer.allocate(48).order(ByteOrder.BIG_ENDIAN);
        for (double v : new double[] {1.5, -2.5, 3.5, 4.5, 5.5, 6.5}) {
            doubles.putDouble(v);
        }
        FcsSample sample = FcsReader.read("t", twoParameters("64").keyword("$DATATYPE", "D")
                .keyword("$BYTEORD", "4,3,2,1").data(doubles.array()).build());
        assertArrayEquals(new float[] {-2.5f, 4.5f, 6.5f}, sample.column(1));
    }

    @Test
    void appliesLogAmplificationAndGain() {
        byte[] bytes = twoParameters("16").keyword("$BYTEORD", "1,2")
                .keyword("$P1E", "4,1").keyword("$P2E", "0,0").keyword("$P2G", "2")
                .data(ints(ByteOrder.LITTLE_ENDIAN, 2, 0, 20, 512, 40, 1023, 60)).build();
        FcsSample sample = FcsReader.read("t", bytes);
        assertEquals(1, sample.value(0, 0), 1e-6);
        assertEquals(100, sample.value(0, 1), 1e-3);
        assertEquals(Math.pow(10, 4 * 1023 / 1024.0), sample.value(0, 2), 1);
        assertArrayEquals(new float[] {10, 20, 30}, sample.column(1));
    }

    @Test
    void logAmplificationWithZeroOffsetUsesOneAndBadValuesAreIgnored() {
        byte[] bytes = twoParameters("16").keyword("$BYTEORD", "1,2")
                .keyword("$P1E", "2,0").keyword("$P2E", "x,y").keyword("$P2R", "1000")
                .data(ints(ByteOrder.LITTLE_ENDIAN, 2, 512, 20, 0, 40, 0, 999)).build();
        FcsSample sample = FcsReader.read("t", bytes);
        assertEquals(10, sample.value(0, 0), 1e-4);
        assertEquals(999, sample.value(1, 2));
    }

    @Test
    void readsDataOffsetsFromTextAndDoubledDelimiters() {
        ByteBuffer floats = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        floats.putFloat(1.25f).putFloat(2.5f);
        byte[] bytes = new FcsBytes().version("FCS3.1").delimiter('|')
                .keyword("$PAR", "1").keyword("$TOT", "2").keyword("$DATATYPE", "F")
                .keyword("$BYTEORD", "1,2,3,4").keyword("$P1N", "FITC-A")
                .keyword("$P1S", "a|b").keyword("$P1R", "262144").keyword("$FIL", "x.fcs")
                .dataOffsetsInTextOnly().data(floats.array()).build();
        FcsSample sample = FcsReader.read("t", bytes);
        assertArrayEquals(new float[] {1.25f, 2.5f}, sample.column(0));
        assertEquals("a|b", sample.channels().get(0).label());
        assertTrue(sample.keyword("$fil").isEmpty());
    }

    @Test
    void dropsKeywordsThatCanNameAPerson() {
        byte[] bytes = new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "0")
                .keyword("$DATATYPE", "F").keyword("$P1N", "FITC-A").keyword("$P1S", "CD4")
                .keyword("$SPILLOVER", "1,FITC-A,1").keyword("SPILL", "1,FITC-A,1")
                .keyword("CYTOSCOPE_GROUP", "TMZ")
                .keyword("$SRC", "Jane Doe").keyword("$OP", "A. Martin")
                .keyword("$INST", "City Hospital").keyword("$CYT", "Canto")
                .keyword("PATIENT NAME", "Jane Doe").keyword("TUBE NAME", "Jane Doe")
                .build();
        FcsSample sample = FcsReader.read("sample-04", bytes);
        assertEquals("sample-04", sample.name());
        assertEquals("CD4", sample.channels().get(0).label());
        assertEquals("1,FITC-A,1", sample.keyword("$SPILLOVER").orElseThrow());
        assertEquals("1,FITC-A,1", sample.keyword("SPILL").orElseThrow());
        assertEquals("TMZ", sample.keyword("CYTOSCOPE_GROUP").orElseThrow());
        assertEquals("Canto", sample.keyword("$CYT").orElseThrow());
        assertTrue(sample.keyword("$SRC").isEmpty());
        assertTrue(sample.keyword("$OP").isEmpty());
        assertTrue(sample.keyword("$INST").isEmpty());
        assertTrue(sample.keyword("PATIENT NAME").isEmpty());
        assertTrue(sample.keyword("TUBE NAME").isEmpty());
    }

    @Test
    void decodesLatin1TextWhenItIsNotUtf8() {
        byte[] bytes = new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "0")
                .keyword("$DATATYPE", "F").keyword("$P1N", "FL1").keyword("$P1S", "Cellules ré")
                .charset(StandardCharsets.ISO_8859_1).build();
        assertEquals("Cellules ré", FcsReader.read("t", bytes).channels().get(0).label());
    }

    @Test
    void mergesSupplementalText() {
        FcsBytes builder = new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "0")
                .keyword("$DATATYPE", "F").keyword("$P1N", "FL1");
        byte[] base = builder.build();
        byte[] extra = "/EXTRA/yes/".getBytes(StandardCharsets.US_ASCII);
        builder.keyword("$BEGINSTEXT", String.format("%05d", base.length + 32))
                .keyword("$ENDSTEXT", String.format("%05d", base.length + 32 + extra.length - 1));
        byte[] withPointers = builder.build();
        int start = withPointers.length;
        builder.keyword("$BEGINSTEXT", String.format("%05d", start))
                .keyword("$ENDSTEXT", String.format("%05d", start + extra.length - 1));
        byte[] file = builder.build();
        byte[] all = new byte[file.length + extra.length];
        System.arraycopy(file, 0, all, 0, file.length);
        System.arraycopy(extra, 0, all, file.length, extra.length);
        assertEquals("yes", FcsReader.read("t", all).keyword("EXTRA").orElseThrow());
    }

    @Test
    void rejectsInvalidFiles() {
        assertThrows(FcsFormatException.class, () -> FcsReader.read("t", new byte[10]));
        byte[] notFcs = new byte[100];
        notFcs[0] = 'P';
        assertThrows(FcsFormatException.class, () -> FcsReader.read("t", notFcs));
        FcsBytes ok = new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "1")
                .keyword("$DATATYPE", "F").keyword("$P1N", "A").data(new byte[4]);
        FcsReader.read("t", ok.build());
        assertFails(new FcsBytes().keyword("$MODE", "H").keyword("$PAR", "1"));
        assertFails(new FcsBytes().keyword("$TOT", "1"));
        assertFails(new FcsBytes().keyword("$PAR", "x").keyword("$TOT", "1"));
        assertFails(new FcsBytes().keyword("$PAR", "0").keyword("$TOT", "1"));
        assertFails(new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "1")
                .keyword("$DATATYPE", "F").keyword("$P1N", " "));
        assertFails(new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "1")
                .keyword("$DATATYPE", "A").keyword("$P1N", "A").data(new byte[4]));
        assertFails(new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "1")
                .keyword("$DATATYPE", "I").keyword("$P1B", "12").keyword("$P1N", "A")
                .data(new byte[4]));
        assertFails(new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "2")
                .keyword("$DATATYPE", "F").keyword("$P1N", "A").data(new byte[4]));
        assertFails(new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "1")
                .keyword("$BYTEORD", "3,1,2,4").keyword("$DATATYPE", "F")
                .keyword("$P1N", "A").data(new byte[4]));
        byte[] badOffset = ok.build();
        badOffset[12] = 'x';
        assertThrows(FcsFormatException.class, () -> FcsReader.read("t", badOffset));
        byte[] outside = ok.build();
        System.arraycopy("99999999".getBytes(StandardCharsets.US_ASCII), 0, outside, 18, 8);
        assertThrows(FcsFormatException.class, () -> FcsReader.read("t", outside));
    }

    @Test
    void headerWithBlankOffsetsUsesText() {
        byte[] bytes = new FcsBytes().keyword("$PAR", "1").keyword("$TOT", "1")
                .keyword("$DATATYPE", "F").keyword("$P1N", "A").dataOffsetsInTextOnly()
                .data(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(3f).array())
                .build();
        for (int i = 26; i < 42; i++) {
            bytes[i] = ' ';
        }
        assertEquals(3f, FcsReader.read("t", bytes).value(0, 0));
    }

    private static void assertFails(FcsBytes builder) {
        assertThrows(FcsFormatException.class, () -> FcsReader.read("t", builder.build()));
    }
}
