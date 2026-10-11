package com.bioinfo.cytoscope.analysis;

import java.util.Objects;

/**
 * How the annexin V / PI test is read.
 *
 * @param parentId population analyzed, usually the cells gated on light scatter
 * @param annexinChannel channel of annexin V, such as {@code FITC-A}
 * @param piChannel channel of propidium iodide, such as {@code PI-A}
 * @param annexinThreshold annexin V value above which a cell is positive
 * @param piThreshold PI value above which a cell is positive
 */
public record ApoptosisSettings(String parentId, String annexinChannel, String piChannel,
                                double annexinThreshold, double piThreshold) {

    /** Creates the settings and checks them. */
    public ApoptosisSettings {
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(annexinChannel, "annexinChannel");
        Objects.requireNonNull(piChannel, "piChannel");
        if (annexinChannel.equals(piChannel)) {
            throw new IllegalArgumentException("Annexin V and PI need two different channels");
        }
        if (!Double.isFinite(annexinThreshold) || !Double.isFinite(piThreshold)) {
            throw new IllegalArgumentException("The thresholds must be numbers");
        }
    }

    /**
     * Returns the same settings with other thresholds.
     *
     * @param annexin new annexin V threshold
     * @param pi new PI threshold
     * @return the new settings
     */
    public ApoptosisSettings withThresholds(double annexin, double pi) {
        return new ApoptosisSettings(parentId, annexinChannel, piChannel, annexin, pi);
    }
}
