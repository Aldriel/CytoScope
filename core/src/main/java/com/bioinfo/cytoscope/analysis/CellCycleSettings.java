package com.bioinfo.cytoscope.analysis;

import java.util.Objects;

/**
 * How the DNA content histogram is read.
 *
 * @param parentId population analyzed, usually the single cells (doublets excluded)
 * @param dnaChannel channel of the DNA dye, on a linear scale, such as {@code PI-A}
 */
public record CellCycleSettings(String parentId, String dnaChannel) {

    /** Creates the settings and checks them. */
    public CellCycleSettings {
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(dnaChannel, "dnaChannel");
    }
}
