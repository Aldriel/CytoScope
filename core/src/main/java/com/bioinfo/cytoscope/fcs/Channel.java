package com.bioinfo.cytoscope.fcs;

import java.util.Locale;
import java.util.Objects;

/**
 * One measured parameter of an FCS file ($PnN, $PnS and $PnR keywords).
 *
 * @param name short name given by the cytometer, such as {@code FITC-A} or {@code FSC-A}
 * @param label stain or marker entered by the user, such as {@code Annexin V}; empty if none
 * @param range upper bound of the values the cytometer can produce
 */
public record Channel(String name, String label, double range) {

    /** Creates a channel and checks its fields. */
    public Channel {
        Objects.requireNonNull(name, "name");
        label = label == null ? "" : label.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("A channel needs a name");
        }
        if (!(range > 0)) {
            throw new IllegalArgumentException("The range of " + name + " must be positive");
        }
    }

    /**
     * Returns the text shown to the user: the name followed by the stain, if there is one.
     *
     * @return for example {@code FITC-A (Annexin V)}
     */
    public String displayName() {
        return label.isEmpty() || label.equals(name) ? name : name + " (" + label + ")";
    }

    /**
     * Tells whether the channel measures light scatter (size or granularity) rather than a
     * fluorescence.
     *
     * @return true for forward and side scatter
     */
    public boolean isScatter() {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("FSC") || upper.startsWith("SSC");
    }

    /**
     * Tells whether the channel records the acquisition time.
     *
     * @return true for the time channel
     */
    public boolean isTime() {
        return name.equalsIgnoreCase("Time");
    }

    /**
     * Tells whether the channel measures a fluorescence, which can be compensated.
     *
     * @return true unless it is a scatter or time channel
     */
    public boolean isFluorescence() {
        return !isScatter() && !isTime();
    }
}
