package com.bioinfo.cytoscope.experiment;

import java.util.List;

/**
 * Results of a whole experiment.
 *
 * @param samples results of each tube, in display order
 * @param comparison comparison of the groups with the control; null if not possible
 * @param comparisonIssue why the groups could not be compared; null otherwise
 * @param doseResponse dose-response curve of the compared value; null if not possible
 * @param doseResponseIssue why the curve could not be fitted; null otherwise
 */
public record ExperimentResults(List<SampleResult> samples, GroupComparison comparison,
                                AnalysisIssue comparisonIssue, DoseResponse doseResponse,
                                AnalysisIssue doseResponseIssue) {

    /** Creates the results and copies the tubes. */
    public ExperimentResults {
        samples = List.copyOf(samples);
    }
}
