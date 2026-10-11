package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.gating.GatingTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Everything the user has set up for an analysis: the tubes and their annotations, the
 * compensation, the gating hierarchy and the settings of each analysis. The values of the
 * tubes are kept apart, in a {@link SampleStore}.
 *
 * <p>The experiment is immutable; the {@code with...} methods return a modified copy.
 *
 * @param samples the tubes, in display order
 * @param compensation where the spillover matrix comes from
 * @param matrix the spillover matrix; null without compensation
 * @param gating the gating hierarchy, applied to every tube
 * @param apoptosis settings of the annexin V / PI test; null if not used
 * @param cellCycle settings of the DNA content analysis; null if not used
 * @param metric value compared between groups and along the doses; null if none
 * @param controlGroup group the others are compared with; empty if none
 */
public record Experiment(List<SampleInfo> samples, CompensationSource compensation,
                         SpilloverMatrix matrix, GatingTree gating, ApoptosisSettings apoptosis,
                         CellCycleSettings cellCycle, Metric metric, String controlGroup) {

    /** Creates the experiment and checks that the compensation has a matrix. */
    public Experiment {
        samples = List.copyOf(samples);
        Objects.requireNonNull(compensation, "compensation");
        Objects.requireNonNull(gating, "gating");
        controlGroup = controlGroup == null ? "" : controlGroup;
        if ((compensation == CompensationSource.NONE) != (matrix == null)) {
            throw new IllegalArgumentException("A compensated experiment needs a matrix");
        }
        if (samples.stream().map(SampleInfo::id).distinct().count() != samples.size()) {
            throw new IllegalArgumentException("Two tubes have the same identifier");
        }
    }

    /**
     * Creates an empty experiment.
     *
     * @param rootName name of the population of all events
     * @return the experiment, without tube or analysis
     */
    public static Experiment empty(String rootName) {
        return new Experiment(List.of(), CompensationSource.NONE, null,
                GatingTree.create(rootName), null, null, null, "");
    }

    /**
     * Finds a tube.
     *
     * @param id identifier of the tube
     * @return its information, if the experiment has it
     */
    public Optional<SampleInfo> sample(String id) {
        return samples.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /**
     * Returns the names of the groups, in the order of their first tube.
     *
     * @return the groups of the compared samples
     */
    public List<String> groups() {
        return samples.stream().filter(SampleInfo::isCompared).map(SampleInfo::group)
                .distinct().toList();
    }

    /**
     * Returns a copy with one more tube.
     *
     * @param info the tube
     * @return the new experiment
     */
    public Experiment withSample(SampleInfo info) {
        List<SampleInfo> more = new ArrayList<>(samples);
        more.add(info);
        return new Experiment(more, compensation, matrix, gating, apoptosis, cellCycle, metric,
                controlGroup);
    }

    /**
     * Returns a copy with one tube changed.
     *
     * @param id identifier of the tube
     * @param change function that returns its new information
     * @return the new experiment
     */
    public Experiment withSampleChanged(String id, UnaryOperator<SampleInfo> change) {
        List<SampleInfo> changed = samples.stream()
                .map(s -> s.id().equals(id) ? change.apply(s) : s).toList();
        return new Experiment(changed, compensation, matrix, gating, apoptosis, cellCycle,
                metric, controlGroup);
    }

    /**
     * Returns a copy without a tube.
     *
     * @param id identifier of the tube
     * @return the new experiment
     */
    public Experiment withoutSample(String id) {
        List<SampleInfo> kept = samples.stream().filter(s -> !s.id().equals(id)).toList();
        return new Experiment(kept, compensation, matrix, gating, apoptosis, cellCycle, metric,
                controlGroup);
    }

    /**
     * Returns a copy with another compensation.
     *
     * @param source where the matrix comes from
     * @param newMatrix the matrix; null for {@link CompensationSource#NONE}
     * @return the new experiment
     */
    public Experiment withCompensation(CompensationSource source, SpilloverMatrix newMatrix) {
        return new Experiment(samples, source, newMatrix, gating, apoptosis, cellCycle, metric,
                controlGroup);
    }

    /**
     * Returns a copy with another gating hierarchy.
     *
     * @param tree the hierarchy
     * @return the new experiment
     */
    public Experiment withGating(GatingTree tree) {
        return new Experiment(samples, compensation, matrix, tree, apoptosis, cellCycle, metric,
                controlGroup);
    }

    /**
     * Returns a copy with other settings for the annexin V / PI test.
     *
     * @param settings the settings, null to stop the analysis
     * @return the new experiment
     */
    public Experiment withApoptosis(ApoptosisSettings settings) {
        return new Experiment(samples, compensation, matrix, gating, settings, cellCycle, metric,
                controlGroup);
    }

    /**
     * Returns a copy with other settings for the DNA content analysis.
     *
     * @param settings the settings, null to stop the analysis
     * @return the new experiment
     */
    public Experiment withCellCycle(CellCycleSettings settings) {
        return new Experiment(samples, compensation, matrix, gating, apoptosis, settings, metric,
                controlGroup);
    }

    /**
     * Returns a copy with another compared value and control group.
     *
     * @param newMetric the value, null for none
     * @param newControlGroup the control group, empty for none
     * @return the new experiment
     */
    public Experiment withComparison(Metric newMetric, String newControlGroup) {
        return new Experiment(samples, compensation, matrix, gating, apoptosis, cellCycle,
                newMetric, newControlGroup);
    }
}
