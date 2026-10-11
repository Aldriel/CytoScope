package com.bioinfo.cytoscope.server.api;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.compensation.SpilloverMatrix;
import com.bioinfo.cytoscope.experiment.CompensationSource;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.experiment.SampleInfo;
import com.bioinfo.cytoscope.experiment.SampleRole;
import com.bioinfo.cytoscope.gating.GatingTree;
import com.bioinfo.cytoscope.gating.Population;
import java.util.ArrayList;
import java.util.List;

/**
 * An experiment in JSON: what the web application shows and sends back after each change.
 *
 * @param samples the tubes and their annotations
 * @param compensation where the spillover matrix comes from
 * @param matrix the spillover matrix; null without compensation
 * @param gating the root of the gating hierarchy
 * @param apoptosis settings of the annexin V / PI test, or null
 * @param cellCycle settings of the DNA content analysis, or null
 * @param metric value compared between groups, or null
 * @param controlGroup group the others are compared with; empty if none
 */
public record ExperimentJson(List<SampleJson> samples, CompensationSource compensation,
                             MatrixJson matrix, PopulationJson gating,
                             ApoptosisSettings apoptosis, CellCycleSettings cellCycle,
                             Metric metric, String controlGroup) {

    /**
     * A tube and its annotations.
     *
     * @param id identifier of the loaded file
     * @param name name shown to the user
     * @param role part the tube plays
     * @param group treatment group; empty for controls
     * @param dose dose or concentration, null if not given
     * @param stainChannel dye channel of a single-stain control; empty otherwise
     */
    public record SampleJson(String id, String name, SampleRole role, String group, Double dose,
                             String stainChannel) {

        static SampleJson of(SampleInfo info) {
            return new SampleJson(info.id(), info.name(), info.role(), info.group(),
                    Double.isNaN(info.dose()) ? null : info.dose(), info.stainChannel());
        }

        SampleInfo toInfo() {
            return new SampleInfo(id, name, role, group, dose == null ? Double.NaN : dose,
                    stainChannel);
        }
    }

    /**
     * A spillover matrix: {@code values[i][j]} is the share of dye {@code i} seen in channel
     * {@code j}.
     *
     * @param channels the channels, in order
     * @param values the spillover values
     */
    public record MatrixJson(List<String> channels, double[][] values) {

        static MatrixJson of(SpilloverMatrix matrix) {
            int n = matrix.channels().size();
            double[][] values = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    values[i][j] = matrix.value(i, j);
                }
            }
            return new MatrixJson(matrix.channels(), values);
        }

        SpilloverMatrix toMatrix() {
            return new SpilloverMatrix(channels, values);
        }
    }

    /**
     * A population and the populations gated from it.
     *
     * @param id identifier
     * @param name name shown to the user
     * @param gate its gate; null for the root
     * @param children populations gated from it
     */
    public record PopulationJson(String id, String name, GateJson gate,
                                 List<PopulationJson> children) {

        static PopulationJson of(Population population) {
            return new PopulationJson(population.id(), population.name(),
                    population.gate() == null ? null : GateJson.of(population.gate()),
                    population.children().stream().map(PopulationJson::of).toList());
        }

        Population toPopulation() {
            List<Population> converted = new ArrayList<>();
            for (PopulationJson child : children == null ? List.<PopulationJson>of() : children) {
                converted.add(child.toPopulation());
            }
            return new Population(id, name, gate == null ? null : gate.toGate(), converted);
        }
    }

    /**
     * Converts an experiment to JSON.
     *
     * @param experiment the experiment
     * @return its JSON form
     */
    public static ExperimentJson of(Experiment experiment) {
        return new ExperimentJson(
                experiment.samples().stream().map(SampleJson::of).toList(),
                experiment.compensation(),
                experiment.matrix() == null ? null : MatrixJson.of(experiment.matrix()),
                PopulationJson.of(experiment.gating().root()),
                experiment.apoptosis(), experiment.cellCycle(), experiment.metric(),
                experiment.controlGroup());
    }

    /**
     * Converts the JSON form back to an experiment, checking every part.
     *
     * @return the experiment
     * @throws IllegalArgumentException if a part is missing or not valid
     */
    public Experiment toExperiment() {
        try {
            if (samples == null || gating == null) {
                throw new IllegalArgumentException("The tubes and the gating are required");
            }
            return new Experiment(samples.stream().map(SampleJson::toInfo).toList(),
                    compensation == null ? CompensationSource.NONE : compensation,
                    matrix == null ? null : matrix.toMatrix(),
                    GatingTree.of(gating.toPopulation()), apoptosis, cellCycle, metric,
                    controlGroup);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("A required field is missing: " + e.getMessage(),
                    e);
        }
    }
}
