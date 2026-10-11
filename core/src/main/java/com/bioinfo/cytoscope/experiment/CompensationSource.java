package com.bioinfo.cytoscope.experiment;

/** Where the spillover matrix of an experiment comes from. */
public enum CompensationSource {
    /** No compensation: the values are used as measured. */
    NONE,
    /** The matrix written by the cytometer software in the files. */
    FILE,
    /** The matrix computed from the single-stain controls of the experiment. */
    CONTROLS,
    /** A matrix edited by the user. */
    EDITED
}
