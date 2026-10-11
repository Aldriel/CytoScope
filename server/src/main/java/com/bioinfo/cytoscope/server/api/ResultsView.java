package com.bioinfo.cytoscope.server.api;

import com.bioinfo.cytoscope.analysis.ApoptosisResult;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleResult;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.experiment.AnalysisIssue;
import com.bioinfo.cytoscope.experiment.DoseResponse;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.experiment.PopulationStats;
import com.bioinfo.cytoscope.experiment.SampleResult;
import com.bioinfo.cytoscope.stats.FourParameterLogistic;
import com.bioinfo.cytoscope.stats.OneWayAnova;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The results of an experiment in JSON.
 *
 * @param samples results of each tube
 * @param comparison comparison of the groups with the control; null if it could not be run
 * @param comparisonIssue why the comparison is missing, or null
 * @param doseResponse the dose-response curve; null if it could not be fitted
 * @param doseResponseIssue why the curve is missing, or null
 */
public record ResultsView(List<SampleView> samples, ComparisonView comparison,
                          AnalysisIssue comparisonIssue, CurveView doseResponse,
                          AnalysisIssue doseResponseIssue) {

    /**
     * Results of one tube.
     *
     * @param id identifier of the tube
     * @param eventCount number of events
     * @param populations statistics of each population
     * @param apoptosis share of cells in each state, in percent; null without the test
     * @param cellCycle phases of the cell cycle; null without the analysis
     * @param cellCycleIssue why the cell cycle could not be read, or null
     */
    public record SampleView(String id, int eventCount, List<PopulationStats> populations,
                             Map<CellFate, Double> apoptosis, CellCycleView cellCycle,
                             AnalysisIssue cellCycleIssue) {}

    /**
     * Phases of the cell cycle of one tube.
     *
     * @param g1Peak DNA value of the G0/G1 peak
     * @param g2Peak DNA value of the G2/M peak
     * @param g1Cv coefficient of variation of the G0/G1 peak, in percent
     * @param bounds the four values that separate the phases
     * @param percents share of cells in each phase
     * @param total number of cells analyzed
     */
    public record CellCycleView(double g1Peak, double g2Peak, double g1Cv, double[] bounds,
                                Map<CellCyclePhase, Double> percents, int total) {}

    /**
     * Comparison of the groups.
     *
     * @param controlGroup the control group
     * @param anova analysis of variance over all the groups
     * @param groups summary of each group, the control first
     */
    public record ComparisonView(String controlGroup, OneWayAnova anova,
                                 List<GroupView> groups) {}

    /**
     * One group of the comparison.
     *
     * @param name the group
     * @param values value of each replicate
     * @param mean mean of the replicates
     * @param standardDeviation standard deviation of the replicates
     * @param difference difference with the control mean; null for the control
     * @param adjustedP p-value of Dunnett's test; null for the control
     * @param stars significance marks
     */
    public record GroupView(String name, double[] values, double mean, double standardDeviation,
                            Double difference, Double adjustedP, String stars) {}

    /**
     * The dose-response curve.
     *
     * @param points the measured points
     * @param fit the fitted four-parameter logistic curve
     */
    public record CurveView(List<DoseResponse.Point> points, FourParameterLogistic fit) {}

    /**
     * Converts the results.
     *
     * @param results the results
     * @return their JSON form
     */
    public static ResultsView of(ExperimentResults results) {
        return new ResultsView(results.samples().stream().map(ResultsView::sample).toList(),
                results.comparison() == null ? null : comparison(results.comparison()),
                results.comparisonIssue(),
                results.doseResponse() == null ? null : new CurveView(
                        results.doseResponse().points(), results.doseResponse().fit()),
                results.doseResponseIssue());
    }

    private static SampleView sample(SampleResult result) {
        Map<CellFate, Double> apoptosis = null;
        ApoptosisResult a = result.apoptosis();
        if (a != null) {
            apoptosis = new EnumMap<>(CellFate.class);
            for (CellFate fate : CellFate.values()) {
                apoptosis.put(fate, a.percent(fate));
            }
        }
        CellCycleView cycle = null;
        CellCycleResult c = result.cellCycle();
        if (c != null) {
            Map<CellCyclePhase, Double> percents = new EnumMap<>(CellCyclePhase.class);
            for (CellCyclePhase phase : CellCyclePhase.values()) {
                percents.put(phase, c.percent(phase));
            }
            cycle = new CellCycleView(c.g1Peak(), c.g2Peak(), c.g1Cv(), c.bounds(), percents,
                    c.total());
        }
        return new SampleView(result.info().id(), result.eventCount(), result.populations(),
                apoptosis, cycle, result.cellCycleIssue());
    }

    private static ComparisonView comparison(GroupComparison comparison) {
        List<GroupView> groups = comparison.groups().stream().map(g -> new GroupView(g.name(),
                g.values(), g.mean(), g.standardDeviation(),
                g.comparison() == null ? null : g.comparison().difference(),
                g.comparison() == null ? null : g.comparison().adjustedP(), g.stars())).toList();
        return new ComparisonView(comparison.controlGroup(), comparison.anova(), groups);
    }
}
