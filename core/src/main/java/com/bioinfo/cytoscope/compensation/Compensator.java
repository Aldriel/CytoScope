package com.bioinfo.cytoscope.compensation;

import com.bioinfo.cytoscope.fcs.FcsSample;

/**
 * Removes the spillover between fluorochromes from the values of a sample.
 *
 * <p>For each event, the vector of the compensated channels is multiplied by the inverse of the
 * spillover matrix. The other channels (scatter, time, channels absent from the matrix) are
 * kept as they are.
 */
public final class Compensator {

    private Compensator() {}

    /**
     * Compensates a sample.
     *
     * @param sample the measured values
     * @param matrix the spillover matrix; its channels must exist in the sample
     * @return a new sample with the compensated values
     * @throws IllegalArgumentException if a channel of the matrix is missing from the sample
     */
    public static FcsSample apply(FcsSample sample, SpilloverMatrix matrix) {
        int n = matrix.channels().size();
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) {
            indices[i] = sample.requireChannel(matrix.channels().get(i));
        }
        double[][] inverse = matrix.inverse();
        int channelCount = sample.channels().size();
        float[][] values = new float[channelCount][];
        for (int c = 0; c < channelCount; c++) {
            values[c] = sample.column(c);
        }
        float[][] measured = new float[n][];
        for (int i = 0; i < n; i++) {
            measured[i] = sample.column(indices[i]);
        }
        int events = sample.eventCount();
        for (int e = 0; e < events; e++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    sum += measured[i][e] * inverse[i][j];
                }
                values[indices[j]][e] = (float) sum;
            }
        }
        return sample.withValues(values);
    }
}
