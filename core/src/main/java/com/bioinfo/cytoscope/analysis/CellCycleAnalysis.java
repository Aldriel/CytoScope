package com.bioinfo.cytoscope.analysis;

import com.bioinfo.cytoscope.fcs.FcsSample;
import com.bioinfo.cytoscope.gating.GatingResult;
import com.bioinfo.cytoscope.stats.Descriptive;
import java.util.Arrays;

/**
 * Reads a DNA content histogram (propidium iodide or another DNA dye, linear scale).
 *
 * <p>The G0/G1 peak is the tallest peak of the smoothed histogram, unless a peak at half its
 * position holds at least a tenth of its height: the tallest peak is then G2/M, as after a G2
 * arrest. The G2/M peak is looked for near twice the G0/G1 peak. The phases are separated at
 * 2.5 standard deviations around each peak, the standard deviation being estimated robustly from
 * the events around the G0/G1 peak. This peak-based model is simpler than the mathematical
 * deconvolutions (Dean-Jett-Fox, Watson): it slightly underestimates the S phase, whose edges
 * overlap the peaks, but needs no fitting.
 */
public final class CellCycleAnalysis {

    static final int BINS = 256;
    private static final int SMOOTHING = 2;
    private static final double DEBRIS_FRACTION = 0.04;
    private static final double G1_SEARCH_SHARE = 0.1;
    private static final double PEAK_WINDOW = 0.12;
    private static final double SPREAD = 2.5;

    private CellCycleAnalysis() {}

    /**
     * Analyzes a gated sample.
     *
     * @param gating the gated sample
     * @param settings population and DNA channel
     * @return the phases, peaks and bounds
     * @throws IllegalArgumentException if the population has too few cells to find a peak
     */
    public static CellCycleResult analyze(GatingResult gating, CellCycleSettings settings) {
        FcsSample sample = gating.sample();
        int channel = sample.requireChannel(settings.dnaChannel());
        double[] dna = gating.values(settings.parentId(), channel);
        double range = sample.channels().get(channel).range();
        return analyze(sample.name(), dna, range);
    }

    /**
     * Analyzes DNA values.
     *
     * @param sampleName name of the sample
     * @param dna DNA value of each cell
     * @param range largest value of the channel
     * @return the phases, peaks and bounds
     * @throws IllegalArgumentException if there are too few cells to find a peak
     */
    public static CellCycleResult analyze(String sampleName, double[] dna, double range) {
        double[] histogram = smooth(histogram(dna, range));
        double binWidth = range / BINS;
        int firstBin = (int) Math.ceil(BINS * DEBRIS_FRACTION);
        int tallest = argMax(histogram, firstBin, BINS);
        if (histogram[tallest] < 5) {
            throw new IllegalArgumentException("Too few cells to find the G0/G1 peak");
        }
        int g1Bin = tallest;
        int half = argMax(histogram, Math.max(firstBin, (int) (tallest * 0.4)),
                Math.max(firstBin + 1, (int) (tallest * 0.6) + 1));
        if (half < tallest && histogram[half] >= G1_SEARCH_SHARE * histogram[tallest]
                && isLocalPeak(histogram, half)) {
            g1Bin = half;
        }
        double g1 = refine(dna, (g1Bin + 0.5) * binWidth);
        double sigma = robustSigma(dna, g1);

        int g2Low = Math.min(BINS - 1, (int) (g1 * 1.75 / binWidth));
        int g2High = Math.min(BINS, (int) (g1 * 2.25 / binWidth) + 1);
        double g2 = 2 * g1;
        if (g2Low < g2High - 1) {
            int g2Bin = argMax(histogram, g2Low, g2High);
            if (isLocalPeak(histogram, g2Bin) && histogram[g2Bin] >= 0.02 * histogram[g1Bin]) {
                g2 = refine(dna, (g2Bin + 0.5) * binWidth);
            }
        }
        double sigma2 = sigma * g2 / g1;
        double[] bounds = {
            g1 - SPREAD * sigma, g1 + SPREAD * sigma, g2 - SPREAD * sigma2, g2 + SPREAD * sigma2};
        if (bounds[2] < bounds[1]) {
            bounds[2] = bounds[1];
        }
        int[] counts = new int[CellCyclePhase.values().length];
        for (double value : dna) {
            int phase = 0;
            while (phase < bounds.length && value >= bounds[phase]) {
                phase++;
            }
            counts[phase]++;
        }
        return new CellCycleResult(sampleName, g1, g2, 100 * sigma / g1, bounds, counts);
    }

    static double[] histogram(double[] values, double range) {
        double[] counts = new double[BINS];
        for (double value : values) {
            int bin = (int) Math.floor(value / range * BINS);
            if (bin >= 0 && bin < BINS) {
                counts[bin]++;
            }
        }
        return counts;
    }

    private static double[] smooth(double[] histogram) {
        double[] smoothed = new double[histogram.length];
        for (int i = 0; i < histogram.length; i++) {
            double sum = 0;
            int n = 0;
            for (int j = Math.max(0, i - SMOOTHING); j <= Math.min(histogram.length - 1,
                    i + SMOOTHING); j++) {
                sum += histogram[j];
                n++;
            }
            smoothed[i] = sum / n;
        }
        return smoothed;
    }

    private static int argMax(double[] values, int from, int to) {
        int best = from;
        for (int i = from; i < to; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static boolean isLocalPeak(double[] histogram, int bin) {
        double left = histogram[Math.max(0, bin - 3)];
        double right = histogram[Math.min(histogram.length - 1, bin + 3)];
        return histogram[bin] > left && histogram[bin] > right;
    }

    /** Median of the values within 12 % of a rough peak position: the center of the peak. */
    private static double refine(double[] values, double rough) {
        double[] near = Arrays.stream(values)
                .filter(v -> Math.abs(v - rough) <= PEAK_WINDOW * rough).toArray();
        return near.length == 0 ? rough : Descriptive.median(near);
    }

    /**
     * Standard deviation of the G0/G1 peak, from the half-width of its lower side: the upper
     * side is widened by the cells entering S phase.
     */
    private static double robustSigma(double[] values, double g1) {
        double[] lower = Arrays.stream(values)
                .filter(v -> v <= g1 && v >= g1 * (1 - PEAK_WINDOW * 2)).toArray();
        if (lower.length < 3) {
            return g1 * 0.03;
        }
        double[] distances = Arrays.stream(lower).map(v -> g1 - v).toArray();
        // For a half-normal distribution, the median distance is 0.6745 sigma.
        double sigma = Descriptive.median(distances) / 0.6745;
        return Math.max(sigma, g1 * 0.005);
    }
}
