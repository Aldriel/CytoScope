package com.bioinfo.cytoscope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.MissingSampleException;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.experiment.Tubes;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.fcs.FcsWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceTest {

    private static final SpilloverMatrix MATRIX = new SpilloverMatrix(
            List.of("FITC-A", "PI-A"), new double[][] {{1, 0.1}, {0, 1}});

    private final CytoService service = new CytoService();

    @Test
    void readsFilesAndStripsTheExtension() {
        FcsSample tube = Tubes.tube("t", 5, Map.of());
        FcsSample read = service.read("NC 1.FCS", FcsWriter.write(tube));
        assertEquals("NC 1", read.name());
        assertEquals(101, read.eventCount());
        assertEquals("other.lmd", service.read("other.lmd.lmd", FcsWriter.write(tube)).name());
    }

    @Test
    void addingTubesTurnsOnTheFileMatrixAndFindsTheControlGroup() {
        Experiment experiment = Experiment.empty("All");
        experiment = service.addSample(experiment, "a", Tubes.tube("TMZ 1", 0, Map.of()));
        assertEquals(CompensationSource.NONE, experiment.compensation());
        assertEquals("", experiment.controlGroup());

        experiment = service.addSample(experiment, "b", Tubes.tube("DMSO 1", 0,
                Map.of("$SPILLOVER", MATRIX.format())));
        assertEquals(CompensationSource.FILE, experiment.compensation());
        assertEquals(MATRIX, experiment.matrix());
        assertEquals("DMSO", experiment.controlGroup());

        SpilloverMatrix other = MATRIX.with(0, 1, 0.3);
        experiment = service.addSample(experiment, "c", Tubes.tube("NC 1", 0,
                Map.of("$SPILLOVER", other.format())));
        assertEquals(MATRIX, experiment.matrix());
        assertEquals("DMSO", experiment.controlGroup());

        Experiment byDose = service.addSample(Experiment.empty("All"), "d",
                Tubes.tube("Cd_0uM_1", 0, Map.of()));
        assertEquals("CD 0UM", byDose.controlGroup());
        Experiment unstained = service.addSample(Experiment.empty("All"), "e",
                Tubes.tube("Unstained", 0, Map.of()));
        assertEquals("", unstained.controlGroup());
    }

    @Test
    void compensatesOnceAndGates() {
        InMemorySampleStore store = new InMemorySampleStore();
        FcsSample tube = Tubes.tube("t", 30, Map.of());
        store.put("t", tube);
        Experiment experiment = Experiment.empty("All").withGating(Tubes.gating())
                .withSample(new SampleInfo("t", "t", SampleRole.SAMPLE, "NC", 0, ""));
        assertSame(tube, service.prepared(store, experiment, "t"));

        Experiment compensated = experiment.withCompensation(CompensationSource.FILE, MATRIX);
        FcsSample first = service.prepared(store, compensated, "t");
        assertNotSame(tube, first);
        assertEquals(500, first.value(1, 0), 1e-3);
        assertEquals(0, first.value(2, 0), 1e-3);
        assertSame(first, service.prepared(store, compensated, "t"));
        assertEquals(100, service.gate(store, compensated, "t").count("p1"));
        assertEquals(1, service.analyze(store, compensated).samples().size());

        store.remove("t");
        assertThrows(MissingSampleException.class, () -> service.prepared(store, experiment,
                "t"));
        store.putAll(Map.of("t", tube));
        assertTrue(store.find("t").isPresent());
        store.clear();
        assertTrue(store.find("t").isEmpty());
    }

    @Test
    void cacheForgetsTheOldestTube() {
        CompensationCache cache = new CompensationCache(1);
        FcsSample a = Tubes.tube("a", 0, Map.of());
        FcsSample b = Tubes.tube("b", 0, Map.of());
        FcsSample first = cache.compensate(a, MATRIX);
        assertSame(first, cache.compensate(a, SpilloverMatrix.parse(MATRIX.format())));
        cache.compensate(b, MATRIX);
        assertEquals(1, cache.size());
        assertNotSame(first, cache.compensate(a, MATRIX));
        assertNotSame(cache.compensate(a, MATRIX), cache.compensate(a, MATRIX.with(0, 1, 0.2)));
        assertNotSame(cache.compensate(a, MATRIX), cache.compensate(a.withName("a"), MATRIX));
    }

    @Test
    void matrixAndThresholdsNeedControls() {
        InMemorySampleStore store = new InMemorySampleStore();
        store.put("s", Tubes.tube("s", 0, Map.of()));
        Experiment experiment = Experiment.empty("All").withGating(Tubes.gating())
                .withSample(new SampleInfo("s", "PI", SampleRole.SINGLE_STAIN, "", Double.NaN,
                        "PI-A"));
        assertThrows(IllegalArgumentException.class,
                () -> service.matrixFromControls(store, experiment));

        ApoptosisSettings settings = Tubes.apoptosis();
        assertEquals(settings, service.suggestThresholds(store, Experiment.empty("All"),
                settings));
        ApoptosisSettings proposed = service.suggestThresholds(store, experiment, settings);
        assertEquals(10, proposed.annexinThreshold(), 1e-9);
        assertEquals(100, proposed.piThreshold());

        store.put("u", Tubes.tube("u", 0, Map.of()));
        Experiment withUnstained = experiment.withSample(
                new SampleInfo("u", "U", SampleRole.UNSTAINED, "", Double.NaN, ""));
        assertEquals(10, service.suggestThresholds(store, withUnstained, settings)
                .piThreshold(), 1e-9);
        ApoptosisSettings missingParent = new ApoptosisSettings("p9", "FITC-A", "PI-A", 7, 8);
        assertEquals(missingParent, service.suggestThresholds(store, withUnstained,
                missingParent));
    }

    @Test
    void examplesAreReadOnce() {
        assertSame(ExampleLibrary.samples(Example.CADMIUM),
                ExampleLibrary.samples(Example.CADMIUM));
        assertEquals(24, ExampleLibrary.samples(Example.CADMIUM).size());
        assertTrue(ExampleLibrary.attribution().contains("10.17632/d4r5h95356.1"));
        assertEquals(Example.U87_CELL_CYCLE, Example.fromFolder("u87-cell-cycle"));
        assertTrue(Example.CADMIUM.simulated());
        assertThrows(IllegalArgumentException.class, () -> Example.fromFolder("x"));
    }
}
