package com.bioinfo.cytoscope.service;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.fcs.FcsReader;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Opens the example experiments, already set up: compensation, gates and analyses. The files
 * are read once and shared, since samples are immutable.
 */
public final class ExampleLibrary {

    private static final String ROOT = "/com/bioinfo/cytoscope/examples/";
    private static final Map<Example, List<FcsSample>> LOADED = new EnumMap<>(Example.class);

    private final CytoService service;

    /**
     * Creates the library.
     *
     * @param service the service that sets up the experiments
     */
    public ExampleLibrary(CytoService service) {
        this.service = service;
    }

    /**
     * Opens an example.
     *
     * @param example the example
     * @param rootName name of the population of all events, in the language of the user
     * @param names names of the populations created by the preset, in the language of the user
     * @param ids gives a new identifier for each tube
     * @return the set-up experiment and its tubes, by identifier
     */
    public OpenedExample open(Example example, String rootName, PresetNames names,
                              Supplier<String> ids) {
        Map<String, FcsSample> samples = new LinkedHashMap<>();
        Experiment experiment = Experiment.empty(rootName);
        for (FcsSample sample : samples(example)) {
            String id = ids.get();
            samples.put(id, sample);
            experiment = service.addSample(experiment, id, sample);
        }
        InMemorySampleStore store = new InMemorySampleStore();
        store.putAll(samples);
        experiment = switch (example) {
            case U87_APOPTOSIS -> u87Apoptosis(store, experiment, names);
            case U87_CELL_CYCLE -> cellCycle(experiment, names);
            case CADMIUM -> {
                Experiment gated = cells(experiment, names, 30_000, 260_000, 5_000, 120_000);
                yield withApoptosis(gated, service.suggestThresholds(store, gated,
                        new ApoptosisSettings(firstGate(gated), "FITC-A", "PI-A", 1_000, 1_000)));
            }
        };
        return new OpenedExample(experiment, samples);
    }

    /**
     * The matrix of the instrument overcompensates PI into FITC, so the preset computes it from
     * the single stains. All the U-87MG cells bind a little annexin V: the thresholds sit in the
     * valley above this baseline rather than above the controls without annexin V.
     */
    private Experiment u87Apoptosis(InMemorySampleStore store, Experiment experiment,
                                    PresetNames names) {
        Experiment compensated = experiment.withCompensation(CompensationSource.CONTROLS,
                service.matrixFromControls(store, experiment));
        Experiment gated = cells(compensated, names, 40_000, 260_000, 10_000, 260_000);
        return withApoptosis(gated,
                new ApoptosisSettings(firstGate(gated), "FITC-A", "PI-A", 2_500, 1_500));
    }

    private static Experiment cells(Experiment experiment, PresetNames names, double fscMin,
                                    double fscMax, double sscMin, double sscMax) {
        return experiment.withGating(experiment.gating().add(experiment.gating().root().id(),
                names.cells(),
                new RectangleGate("FSC-A", "SSC-A", fscMin, fscMax, sscMin, sscMax)));
    }

    private static String firstGate(Experiment experiment) {
        return experiment.gating().root().children().get(0).id();
    }

    private static Experiment withApoptosis(Experiment experiment, ApoptosisSettings settings) {
        return experiment.withApoptosis(settings).withComparison(Metric.apoptotic(),
                experiment.controlGroup());
    }

    private static Experiment cellCycle(Experiment experiment, PresetNames names) {
        Experiment gated = experiment.withGating(experiment.gating().add(
                experiment.gating().root().id(), names.singlets(),
                new PolygonGate("PI-A", "PI-W",
                        new double[] {20_000, 250_000, 250_000, 20_000},
                        new double[] {60_000, 80_000, 140_000, 120_000})));
        return gated.withCellCycle(new CellCycleSettings(firstGate(gated), "PI-A"))
                .withComparison(Metric.phase(CellCyclePhase.G2_M), experiment.controlGroup());
    }

    /**
     * Returns the tubes of an example, read once.
     *
     * @param example the example
     * @return the samples, in the order of the experiment
     */
    public static List<FcsSample> samples(Example example) {
        synchronized (LOADED) {
            return LOADED.computeIfAbsent(example, ExampleLibrary::read);
        }
    }

    private static List<FcsSample> read(Example example) {
        String folder = ROOT + example.folder() + "/";
        List<FcsSample> samples = new ArrayList<>();
        for (String file : resource(folder + "files.txt", 1 << 16).lines().toList()) {
            if (file.isBlank()) {
                continue;
            }
            try (InputStream in = open(folder + file.trim())) {
                samples.add(FcsReader.read(file.trim().replaceAll("\\.fcs$", ""),
                        in.readAllBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return List.copyOf(samples);
    }

    private static String resource(String path, int maxBytes) {
        try (InputStream in = open(path)) {
            return new String(in.readNBytes(maxBytes), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream open(String path) {
        InputStream in = ExampleLibrary.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing example resource " + path);
        }
        return in;
    }

    /**
     * Returns the credit of the real data, to show with the example.
     *
     * @return the attribution text, in Markdown
     */
    public static String attribution() {
        return resource(ROOT + "ATTRIBUTION.md", 1 << 16);
    }

    /**
     * An example ready to use.
     *
     * @param experiment the set-up
     * @param samples the tubes, by identifier, to put in the store
     */
    public record OpenedExample(Experiment experiment, Map<String, FcsSample> samples) {

        /** Copies the map. */
        public OpenedExample {
            samples = Collections.unmodifiableMap(new LinkedHashMap<>(samples));
        }
    }

    /**
     * Names of the populations created by the presets.
     *
     * @param cells the cells gated on light scatter
     * @param singlets the single cells of the DNA content analysis
     */
    public record PresetNames(String cells, String singlets) {

        /** English names. */
        public static final PresetNames ENGLISH = new PresetNames("Cells", "Singlets");
        /** French names. */
        public static final PresetNames FRENCH = new PresetNames("Cellules", "Cellules isolées");
    }
}
