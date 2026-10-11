package com.bioinfo.cytoscope.report;

import com.bioinfo.cytoscope.analysis.ApoptosisResult;
import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleResult;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.SampleResult;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.Population;
import com.bioinfo.cytoscope.gating.QuadrantGate;
import com.bioinfo.cytoscope.plot.Charts;
import com.bioinfo.cytoscope.scale.Scale;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.scale.Scales;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Chooses and draws the figures of a report: the gating strategy on the first tube, one plot
 * per group for the annexin V / PI test and the DNA content, the comparison of the groups and
 * the dose-response curve.
 */
public final class ReportFigures {

    private static final int MAX_GROUP_FIGURES = 8;

    private ReportFigures() {}

    /**
     * Draws the figures.
     *
     * @param experiment the set-up
     * @param results its results
     * @param gate gates a tube by identifier, after compensation
     * @param texts texts of the report language
     * @return the figures, in order
     */
    public static List<Figure> of(Experiment experiment, ExperimentResults results,
                                  Function<String, GatingResult> gate, ReportTexts texts) {
        List<Figure> figures = new ArrayList<>();
        List<SampleResult> samples = results.samples().stream()
                .filter(s -> s.info().role() == SampleRole.SAMPLE).toList();
        if (samples.isEmpty()) {
            return figures;
        }
        Map<String, SampleResult> byGroup = new LinkedHashMap<>();
        for (SampleResult sample : samples) {
            byGroup.putIfAbsent(sample.info().group(), sample);
        }
        SampleResult first = samples.get(0);
        GatingResult firstGating = gate.apply(first.info().id());
        String dna = experiment.cellCycle() == null ? null : experiment.cellCycle().dnaChannel();
        strategy(figures, firstGating, texts, dna);
        int drawn = 0;
        for (SampleResult sample : byGroup.values()) {
            if (drawn++ >= MAX_GROUP_FIGURES) {
                break;
            }
            GatingResult gating = sample == first ? firstGating : gate.apply(sample.info().id());
            if (sample.apoptosis() != null) {
                figures.add(apoptosis(gating, experiment.apoptosis(), sample, texts));
            }
            if (sample.cellCycle() != null) {
                figures.add(cellCycle(gating, experiment, sample.cellCycle(), texts));
            }
        }
        if (results.comparison() != null) {
            String metric = texts.metric(experiment.metric(), experiment.gating());
            figures.add(new Figure(texts.get("figure.comparison", results.comparison()
                    .controlGroup()), Charts.barChart(metric, results.comparison(), metric)));
        }
        if (results.doseResponse() != null) {
            String metric = texts.metric(experiment.metric(), experiment.gating());
            figures.add(new Figure(texts.get("figure.doseResponse"),
                    Charts.doseResponse(metric, results.doseResponse(), texts.get("axis.dose"),
                            metric)));
        }
        return figures;
    }

    /** One plot per parent population whose children have two-channel gates. */
    private static void strategy(List<Figure> figures, GatingResult gating, ReportTexts texts,
                                 String dnaChannel) {
        for (Population parent : gating.tree().root().flatten()) {
            Map<String, List<Gate>> byAxes = new LinkedHashMap<>();
            for (Population child : parent.children()) {
                Gate g = child.gate();
                if (!(g instanceof QuadrantGate)) {
                    String y = g.yChannel() == null ? "SSC-A" : g.yChannel();
                    byAxes.computeIfAbsent(g.xChannel() + "\n" + y, k -> new ArrayList<>()).add(g);
                }
            }
            for (Map.Entry<String, List<Gate>> axes : byAxes.entrySet()) {
                String[] channels = axes.getKey().split("\n");
                FcsSample sample = gating.sample();
                if (sample.indexOf(channels[0]) < 0 || sample.indexOf(channels[1]) < 0) {
                    continue;
                }
                Set<String> names = new LinkedHashSet<>();
                parent.children().stream().filter(c -> axes.getValue().contains(c.gate()))
                        .forEach(c -> names.add(c.name()));
                figures.add(new Figure(texts.get("figure.gating", sample.name(), parent.name(),
                        String.join(", ", names)),
                        Charts.dotPlot(plot(gating, parent.id(), channels[0], channels[1],
                                parent.name(), axes.getValue(), null, null, dnaChannel))));
            }
        }
    }

    private static Figure apoptosis(GatingResult gating, ApoptosisSettings settings,
                                    SampleResult sample, ReportTexts texts) {
        ApoptosisResult result = sample.apoptosis();
        List<String> labels = new ArrayList<>();
        for (CellFate fate : List.of(CellFate.LIVE, CellFate.EARLY_APOPTOTIC,
                CellFate.LATE_APOPTOTIC, CellFate.NECROTIC)) {
            labels.add(texts.percent(result.percent(fate)));
        }
        String population = gating.tree().find(settings.parentId()).map(Population::name)
                .orElse(settings.parentId());
        return new Figure(texts.get("figure.apoptosis", sample.info().name(),
                sample.info().group(), population),
                Charts.dotPlot(plot(gating, settings.parentId(), settings.annexinChannel(),
                        settings.piChannel(), sample.info().name(), List.of(),
                        new double[] {settings.annexinThreshold(), settings.piThreshold()},
                        labels, null)));
    }

    private static Figure cellCycle(GatingResult gating, Experiment experiment,
                                    CellCycleResult result, ReportTexts texts) {
        String parent = experiment.cellCycle().parentId();
        FcsSample sample = gating.sample();
        int index = sample.requireChannel(experiment.cellCycle().dnaChannel());
        Channel channel = sample.channels().get(index);
        double[] values = gating.values(parent, index);
        Scale scale = Scales.create(ScaleType.LINEAR, channel, values);
        StringBuilder phases = new StringBuilder();
        for (CellCyclePhase phase : List.of(CellCyclePhase.G0_G1, CellCyclePhase.S,
                CellCyclePhase.G2_M)) {
            if (!phases.isEmpty()) {
                phases.append(", ");
            }
            phases.append(texts.get("phase." + phase.name())).append(' ')
                    .append(texts.percent(result.percent(phase)));
        }
        return new Figure(texts.get("figure.cellCycle", sample.name(), phases,
                texts.number(result.g1Cv(), 1)),
                Charts.histogram(sample.name(), values, scale, channel.displayName(),
                        result.bounds()));
    }

    private static Charts.DotPlot plot(GatingResult gating, String populationId, String xName,
                                       String yName, String title, List<Gate> gates,
                                       double[] quadrant, List<String> labels,
                                       String dnaChannel) {
        FcsSample sample = gating.sample();
        int xi = sample.requireChannel(xName);
        int yi = sample.requireChannel(yName);
        Channel xc = sample.channels().get(xi);
        Channel yc = sample.channels().get(yi);
        double[] x = gating.values(populationId, xi);
        double[] y = gating.values(populationId, yi);
        return new Charts.DotPlot(title, x, y,
                Scales.create(Scales.defaultType(xc, dnaChannel), xc, x),
                Scales.create(Scales.defaultType(yc, dnaChannel), yc, y),
                xc.displayName(), yc.displayName(), gates, quadrant,
                Objects.requireNonNullElse(labels, List.of()));
    }
}
