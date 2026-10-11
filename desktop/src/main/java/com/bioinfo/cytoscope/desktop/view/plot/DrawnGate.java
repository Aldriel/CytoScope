package com.bioinfo.cytoscope.desktop.view.plot;

import com.bioinfo.cytoscope.gating.Gate;

import java.util.Objects;

/**
 * A gate drawn on a plot, with the population it defines.
 *
 * @param id identifier given back when the gate is clicked or moved
 * @param name name shown next to the gate
 * @param gate the region, in data values
 * @param share share of the parent shown after the name, such as {@code 84.8%}; may be empty
 * @param locked whether the gate is shown only and cannot be dragged
 */
public record DrawnGate(String id, String name, Gate gate, String share, boolean locked) {

    /** Validates the gate. */
    public DrawnGate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gate, "gate");
        share = share == null ? "" : share;
    }

    /** Returns the label of the gate: its name and share. */
    String label() {
        return share.isEmpty() ? name : name + " " + share;
    }
}
