package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.analysis.ApoptosisAnalysis;
import com.bioinfo.cytoscope.analysis.ApoptosisResult;
import com.bioinfo.cytoscope.analysis.CellCycleAnalysis;
import com.bioinfo.cytoscope.analysis.CellCycleResult;
import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingEngine;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.gating.Population;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Runs every analysis of an experiment: gating of each tube, annexin V / PI test, DNA content,
 * comparison of the groups and dose-response curve.
 */
public final class ExperimentAnalyzer {

    private ExperimentAnalyzer() {}

    /**
     * Analyzes an experiment.
     *
     * @param experiment the set-up
     * @param prepared returns the values of a tube ready to gate (compensated), by identifier
     * @return the results
     */
    public static ExperimentResults analyze(Experiment experiment,
                                            Function<String, FcsSample> prepared) {
        List<SampleResult> results = new ArrayList<>();
        for (SampleInfo info : experiment.samples()) {
            results.add(analyzeSample(experiment, info, prepared.apply(info.id())));
        }
        GroupComparison comparison = null;
        AnalysisIssue comparisonIssue = null;
        try {
            comparison = compare(experiment, results);
        } catch (IssueException e) {
            comparisonIssue = e.issue;
        }
        DoseResponse doseResponse = null;
        AnalysisIssue doseIssue = null;
        try {
            doseResponse = doseResponse(experiment, results);
        } catch (IssueException e) {
            doseIssue = e.issue;
        }
        return new ExperimentResults(results, comparison, comparisonIssue, doseResponse,
                doseIssue);
    }

    /**
     * Analyzes one tube.
     *
     * @param experiment the set-up
     * @param info the tube
     * @param sample its values, compensated
     * @return its results
     */
    public static SampleResult analyzeSample(Experiment experiment, SampleInfo info,
                                             FcsSample sample) {
        GatingResult gating = GatingEngine.apply(sample, experiment.gating());
        List<PopulationStats> populations = new ArrayList<>();
        for (Population population : experiment.gating().root().flatten()) {
            String id = population.id();
            populations.add(new PopulationStats(id, gating.count(id),
                    gating.percentOfParent(id), gating.percentOfTotal(id)));
        }
        ApoptosisResult apoptosis = null;
        if (experiment.apoptosis() != null
                && experiment.gating().find(experiment.apoptosis().parentId()).isPresent()) {
            apoptosis = ApoptosisAnalysis.analyze(gating, experiment.apoptosis());
        }
        CellCycleResult cellCycle = null;
        AnalysisIssue cellCycleIssue = null;
        if (experiment.cellCycle() != null && info.role() == SampleRole.SAMPLE
                && experiment.gating().find(experiment.cellCycle().parentId()).isPresent()) {
            try {
                cellCycle = CellCycleAnalysis.analyze(gating, experiment.cellCycle());
            } catch (IllegalArgumentException e) {
                cellCycleIssue = AnalysisIssue.NO_G1_PEAK;
            }
        }
        return new SampleResult(info, sample.eventCount(), populations, apoptosis, cellCycle,
                cellCycleIssue);
    }

    private static GroupComparison compare(Experiment experiment, List<SampleResult> results) {
        Metric metric = requireMetric(experiment);
        if (experiment.controlGroup().isEmpty()) {
            throw new IssueException(AnalysisIssue.NO_CONTROL_GROUP);
        }
        Map<String, List<Double>> byGroup = new LinkedHashMap<>();
        for (SampleResult result : results) {
            if (result.info().isCompared()) {
                OptionalDouble value = result.value(metric);
                if (value.isPresent()) {
                    byGroup.computeIfAbsent(result.info().group(), g -> new ArrayList<>())
                            .add(value.getAsDouble());
                }
            }
        }
        if (byGroup.isEmpty()) {
            throw new IssueException(AnalysisIssue.METRIC_UNAVAILABLE);
        }
        if (!byGroup.containsKey(experiment.controlGroup())) {
            throw new IssueException(AnalysisIssue.NO_CONTROL_GROUP);
        }
        if (byGroup.size() < 2) {
            throw new IssueException(AnalysisIssue.TOO_FEW_GROUPS);
        }
        int values = byGroup.values().stream().mapToInt(List::size).sum();
        if (values - byGroup.size() < 1) {
            throw new IssueException(AnalysisIssue.TOO_FEW_REPLICATES);
        }
        Map<String, double[]> arrays = new LinkedHashMap<>();
        byGroup.forEach((group, list) ->
                arrays.put(group, list.stream().mapToDouble(Double::doubleValue).toArray()));
        return GroupComparison.of(arrays, experiment.controlGroup());
    }

    private static DoseResponse doseResponse(Experiment experiment, List<SampleResult> results) {
        Metric metric = requireMetric(experiment);
        List<DoseResponse.Point> points = new ArrayList<>();
        boolean dosed = false;
        for (SampleResult result : results) {
            SampleInfo info = result.info();
            if (info.role() != SampleRole.SAMPLE || Double.isNaN(info.dose())) {
                continue;
            }
            dosed = true;
            OptionalDouble value = result.value(metric);
            if (value.isPresent()) {
                points.add(new DoseResponse.Point(info.name(), info.dose(), value.getAsDouble()));
            }
        }
        if (dosed && points.isEmpty()) {
            throw new IssueException(AnalysisIssue.METRIC_UNAVAILABLE);
        }
        try {
            return DoseResponse.of(points);
        } catch (IllegalArgumentException e) {
            throw new IssueException(AnalysisIssue.TOO_FEW_DOSES);
        }
    }

    private static Metric requireMetric(Experiment experiment) {
        if (experiment.metric() == null) {
            throw new IssueException(AnalysisIssue.NO_METRIC);
        }
        return experiment.metric();
    }

    /** Carries an issue out of the comparison or the curve, to be stored in the results. */
    private static final class IssueException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final transient AnalysisIssue issue;

        IssueException(AnalysisIssue issue) {
            super(issue.name(), null, false, false);
            this.issue = issue;
        }
    }
}
