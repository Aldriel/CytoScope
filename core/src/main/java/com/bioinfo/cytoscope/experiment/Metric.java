package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.analysis.CellCyclePhase;
import com.bioinfo.cytoscope.analysis.CellFate;
import java.util.Objects;

/**
 * A value measured on every sample, compared between groups or along the doses.
 *
 * @param kind what is measured
 * @param target the population identifier, the cell state or the phase, depending on the kind;
 *     empty for {@link Kind#APOPTOTIC}
 */
public record Metric(Kind kind, String target) {

    /** The kinds of measured values. */
    public enum Kind {
        /** Percentage of a population in its parent. */
        POPULATION_PERCENT,
        /** Percentage of cells in one state of the annexin V / PI test. */
        CELL_FATE,
        /** Percentage of apoptotic cells, early and late together. */
        APOPTOTIC,
        /** Percentage of cells in one phase of the cell cycle. */
        CELL_CYCLE
    }

    /** Creates the metric and checks its target. */
    public Metric {
        Objects.requireNonNull(kind, "kind");
        target = target == null ? "" : target;
        switch (kind) {
            case CELL_FATE -> CellFate.valueOf(target);
            case CELL_CYCLE -> CellCyclePhase.valueOf(target);
            case POPULATION_PERCENT -> {
                if (target.isBlank()) {
                    throw new IllegalArgumentException("Choose a population");
                }
            }
            default -> target = "";
        }
    }

    /**
     * Returns the percentage of apoptotic cells.
     *
     * @return the metric
     */
    public static Metric apoptotic() {
        return new Metric(Kind.APOPTOTIC, "");
    }

    /**
     * Returns the percentage of cells in one state.
     *
     * @param fate the state
     * @return the metric
     */
    public static Metric fate(CellFate fate) {
        return new Metric(Kind.CELL_FATE, fate.name());
    }

    /**
     * Returns the percentage of cells in one phase.
     *
     * @param phase the phase
     * @return the metric
     */
    public static Metric phase(CellCyclePhase phase) {
        return new Metric(Kind.CELL_CYCLE, phase.name());
    }

    /**
     * Returns the percentage of a population in its parent.
     *
     * @param populationId identifier of the population
     * @return the metric
     */
    public static Metric population(String populationId) {
        return new Metric(Kind.POPULATION_PERCENT, populationId);
    }
}
