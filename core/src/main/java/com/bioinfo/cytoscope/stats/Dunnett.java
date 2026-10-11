package com.bioinfo.cytoscope.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Dunnett's test: compares each treated group with one control group, keeping the overall risk
 * of a false positive at the chosen level whatever the number of groups (Dunnett, J. Am. Stat.
 * Assoc. 50:1096, 1955).
 *
 * <p>Each comparison is a t statistic built on the pooled variance of all the groups. The
 * statistics are correlated because they share the control; the adjusted p-value is the
 * probability that the largest of them, in absolute value, exceeds the one observed. It is
 * computed by integrating the multivariate t distribution: conditionally on the control mean and
 * on the pooled standard deviation, the comparisons are independent.
 */
public final class Dunnett {

    private static final int Z_STEPS = 160;
    private static final double Z_LIMIT = 8.5;
    private static final int S_STEPS = 240;

    private Dunnett() {}

    /**
     * Comparison of one treated group with the control.
     *
     * @param difference mean of the group minus mean of the control
     * @param standardError standard error of the difference
     * @param t the t statistic
     * @param adjustedP two-sided p-value adjusted for the number of comparisons
     */
    public record Comparison(double difference, double standardError, double t,
                             double adjustedP) {}

    /**
     * Runs the test.
     *
     * @param control values of the control group
     * @param treated values of each treated group
     * @return one comparison per treated group, in the same order
     * @throws IllegalArgumentException if a group is empty or no degree of freedom is left
     */
    public static List<Comparison> compare(double[] control, List<double[]> treated) {
        List<double[]> all = new ArrayList<>();
        all.add(control);
        all.addAll(treated);
        for (double[] group : all) {
            if (OneWayAnova.count(group) == 0) {
                throw new IllegalArgumentException("Every group needs at least one value");
            }
        }
        OneWayAnova anova = OneWayAnova.of(all);
        double s = Math.sqrt(anova.meanSquareError());
        int degrees = anova.withinDegrees();
        int n0 = OneWayAnova.count(control);
        double mean0 = Descriptive.mean(control);
        int k = treated.size();
        double[] lambdas = new double[k];
        List<Comparison> raw = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            int ni = OneWayAnova.count(treated.get(i));
            lambdas[i] = Math.sqrt((double) ni / (ni + n0));
            double difference = Descriptive.mean(treated.get(i)) - mean0;
            double se = s * Math.sqrt(1.0 / ni + 1.0 / n0);
            double t = se == 0 ? (difference == 0 ? 0 : Math.copySign(1e300, difference))
                    : difference / se;
            raw.add(new Comparison(difference, se, t, Double.NaN));
        }
        List<Comparison> comparisons = new ArrayList<>();
        for (Comparison c : raw) {
            double p = Double.isFinite(c.t()) && Math.abs(c.t()) < 1e300
                    ? 1 - probabilityAllWithin(Math.abs(c.t()), lambdas, degrees) : 0;
            comparisons.add(new Comparison(c.difference(), c.standardError(), c.t(),
                    Math.max(0, Math.min(1, p))));
        }
        return comparisons;
    }

    /**
     * Returns the two-sided critical value of the test: the value that the largest statistic
     * exceeds with the given probability when no group differs from the control.
     *
     * @param alpha risk of a false positive, such as 0.05
     * @param groupSizes size of each treated group
     * @param controlSize size of the control group
     * @param degrees degrees of freedom of the pooled variance; infinite for a known variance
     * @return the critical value
     */
    public static double criticalValue(double alpha, int[] groupSizes, int controlSize,
                                       double degrees) {
        double[] lambdas = new double[groupSizes.length];
        for (int i = 0; i < groupSizes.length; i++) {
            lambdas[i] = Math.sqrt((double) groupSizes[i] / (groupSizes[i] + controlSize));
        }
        double low = 0;
        double high = 20;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2;
            if (1 - probabilityAllWithin(mid, lambdas, degrees) > alpha) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /**
     * Probability that every |T_i| is below c. T_i = (λ_i Z + √(1-λ_i²) E_i) / S, where Z and the
     * E_i are independent standard normals and S is the pooled standard deviation divided by
     * the true one (chi distribution with {@code degrees} degrees of freedom, divided by its
     * square root).
     */
    static double probabilityAllWithin(double c, double[] lambdas, double degrees) {
        if (Double.isInfinite(degrees)) {
            return integrateOverZ(c, lambdas);
        }
        double spread = 1 / Math.sqrt(2 * degrees);
        double sMin = Math.max(1e-9, 1 - 9 * spread);
        double sMax = 1 + 12 * spread + 4 / degrees;
        double h = (sMax - sMin) / S_STEPS;
        double logNorm = (degrees / 2) * Math.log(degrees / 2) - Distributions.logGamma(degrees / 2)
                + Math.log(2);
        double sum = 0;
        for (int i = 0; i <= S_STEPS; i++) {
            double s = sMin + i * h;
            double density = Math.exp(logNorm + (degrees - 1) * Math.log(s)
                    - degrees * s * s / 2);
            double weight = i == 0 || i == S_STEPS ? 1 : (i % 2 == 1 ? 4 : 2);
            sum += weight * density * integrateOverZ(c * s, lambdas);
        }
        return sum * h / 3;
    }

    /** Probability that every |λ_i Z + √(1-λ_i²) E_i| is below a bound, integrated over Z. */
    private static double integrateOverZ(double bound, double[] lambdas) {
        double h = 2 * Z_LIMIT / Z_STEPS;
        double sum = 0;
        for (int i = 0; i <= Z_STEPS; i++) {
            double z = -Z_LIMIT + i * h;
            double product = 1;
            for (double lambda : lambdas) {
                double sd = Math.sqrt(1 - lambda * lambda);
                product *= Distributions.normalCdf((bound - lambda * z) / sd)
                        - Distributions.normalCdf((-bound - lambda * z) / sd);
            }
            double weight = i == 0 || i == Z_STEPS ? 1 : (i % 2 == 1 ? 4 : 2);
            sum += weight * Distributions.normalDensity(z) * product;
        }
        return sum * h / 3;
    }
}
