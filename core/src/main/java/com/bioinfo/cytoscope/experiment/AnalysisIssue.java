package com.bioinfo.cytoscope.experiment;

/**
 * Why an analysis of the experiment could not be done. The applications translate each issue
 * into a message for the user.
 */
public enum AnalysisIssue {
    /** No value has been chosen for the comparison. */
    NO_METRIC,
    /** The control group is not chosen or has no sample. */
    NO_CONTROL_GROUP,
    /** The comparison needs at least one group besides the control. */
    TOO_FEW_GROUPS,
    /** The groups need replicates to estimate the variance. */
    TOO_FEW_REPLICATES,
    /** The value cannot be computed: its analysis is not set up. */
    METRIC_UNAVAILABLE,
    /** The curve needs at least five samples with a dose, and two positive doses. */
    TOO_FEW_DOSES,
    /** The DNA histogram of a sample has no clear G0/G1 peak. */
    NO_G1_PEAK
}
