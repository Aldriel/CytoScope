package com.bioinfo.cytoscope.desktop.model;

import com.bioinfo.cytoscope.analysis.ApoptosisSettings;
import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellCycleSettings;
import com.bioinfo.cytoscope.analysis.CellFate;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.gating.Population;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Questions and changes about the set-up of an experiment, shared by the controllers: the first
 * settings of the analyses, guessed from the channels, and the values that can be compared. Every
 * method returns new values and leaves its arguments unchanged.
 */
public final class ExperimentSetup {

    /** Share of the range of a channel used as first threshold, before any suggestion. */
    static final double DEFAULT_THRESHOLD_SHARE = 0.01;

    private static final Pattern ANNEXIN =
            Pattern.compile("annexin|fitc", Pattern.CASE_INSENSITIVE);
    private static final Pattern PI =
            Pattern.compile(
                    "\\bPI\\b|propidium|PI-|PE-Texas|7-?AAD", Pattern.CASE_INSENSITIVE);
    private static final Pattern DNA =
            Pattern.compile(
                    "dna|dapi|hoechst|\\bPI\\b|PI-|propidium", Pattern.CASE_INSENSITIVE);

    private ExperimentSetup() {}

    /**
     * Returns the fluorescence channels among those of the tubes.
     *
     * @param channels channels of the first tube
     * @return the fluorescence channels, in file order
     */
    public static List<Channel> fluorescence(List<Channel> channels) {
        return channels.stream().filter(Channel::isFluorescence).toList();
    }

    /**
     * First set-up of the annexin V / PI test, guessed from the labels of the channels.
     *
     * @param experiment the experiment, whose first gate is analyzed
     * @param channels channels of the first tube
     * @return the settings, or null without fluorescence channel
     */
    public static ApoptosisSettings defaultApoptosis(
            Experiment experiment, List<Channel> channels) {
        List<Channel> fluorescence = fluorescence(channels);
        if (fluorescence.isEmpty()) {
            return null;
        }
        Channel annexin = channelLike(fluorescence, ANNEXIN, 0);
        Channel pi = channelLike(fluorescence, PI, 1);
        return new ApoptosisSettings(
                defaultParent(experiment),
                annexin.name(),
                pi.name(),
                annexin.range() * DEFAULT_THRESHOLD_SHARE,
                pi.range() * DEFAULT_THRESHOLD_SHARE);
    }

    /**
     * First set-up of the cell cycle, guessed from the labels of the channels.
     *
     * @param experiment the experiment, whose first gate is analyzed
     * @param channels channels of the first tube
     * @return the settings, or null without fluorescence channel
     */
    public static CellCycleSettings defaultCellCycle(
            Experiment experiment, List<Channel> channels) {
        List<Channel> fluorescence = fluorescence(channels);
        if (fluorescence.isEmpty()) {
            return null;
        }
        return new CellCycleSettings(
                defaultParent(experiment), channelLike(fluorescence, DNA, 0).name());
    }

    /** First channel whose name or label matches a pattern, otherwise the one at a position. */
    private static Channel channelLike(List<Channel> channels, Pattern pattern, int fallback) {
        return channels.stream()
                .filter(
                        c ->
                                pattern.matcher(c.label()).find()
                                        || pattern.matcher(c.name()).find())
                .findFirst()
                .orElse(channels.get(Math.min(fallback, channels.size() - 1)));
    }

    /** Population analyzed by default: the first gate under the root, or the root. */
    private static String defaultParent(Experiment experiment) {
        List<Population> children = experiment.gating().root().children();
        return children.isEmpty() ? Population.ROOT_ID : children.getFirst().id();
    }

    /**
     * Returns the values that can be compared with the current set-up: the apoptotic cells and
     * each state when the annexin V / PI test is set up, each phase with the cell cycle, and the
     * share of each gated population.
     *
     * @param experiment the experiment
     * @return the metrics, in display order
     */
    public static List<Metric> metricOptions(Experiment experiment) {
        List<Metric> options = new ArrayList<>();
        if (experiment.apoptosis() != null) {
            options.add(Metric.apoptotic());
            for (CellFate fate : CellFate.values()) {
                options.add(Metric.fate(fate));
            }
        }
        if (experiment.cellCycle() != null) {
            for (CellCyclePhase phase : CellCyclePhase.values()) {
                options.add(Metric.phase(phase));
            }
        }
        for (Population population : experiment.gating().root().flatten()) {
            if (!population.id().equals(Population.ROOT_ID)) {
                options.add(Metric.population(population.id()));
            }
        }
        return options;
    }

    /**
     * Removes the analyses that refer to populations no longer in the tree, and the compared value
     * that no longer exists.
     *
     * @param experiment the experiment, for example after a population was deleted
     * @return the experiment without dangling reference
     */
    public static Experiment withoutDanglingReferences(Experiment experiment) {
        Set<String> ids =
                experiment.gating().root().flatten().stream()
                        .map(Population::id)
                        .collect(Collectors.toSet());
        ApoptosisSettings apoptosis =
                experiment.apoptosis() != null && ids.contains(experiment.apoptosis().parentId())
                        ? experiment.apoptosis()
                        : null;
        CellCycleSettings cellCycle =
                experiment.cellCycle() != null && ids.contains(experiment.cellCycle().parentId())
                        ? experiment.cellCycle()
                        : null;
        Metric metric = experiment.metric();
        boolean kept =
                metric == null
                        || switch (metric.kind()) {
                            case POPULATION_PERCENT -> ids.contains(metric.target());
                            case APOPTOTIC, CELL_FATE -> apoptosis != null;
                            case CELL_CYCLE -> cellCycle != null;
                        };
        return experiment
                .withApoptosis(apoptosis)
                .withCellCycle(cellCycle)
                .withComparison(kept ? metric : null, experiment.controlGroup());
    }
}
