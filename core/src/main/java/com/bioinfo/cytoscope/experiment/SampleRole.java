package com.bioinfo.cytoscope.experiment;

/** The part a tube plays in an experiment. */
public enum SampleRole {
    /** A sample to analyze: control or treated cells, stained with every dye. */
    SAMPLE,
    /** Cells without any dye: autofluorescence, used as negative reference. */
    UNSTAINED,
    /** Cells stained with one dye only: used to compute the compensation and the thresholds. */
    SINGLE_STAIN
}
