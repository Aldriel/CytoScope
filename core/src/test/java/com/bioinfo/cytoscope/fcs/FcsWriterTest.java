package com.bioinfo.cytoscope.fcs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FcsWriterTest {

    static FcsSample sample() {
        return new FcsSample("tube",
                Map.of("$CYT", "Canto/II", "$P1B", "16", "CYTOSCOPE_GROUP", ""),
                List.of(new Channel("FSC-A", "", 262_144), new Channel("FITC-A", "Annexin V", 1e5),
                        new Channel("Time", "", 1024.5)),
                new float[][] {{1, 2, 3}, {-4.5f, 5, 6}, {0, 0.5f, 1}});
    }

    @Test
    void roundTripsThroughTheReader() {
        FcsSample read = FcsReader.read("copy", FcsWriter.write(sample()));
        assertEquals(3, read.eventCount());
        assertArrayEquals(new float[] {-4.5f, 5, 6}, read.column(1));
        assertEquals("Annexin V", read.channels().get(1).label());
        assertEquals(1024.5, read.channels().get(2).range());
        assertEquals("Canto/II", read.keyword("$CYT").orElseThrow());
        assertEquals("32", read.keyword("$P1B").orElseThrow());
        assertEquals("FCS3.1", new String(FcsWriter.write(sample()), 0, 6));
    }

    @Test
    void writesSelectedEventsOnly() {
        BitSet events = new BitSet();
        events.set(2);
        FcsSample read = FcsReader.read("copy", FcsWriter.write(sample(), events));
        assertArrayEquals(new float[] {3}, read.column(0));
        FcsSample empty = FcsReader.read("copy", FcsWriter.write(sample(), new BitSet()));
        assertEquals(0, empty.eventCount());
    }

    @Test
    void sampleAccessors() {
        FcsSample sample = sample();
        assertEquals(1, sample.indexOf("fitc-a"));
        assertEquals(1, sample.indexOf("annexin v"));
        assertEquals(-1, sample.indexOf("PE-A"));
        assertThrows(IllegalArgumentException.class, () -> sample.requireChannel("PE-A"));
        assertEquals("other", sample.withName("other").name());
        assertSame(sample.keywords(), sample.keywords());
        assertTrue(sample.keyword("$cyt").isPresent());
        float[] column = sample.column(0);
        column[0] = 99;
        assertEquals(1, sample.value(0, 0));
        assertEquals(7, sample.withValues(new float[][] {{7}, {8}, {9}}).value(0, 0));
        assertEquals(0, new FcsSample("e", Map.of(), List.of(), new float[0][]).eventCount());
        assertThrows(IllegalArgumentException.class, () -> new FcsSample("x", Map.of(),
                List.of(new Channel("A", "", 1)), new float[0][]));
        assertThrows(IllegalArgumentException.class, () -> new FcsSample("x", Map.of(),
                List.of(new Channel("A", "", 1), new Channel("B", "", 1)),
                new float[][] {{1}, {1, 2}}));
    }

    @Test
    void channelKinds() {
        Channel fitc = new Channel("FITC-A", "Annexin V", 1000);
        assertEquals("FITC-A (Annexin V)", fitc.displayName());
        assertEquals("FSC-A", new Channel("FSC-A", null, 1).displayName());
        assertEquals("PI-A", new Channel("PI-A", "PI-A", 1).displayName());
        assertTrue(fitc.isFluorescence());
        assertTrue(new Channel("SSC-H", "", 1).isScatter());
        assertTrue(new Channel("TIME", "", 1).isTime());
        assertFalse(new Channel("Time", "", 1).isFluorescence());
        assertThrows(IllegalArgumentException.class, () -> new Channel(" ", "", 1));
        assertThrows(IllegalArgumentException.class, () -> new Channel("A", "", 0));
    }
}
