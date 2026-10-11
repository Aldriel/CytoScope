package com.bioinfo.cytoscope.compensation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsReader;
import com.bioinfo.cytoscope.fcs.FcsSample;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompensationTest {

    private static FcsSample example(String file) throws IOException {
        try (InputStream in = CompensationTest.class.getResourceAsStream(
                "/com/bioinfo/cytoscope/examples/" + file)) {
            return FcsReader.read(file, in.readAllBytes());
        }
    }

    private static FcsSample withKeyword(String key, String value) {
        return new FcsSample("s", Map.of(key, value),
                List.of(new Channel("FITC-A", "", 1000), new Channel("PI-A", "", 1000)),
                new float[][] {{100, 0}, {12, 50}});
    }

    @Test
    void parsesAndFormats() {
        SpilloverMatrix m = SpilloverMatrix.parse("2, FITC-A ,PI-A,1,0.1,0.02,1");
        assertEquals(List.of("FITC-A", "PI-A"), m.channels());
        assertEquals(0.1, m.value(0, 1));
        assertEquals("2,FITC-A,PI-A,1.00000,0.100000,0.0200000,1.00000", m.format());
        assertEquals(m, SpilloverMatrix.parse(m.format()));
        assertEquals(m.hashCode(), SpilloverMatrix.parse(m.format()).hashCode());
        assertNotEquals(m, SpilloverMatrix.identity(List.of("FITC-A", "PI-A")));
        assertNotEquals(m, SpilloverMatrix.identity(List.of("A", "B")));
        assertNotEquals(m, "matrix");
        assertEquals(0.3, m.with(0, 1, 0.3).value(0, 1));
        assertEquals(0.1, m.value(0, 1));
    }

    @Test
    void rejectsInvalidMatrices() {
        assertThrows(IllegalArgumentException.class, () -> SpilloverMatrix.parse("x,A,1"));
        assertThrows(IllegalArgumentException.class, () -> SpilloverMatrix.parse("2,A,B,1,0"));
        assertThrows(IllegalArgumentException.class, () -> SpilloverMatrix.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> SpilloverMatrix.parse("1,A,z"));
        assertThrows(IllegalArgumentException.class,
                () -> new SpilloverMatrix(List.of(), new double[0][]));
        assertThrows(IllegalArgumentException.class,
                () -> new SpilloverMatrix(List.of("A", "B"), new double[][] {{1, 0}, {0}}));
        assertThrows(IllegalArgumentException.class,
                () -> new SpilloverMatrix(List.of("A"), new double[][] {{Double.NaN}}));
        assertThrows(IllegalArgumentException.class,
                () -> SpilloverMatrix.parse("2,A,B,1,1,1,1"));
    }

    @Test
    void invertsWithPivoting() {
        SpilloverMatrix m = new SpilloverMatrix(List.of("A", "B", "C"),
                new double[][] {{1, 0.2, 0.05}, {0.1, 1, 0.3}, {0, 0.15, 1}});
        double[][] inverse = m.inverse();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double product = 0;
                for (int k = 0; k < 3; k++) {
                    product += m.value(i, k) * inverse[k][j];
                }
                assertEquals(i == j ? 1 : 0, product, 1e-12);
            }
        }
        SpilloverMatrix swapped = new SpilloverMatrix(List.of("A", "B"),
                new double[][] {{0, 1}, {1, 0}});
        assertArrayEquals(new double[] {0, 1}, swapped.inverse()[0], 1e-12);
    }

    @Test
    void readsTheMatrixOfAFile() {
        assertEquals(0.4748, SpilloverMatrix.fromSample(withKeyword("SPILL",
                "2,FITC-A,PI-A,1,0.4748,0.34,1")).orElseThrow().value(0, 1), 1e-9);
        assertTrue(SpilloverMatrix.fromSample(withKeyword("$SPILLOVER",
                "2,FITC-A,PI-A,1,0.1,0,1")).isPresent());
        assertTrue(SpilloverMatrix.fromSample(withKeyword("SPILL",
                "2,FITC-A,PE-A,1,0.1,0,1")).isEmpty());
        assertTrue(SpilloverMatrix.fromSample(withKeyword("SPILL", "garbage")).isEmpty());
        assertTrue(SpilloverMatrix.fromSample(withKeyword("OTHER", "1")).isEmpty());
    }

    @Test
    void compensatesOnlyTheChannelsOfTheMatrix() {
        FcsSample sample = new FcsSample("s", Map.of(),
                List.of(new Channel("FSC-A", "", 1000), new Channel("FITC-A", "", 1000),
                        new Channel("PI-A", "", 1000)),
                new float[][] {{5, 6}, {100, 0}, {12, 50}});
        SpilloverMatrix m = SpilloverMatrix.parse("2,FITC-A,PI-A,1,0.12,0,1");
        FcsSample compensated = Compensator.apply(sample, m);
        assertArrayEquals(new float[] {5, 6}, compensated.column(0));
        assertArrayEquals(new float[] {100, 0}, compensated.column(1), 1e-4f);
        assertArrayEquals(new float[] {0, 50}, compensated.column(2), 1e-4f);
        assertThrows(IllegalArgumentException.class, () -> Compensator.apply(sample,
                SpilloverMatrix.identity(List.of("PE-A"))));
    }

    /** The simulated controls were made with FITC into PI 12 % and PI into FITC 2 %. */
    @Test
    void estimatesTheSimulatedSpilloverFromControls() throws IOException {
        Map<String, FcsSample> controls = new LinkedHashMap<>();
        controls.put("FITC-A", example("cadmium/Control_annexin_only.fcs"));
        controls.put("PI-A", example("cadmium/Control_PI_only.fcs"));
        FcsSample unstained = example("cadmium/Control_unstained.fcs");
        SpilloverMatrix withUnstained = SpilloverEstimator.estimate(controls, unstained);
        assertEquals(0.12, withUnstained.value(0, 1), 0.006);
        assertEquals(0.02, withUnstained.value(1, 0), 0.006);
        assertEquals(1, withUnstained.value(0, 0));
        SpilloverMatrix alone = SpilloverEstimator.estimate(controls, null);
        assertEquals(0.12, alone.value(0, 1), 0.01);
        assertEquals(0.02, alone.value(1, 0), 0.01);
    }

    @Test
    void refusesADimControl() throws IOException {
        FcsSample unstained = example("cadmium/Control_unstained.fcs");
        Map<String, FcsSample> controls = new LinkedHashMap<>();
        controls.put("FITC-A", unstained);
        controls.put("PI-A", unstained);
        assertThrows(IllegalArgumentException.class,
                () -> SpilloverEstimator.estimate(controls, unstained));
    }
}
