package com.bioinfo.cytoscope.analysis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingEngine;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.GatingTree;
import com.bioinfo.cytoscope.gating.IntervalGate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AnalysisTest {

    private static final double RANGE = 262_144;

    /** DNA values: sub-G1, G0/G1 at 50 000 (CV 3 %), S, and G2/M at 100 000. */
    static double[] cycle(double g1Share, double sShare, double g2Share, long seed) {
        Random random = new Random(seed);
        double[] dna = new double[20_000];
        for (int i = 0; i < dna.length; i++) {
            double u = random.nextDouble();
            if (u < g1Share) {
                dna[i] = 50_000 + random.nextGaussian() * 1_500;
            } else if (u < g1Share + sShare) {
                dna[i] = 58_000 + random.nextDouble() * 34_000;
            } else if (u < g1Share + sShare + g2Share) {
                dna[i] = 100_000 + random.nextGaussian() * 3_000;
            } else {
                dna[i] = 12_000 + random.nextDouble() * 25_000;
            }
        }
        return dna;
    }

    @Test
    void findsThePhasesOfAnAsynchronousPopulation() {
        CellCycleResult r = CellCycleAnalysis.analyze("t", cycle(0.6, 0.25, 0.12, 1), RANGE);
        assertEquals(50_000, r.g1Peak(), 300);
        assertEquals(100_000, r.g2Peak(), 1_000);
        assertEquals(3, r.g1Cv(), 0.3);
        assertEquals(2, r.g2g1Ratio(), 0.03);
        assertEquals(60, r.percent(CellCyclePhase.G0_G1), 2);
        assertEquals(25, r.percent(CellCyclePhase.S), 3);
        assertEquals(12, r.percent(CellCyclePhase.G2_M), 2);
        assertEquals(3, r.percent(CellCyclePhase.SUB_G1), 1);
        assertEquals(20_000, r.total());
        assertEquals(4, r.bounds().length);
        assertEquals(r, new CellCycleResult("t", r.g1Peak(), r.g2Peak(), r.g1Cv(), r.bounds(),
                r.counts()));
        assertEquals(r.hashCode(), new CellCycleResult("t", r.g1Peak(), r.g2Peak(), r.g1Cv(),
                r.bounds(), r.counts()).hashCode());
        assertNotEquals(r, "result");
        assertTrue(r.toString().contains("G1"));
    }

    @Test
    void recognizesAG2Arrest() {
        CellCycleResult r = CellCycleAnalysis.analyze("t", cycle(0.2, 0.15, 0.62, 2), RANGE);
        assertEquals(50_000, r.g1Peak(), 500);
        assertEquals(62, r.percent(CellCyclePhase.G2_M), 3);
    }

    @Test
    void assumesG2AtTwiceG1WithoutAG2Peak() {
        CellCycleResult r = CellCycleAnalysis.analyze("t", cycle(1, 0, 0, 3), RANGE);
        assertEquals(2 * r.g1Peak(), r.g2Peak(), 1e-9);
        assertEquals(100, r.percent(CellCyclePhase.G0_G1), 1.5);
        double[] same = new double[30];
        Arrays.fill(same, 50_000);
        CellCycleResult narrow = CellCycleAnalysis.analyze("t", same, RANGE);
        assertEquals(0.5, narrow.g1Cv(), 1e-9);
    }

    @Test
    void refusesTooFewCells() {
        assertThrows(IllegalArgumentException.class,
                () -> CellCycleAnalysis.analyze("t", new double[] {50_000, 60_000}, RANGE));
        assertThrows(IllegalArgumentException.class, () -> new CellCycleResult("t", 1, 2, 3,
                new double[3], new int[5]));
        assertThrows(IllegalArgumentException.class, () -> new CellCycleResult("t", 1, 2, 3,
                new double[4], new int[4]));
        CellCycleResult empty = new CellCycleResult("t", 1, 2, 3, new double[4], new int[5]);
        assertTrue(Double.isNaN(empty.percent(CellCyclePhase.S)));
    }

    @Test
    void ignoresValuesOutsideTheRange() {
        double[] histogram = CellCycleAnalysis.histogram(new double[] {-5, 0, RANGE, 1e9}, RANGE);
        assertEquals(1, histogram[0]);
        assertEquals(1, Arrays.stream(histogram).sum());
    }

    @Test
    void analyzesAGatedPopulation() {
        double[] dna = cycle(0.6, 0.25, 0.12, 4);
        float[] values = new float[dna.length];
        float[] fsc = new float[dna.length];
        for (int i = 0; i < dna.length; i++) {
            values[i] = (float) dna[i];
            fsc[i] = i % 2 == 0 ? 500 : 50;
        }
        FcsSample sample = new FcsSample("tube", Map.of(),
                List.of(new Channel("FSC-A", "", 1000), new Channel("PI-A", "", RANGE)),
                new float[][] {fsc, values});
        GatingTree tree = GatingTree.create("All").add("all", "Cells",
                new IntervalGate("FSC-A", 100, 1000));
        GatingResult gating = GatingEngine.apply(sample, tree);
        CellCycleResult r = CellCycleAnalysis.analyze(gating, new CellCycleSettings("p1", "PI-A"));
        assertEquals(10_000, r.total());
        assertEquals("tube", r.sampleName());
        assertThrows(NullPointerException.class, () -> new CellCycleSettings(null, "PI-A"));
        assertThrows(NullPointerException.class, () -> new CellCycleSettings("p1", null));
    }

    @Test
    void readsTheAnnexinTest() {
        FcsSample sample = new FcsSample("tube", Map.of(),
                List.of(new Channel("FITC-A", "Annexin V", 1000), new Channel("PI-A", "", 1000)),
                new float[][] {{10, 500, 500, 10, 500}, {10, 10, 500, 500, 499}});
        GatingResult gating = GatingEngine.apply(sample, GatingTree.create("All"));
        ApoptosisResult r = ApoptosisAnalysis.analyze(gating,
                new ApoptosisSettings("all", "Annexin V", "PI-A", 100, 500));
        assertEquals(1, r.count(CellFate.LIVE));
        assertEquals(2, r.count(CellFate.EARLY_APOPTOTIC));
        assertEquals(1, r.count(CellFate.LATE_APOPTOTIC));
        assertEquals(1, r.count(CellFate.NECROTIC));
        assertEquals(60, r.percentApoptotic(), 1e-12);
        assertEquals(5, r.total());
        assertEquals("tube", r.sampleName());
        assertTrue(Double.isNaN(new ApoptosisResult("e", new int[4]).percent(CellFate.LIVE)));
        assertThrows(IllegalArgumentException.class, () -> new ApoptosisResult("e", new int[3]));
    }

    @Test
    void apoptosisSettingsAndThresholds() {
        ApoptosisSettings settings = new ApoptosisSettings("p1", "FITC-A", "PI-A", 1, 2);
        assertEquals(new ApoptosisSettings("p1", "FITC-A", "PI-A", 3, 4),
                settings.withThresholds(3, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new ApoptosisSettings("p1", "PI-A", "PI-A", 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ApoptosisSettings("p1", "FITC-A", "PI-A", Double.NaN, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ApoptosisSettings("p1", "FITC-A", "PI-A", 1, Double.NaN));
        double[] control = new double[101];
        for (int i = 0; i < control.length; i++) {
            control[i] = i;
        }
        assertEquals(99, ApoptosisAnalysis.thresholdFromControl(control), 1e-12);
        assertArrayEquals(new CellFate[] {CellFate.LIVE, CellFate.EARLY_APOPTOTIC,
            CellFate.LATE_APOPTOTIC, CellFate.NECROTIC}, new CellFate[] {
                CellFate.of(false, false), CellFate.of(true, false), CellFate.of(true, true),
                CellFate.of(false, true)});
    }
}
