package com.bioinfo.cytoscope.experiment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.stats.Dunnett;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExperimentTest {

    private static final double[] DOSES = {0, 1, 3, 10, 30, 100};

    /** Builds a dose series: two tubes per dose, the share of apoptotic cells rising. */
    private static Map<String, FcsSample> series(Experiment[] experiment) {
        Map<String, FcsSample> tubes = new LinkedHashMap<>();
        Experiment exp = Experiment.empty("All").withGating(Tubes.gating())
                .withApoptosis(Tubes.apoptosis());
        for (double dose : DOSES) {
            int apoptotic = (int) Math.round(10 + 60 / (1 + Math.pow(5 / dose, 1.5)));
            for (int rep = 0; rep < 2; rep++) {
                String id = "d" + dose + "r" + rep;
                tubes.put(id, Tubes.tube(id, apoptotic + rep, Map.of()));
                exp = exp.withSample(new SampleInfo(id, id, SampleRole.SAMPLE,
                        groupName(dose), dose, ""));
            }
        }
        experiment[0] = exp;
        return tubes;
    }

    private static String groupName(double dose) {
        return (dose == Math.rint(dose) ? String.valueOf((int) dose) : String.valueOf(dose))
                + " µM";
    }

    @Test
    void comparesGroupsAndFitsTheDoseResponse() {
        Experiment[] holder = new Experiment[1];
        Map<String, FcsSample> tubes = series(holder);
        Experiment experiment = holder[0].withComparison(Metric.apoptotic(), "0 µM");
        ExperimentResults results = ExperimentAnalyzer.analyze(experiment, tubes::get);

        assertEquals(12, results.samples().size());
        SampleResult first = results.samples().get(0);
        assertEquals(101, first.eventCount());
        assertEquals(10, first.value(Metric.apoptotic()).orElseThrow(), 1e-9);
        assertEquals(100.0 * 100 / 101, first.value(Metric.population("p1")).orElseThrow(),
                1e-9);
        assertEquals(90, first.value(Metric.fate(CellFate.LIVE)).orElseThrow(), 1e-9);
        assertTrue(first.value(Metric.population("p9")).isEmpty());
        assertTrue(first.value(Metric.phase(CellCyclePhase.S)).isEmpty());

        GroupComparison comparison = results.comparison();
        assertNull(results.comparisonIssue());
        assertEquals("0 µM", comparison.controlGroup());
        assertEquals(6, comparison.groups().size());
        assertEquals("", comparison.groups().get(0).stars());
        assertEquals("***", comparison.groups().get(5).stars());
        assertArrayEquals(new double[] {10, 11}, comparison.groups().get(0).values());
        assertEquals(10.5, comparison.groups().get(0).mean(), 1e-12);
        assertTrue(comparison.anova().pValue() < 1e-6);

        DoseResponse curve = results.doseResponse();
        assertNull(results.doseResponseIssue());
        assertEquals(12, curve.points().size());
        assertEquals(0, curve.points().get(0).dose());
        assertEquals(5, curve.fit().ec50(), 1);
        assertEquals(10.5, curve.fit().bottom(), 1.5);
        assertEquals(70.5, curve.fit().top(), 1.5);
    }

    @Test
    void reportsWhyTheComparisonOrTheCurveIsMissing() {
        Experiment[] holder = new Experiment[1];
        Map<String, FcsSample> tubes = series(holder);
        Experiment base = holder[0];

        ExperimentResults none = ExperimentAnalyzer.analyze(base, tubes::get);
        assertEquals(AnalysisIssue.NO_METRIC, none.comparisonIssue());
        assertEquals(AnalysisIssue.NO_METRIC, none.doseResponseIssue());

        ExperimentResults noControl = ExperimentAnalyzer.analyze(
                base.withComparison(Metric.apoptotic(), ""), tubes::get);
        assertEquals(AnalysisIssue.NO_CONTROL_GROUP, noControl.comparisonIssue());
        assertTrue(noControl.doseResponse() != null);

        ExperimentResults unknownControl = ExperimentAnalyzer.analyze(
                base.withComparison(Metric.apoptotic(), "PBS"), tubes::get);
        assertEquals(AnalysisIssue.NO_CONTROL_GROUP, unknownControl.comparisonIssue());

        ExperimentResults unavailable = ExperimentAnalyzer.analyze(
                base.withComparison(Metric.phase(CellCyclePhase.G2_M), "0 µM"), tubes::get);
        assertEquals(AnalysisIssue.METRIC_UNAVAILABLE, unavailable.comparisonIssue());
        assertEquals(AnalysisIssue.METRIC_UNAVAILABLE, unavailable.doseResponseIssue());

        Experiment twoTubes = base;
        for (SampleInfo info : base.samples()) {
            if (!info.id().equals("d0.0r0") && !info.id().equals("d100.0r0")) {
                twoTubes = twoTubes.withoutSample(info.id());
            }
        }
        ExperimentResults single = ExperimentAnalyzer.analyze(
                twoTubes.withComparison(Metric.apoptotic(), "0 µM"), tubes::get);
        assertEquals(AnalysisIssue.TOO_FEW_REPLICATES, single.comparisonIssue());
        assertEquals(AnalysisIssue.TOO_FEW_DOSES, single.doseResponseIssue());

        Experiment controlOnly = twoTubes.withoutSample("d100.0r0")
                .withComparison(Metric.apoptotic(), "0 µM");
        assertEquals(AnalysisIssue.TOO_FEW_GROUPS,
                ExperimentAnalyzer.analyze(controlOnly, tubes::get).comparisonIssue());

        Experiment empty = Experiment.empty("All").withComparison(Metric.apoptotic(), "NC");
        ExperimentResults nothing = ExperimentAnalyzer.analyze(empty, id -> null);
        assertEquals(AnalysisIssue.METRIC_UNAVAILABLE, nothing.comparisonIssue());
        assertEquals(AnalysisIssue.TOO_FEW_DOSES, nothing.doseResponseIssue());
    }

    @Test
    void analyzesTheCellCycleOfSamplesOnly() {
        FcsSample tube = Tubes.tube("t", 10, Map.of());
        Experiment experiment = Experiment.empty("All").withGating(Tubes.gating())
                .withCellCycle(new CellCycleSettings("p1", "PI-A"));
        SampleInfo sample = new SampleInfo("t", "t", SampleRole.SAMPLE, "NC", 0, "");
        SampleResult result = ExperimentAnalyzer.analyzeSample(experiment, sample, tube);
        assertNull(result.cellCycle());
        assertEquals(AnalysisIssue.NO_G1_PEAK, result.cellCycleIssue());

        SampleInfo unstained = sample.withRole(SampleRole.UNSTAINED, "");
        SampleResult control = ExperimentAnalyzer.analyzeSample(experiment, unstained, tube);
        assertNull(control.cellCycle());
        assertNull(control.cellCycleIssue());

        Experiment missingParents = experiment
                .withCellCycle(new CellCycleSettings("p9", "PI-A"))
                .withApoptosis(new ApoptosisSettings("p9", "FITC-A", "PI-A", 1, 1));
        SampleResult none = ExperimentAnalyzer.analyzeSample(missingParents, sample, tube);
        assertNull(none.apoptosis());
        assertNull(none.cellCycleIssue());
        assertEquals(2, none.populations().size());
    }

    @Test
    void experimentIsImmutable() {
        Experiment empty = Experiment.empty("All");
        SampleInfo nc = new SampleInfo("a", "NC 1", SampleRole.SAMPLE, "NC", Double.NaN, "");
        SampleInfo tmz = new SampleInfo("b", "TMZ 1", SampleRole.SAMPLE, "TMZ", Double.NaN, "");
        SampleInfo blank = new SampleInfo("c", "Blank", SampleRole.UNSTAINED, "", Double.NaN,
                null);
        Experiment experiment = empty.withSample(tmz).withSample(nc).withSample(blank)
                .withSample(new SampleInfo("d", "TMZ 2", SampleRole.SAMPLE, "x", Double.NaN, "")
                        .withGroup("TMZ").withDose(1).withRole(SampleRole.SAMPLE, "x"));
        assertEquals(List.of("TMZ", "NC"), experiment.groups());
        assertEquals(Optional.of(blank), experiment.sample("c"));
        assertTrue(experiment.sample("z").isEmpty());
        assertEquals(0, empty.samples().size());
        assertEquals("TMZ", experiment.withSampleChanged("a", s -> s.withGroup("TMZ"))
                .sample("a").orElseThrow().group());
        assertEquals(3, experiment.withoutSample("a").samples().size());

        SpilloverMatrix matrix = SpilloverMatrix.identity(List.of("FITC-A", "PI-A"));
        Experiment compensated = experiment.withCompensation(CompensationSource.FILE, matrix);
        assertSame(matrix, compensated.matrix());
        assertThrows(IllegalArgumentException.class,
                () -> experiment.withCompensation(CompensationSource.FILE, null));
        assertThrows(IllegalArgumentException.class,
                () -> experiment.withCompensation(CompensationSource.NONE, matrix));
        assertThrows(IllegalArgumentException.class, () -> experiment.withSample(nc));
        Experiment noControl = new Experiment(List.of(), CompensationSource.NONE, null,
                Tubes.gating(), null, null, null, null);
        assertEquals("", noControl.controlGroup());
    }

    @Test
    void sampleInfoAndMetrics() {
        SampleInfo info = new SampleInfo("a", "PI", SampleRole.SINGLE_STAIN, null, Double.NaN,
                " PI-A ");
        assertEquals("", info.group());
        assertEquals("PI-A", info.stainChannel());
        assertFalse(info.isCompared());
        assertEquals("PI-A", info.withRole(SampleRole.SINGLE_STAIN, "PI-A").stainChannel());
        assertEquals("", info.withRole(SampleRole.UNSTAINED, "PI-A").stainChannel());
        assertFalse(info.withRole(SampleRole.SAMPLE, "").isCompared());
        assertTrue(info.withRole(SampleRole.SAMPLE, "").withGroup("NC").isCompared());
        assertThrows(IllegalArgumentException.class, () -> info.withDose(-1));
        assertThrows(NullPointerException.class,
                () -> new SampleInfo(null, "n", SampleRole.SAMPLE, "", 0, ""));

        assertEquals("", new Metric(Metric.Kind.APOPTOTIC, "x").target());
        assertEquals("G2_M", Metric.phase(CellCyclePhase.G2_M).target());
        assertEquals(Metric.Kind.CELL_FATE, Metric.fate(CellFate.NECROTIC).kind());
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(Metric.Kind.CELL_FATE, "DEAD"));
        assertThrows(IllegalArgumentException.class,
                () -> new Metric(Metric.Kind.CELL_CYCLE, null));
        assertThrows(IllegalArgumentException.class, () -> Metric.population(" "));
        assertThrows(NullPointerException.class, () -> new Metric(null, ""));
    }

    @Test
    void starsFollowTheAdjustedP() {
        assertEquals("***", summary(0.0009).stars());
        assertEquals("**", summary(0.009).stars());
        assertEquals("*", summary(0.049).stars());
        assertEquals("", summary(0.05).stars());
        double[] values = {1, 2};
        GroupComparison.GroupSummary summary = new GroupComparison.GroupSummary("g", values, 1.5,
                0.7, null);
        values[0] = 9;
        assertEquals(1, summary.values()[0]);
        summary.values()[1] = 9;
        assertEquals(2, summary.values()[1]);
        Map<String, double[]> groups = new HashMap<>();
        groups.put("NC", new double[] {1, 2});
        assertThrows(IllegalArgumentException.class, () -> GroupComparison.of(groups, "PBS"));
        assertThrows(IllegalArgumentException.class, () -> GroupComparison.of(groups, "NC"));
    }

    private static GroupComparison.GroupSummary summary(double p) {
        return new GroupComparison.GroupSummary("g", new double[] {1}, 1, 0,
                new Dunnett.Comparison(1, 1, 1, p));
    }

    @Test
    void sampleStoreReportsMissingTubes() {
        FcsSample tube = Tubes.tube("t", 0, Map.of());
        SampleStore store = id -> id.equals("t") ? Optional.of(tube) : Optional.empty();
        assertSame(tube, store.require("t"));
        MissingSampleException e = assertThrows(MissingSampleException.class,
                () -> store.require("u"));
        assertEquals("u", e.sampleId());
        assertTrue(e.getMessage().contains("u"));
    }
}
