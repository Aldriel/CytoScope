package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.analysis.ApoptosisResult;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleResult;
import com.bioinfo.cytoscope.analysis.CellFate;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Results of one tube: the counts of its populations and, when they are set up, the annexin V /
 * PI test and the DNA content analysis.
 *
 * @param info the tube
 * @param eventCount number of events in the file
 * @param populations counts of each population, in the order of the hierarchy
 * @param apoptosis result of the annexin V / PI test; null if not set up
 * @param cellCycle result of the DNA content analysis; null if not set up or not possible
 * @param cellCycleIssue why the DNA content could not be analyzed; null otherwise
 */
public record SampleResult(SampleInfo info, int eventCount, List<PopulationStats> populations,
                           ApoptosisResult apoptosis, CellCycleResult cellCycle,
                           AnalysisIssue cellCycleIssue) {

    /** Creates the result and copies the populations. */
    public SampleResult {
        populations = List.copyOf(populations);
    }

    /**
     * Returns the value of a metric for this tube.
     *
     * @param metric the metric
     * @return the value, empty if its analysis is not available
     */
    public OptionalDouble value(Metric metric) {
        double value = switch (metric.kind()) {
            case POPULATION_PERCENT -> populations.stream()
                    .filter(p -> p.populationId().equals(metric.target()))
                    .mapToDouble(PopulationStats::percentOfParent).findFirst().orElse(Double.NaN);
            case APOPTOTIC -> apoptosis == null ? Double.NaN : apoptosis.percentApoptotic();
            case CELL_FATE -> apoptosis == null ? Double.NaN
                    : apoptosis.percent(CellFate.valueOf(metric.target()));
            case CELL_CYCLE -> cellCycle == null ? Double.NaN
                    : cellCycle.percent(CellCyclePhase.valueOf(metric.target()));
        };
        return Double.isNaN(value) ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}
