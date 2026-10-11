package com.bioinfo.cytoscope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.service.ExampleLibrary.OpenedExample;
import com.bioinfo.cytoscope.service.ExampleLibrary.PresetNames;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Opens the examples and checks their main results, as a guard against regressions. */
class ExampleLibraryTest {

    private final CytoService service = new CytoService();
    private final ExampleLibrary library = new ExampleLibrary(service);

    private record Opened(Experiment experiment, ExperimentResults results) {}

    private Opened open(Example example, PresetNames names) {
        AtomicInteger next = new AtomicInteger();
        OpenedExample opened = library.open(example, "All", names,
                () -> "s" + next.incrementAndGet());
        InMemorySampleStore store = new InMemorySampleStore();
        store.putAll(opened.samples());
        assertThrows(UnsupportedOperationException.class, () -> opened.samples().clear());
        return new Opened(opened.experiment(), service.analyze(store, opened.experiment()));
    }

    private static GroupComparison.GroupSummary group(GroupComparison comparison, String name) {
        return comparison.groups().stream().filter(g -> g.name().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void cadmiumSeries() {
        Opened opened = open(Example.CADMIUM, PresetNames.ENGLISH);
        Experiment experiment = opened.experiment();
        assertEquals(CompensationSource.FILE, experiment.compensation());
        assertEquals("0 µM", experiment.controlGroup());
        assertEquals("Cells", experiment.gating().root().children().get(0).name());
        assertEquals(3, experiment.samples().stream()
                .filter(s -> s.role() != SampleRole.SAMPLE).count());
        assertNotEquals(1_000, experiment.apoptosis().annexinThreshold());
        assertNotEquals(1_000, experiment.apoptosis().piThreshold());

        ExperimentResults results = opened.results();
        assertNull(results.comparisonIssue());
        assertEquals("***", group(results.comparison(), "50 µM").stars());
        assertEquals(8.08, results.doseResponse().fit().ec50(), 0.15);
        assertEquals(1.58, results.doseResponse().fit().hill(), 0.1);
    }

    @Test
    void u87Apoptosis() {
        Opened opened = open(Example.U87_APOPTOSIS, PresetNames.FRENCH);
        Experiment experiment = opened.experiment();
        assertEquals(CompensationSource.CONTROLS, experiment.compensation());
        assertEquals("NC", experiment.controlGroup());
        assertEquals("Cellules", experiment.gating().root().children().get(0).name());
        assertEquals(Metric.apoptotic(), experiment.metric());

        GroupComparison comparison = opened.results().comparison();
        assertEquals(4.35, group(comparison, "NC").mean(), 0.2);
        assertEquals(20.7, group(comparison, "TMZ").mean(), 0.5);
        assertEquals("**", group(comparison, "TMZ").stars());
        assertEquals(26.2, group(comparison, "DOXY").mean(), 0.5);
        assertEquals("***", group(comparison, "DOXY").stars());
        assertEquals(5, comparison.groups().size());
    }

    @Test
    void u87CellCycle() {
        Opened opened = open(Example.U87_CELL_CYCLE, PresetNames.ENGLISH);
        Experiment experiment = opened.experiment();
        assertEquals(CompensationSource.NONE, experiment.compensation());
        assertEquals("Singlets", experiment.gating().root().children().get(0).name());

        GroupComparison comparison = opened.results().comparison();
        assertEquals(10, group(comparison, "NC").mean(), 1.5);
        assertEquals(54, group(comparison, "TMZ").mean(), 2);
        assertEquals("***", group(comparison, "TMZ").stars());
        assertTrue(opened.results().samples().stream().allMatch(s -> s.cellCycle() != null));
    }
}
