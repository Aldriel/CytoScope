package com.bioinfo.cytoscope.stats;

/**
 * Four-parameter logistic model of a dose-response curve, fitted by least squares
 * (Levenberg-Marquardt).
 *
 * <p>{@code y = bottom + (top - bottom) / (1 + (EC50 / dose)^hill)}: the response goes from
 * {@code bottom} at dose 0 to {@code top} at high doses; the slope is reported positive, so
 * {@code top} is below {@code bottom} for a response that falls. EC50 is the dose that gives
 * half of the effect. The
 * fit works on log10(EC50), which keeps EC50 positive and makes its confidence interval
 * symmetric on a log scale, as is usual in pharmacology and toxicology.
 *
 * @param bottom response at dose 0 (for a positive slope)
 * @param top response at infinite dose (for a positive slope)
 * @param ec50 dose that gives half of the effect
 * @param hill Hill slope: steepness and direction of the curve
 * @param ec50Low lower bound of the 95 % confidence interval of EC50
 * @param ec50High upper bound of the 95 % confidence interval of EC50
 * @param rSquared share of the variance explained by the curve
 * @param pointCount number of points fitted
 */
public record FourParameterLogistic(double bottom, double top, double ec50, double hill,
                                    double ec50Low, double ec50High, double rSquared,
                                    int pointCount) {

    private static final int PARAMETERS = 4;
    private static final int MAX_ITERATIONS = 500;

    /**
     * Returns the response predicted for a dose.
     *
     * @param dose the dose, 0 or more
     * @return the response
     */
    public double predict(double dose) {
        return evaluate(new double[] {bottom, top, Math.log10(ec50), hill}, dose);
    }

    private static double evaluate(double[] p, double dose) {
        if (dose <= 0) {
            return p[3] > 0 ? p[0] : p[1];
        }
        double ratio = Math.pow(10, (p[2] - Math.log10(dose)) * p[3]);
        return p[0] + (p[1] - p[0]) / (1 + ratio);
    }

    /**
     * Fits the curve to dose-response points; replicates are given as separate points.
     *
     * @param doses dose of each point, 0 or more; at least two distinct positive doses
     * @param responses response of each point
     * @return the fitted model
     * @throws IllegalArgumentException with fewer than five points or a curve that cannot be
     *     fitted
     */
    public static FourParameterLogistic fit(double[] doses, double[] responses) {
        if (doses.length != responses.length) {
            throw new IllegalArgumentException("One response is needed per dose");
        }
        int n = doses.length;
        if (n <= PARAMETERS) {
            throw new IllegalArgumentException("The curve needs at least five points");
        }
        double minPositive = Double.POSITIVE_INFINITY;
        double maxDose = 0;
        for (double dose : doses) {
            if (dose < 0 || Double.isNaN(dose)) {
                throw new IllegalArgumentException("Doses must be 0 or more");
            }
            if (dose > 0) {
                minPositive = Math.min(minPositive, dose);
            }
            maxDose = Math.max(maxDose, dose);
        }
        if (!(maxDose > minPositive)) {
            throw new IllegalArgumentException("The curve needs at least two positive doses");
        }

        double[] best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (double[] start : starts(doses, responses, minPositive, maxDose)) {
            double[] fitted = levenbergMarquardt(doses, responses, start);
            double cost = cost(doses, responses, fitted);
            if (cost < bestCost) {
                bestCost = cost;
                best = fitted;
            }
        }
        if (best == null || !Double.isFinite(bestCost)) {
            throw new IllegalArgumentException("The curve could not be fitted to these points");
        }

        double mean = Descriptive.mean(responses);
        double total = 0;
        for (double r : responses) {
            total += (r - mean) * (r - mean);
        }
        double rSquared = total == 0 ? 1 : 1 - bestCost / total;
        double[] interval = ec50Interval(doses, responses, best, bestCost);
        if (best[3] < 0) {
            // Same curve written with a positive slope: bottom is then always the response at 0.
            best = new double[] {best[1], best[0], best[2], -best[3]};
        }
        return new FourParameterLogistic(best[0], best[1], Math.pow(10, best[2]), best[3],
                interval[0], interval[1], rSquared, n);
    }

    /** Starting points: both directions of the curve, EC50 at several doses of the range. */
    private static double[][] starts(double[] doses, double[] responses, double minPositive,
                                     double maxDose) {
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (double r : responses) {
            low = Math.min(low, r);
            high = Math.max(high, r);
        }
        double logMin = Math.log10(minPositive);
        double logMax = Math.log10(maxDose);
        double[][] starts = new double[10][];
        int i = 0;
        for (int step = 0; step < 5; step++) {
            double logEc50 = logMin + (logMax - logMin) * (step + 0.5) / 5;
            starts[i++] = new double[] {low, high, logEc50, 1};
            starts[i++] = new double[] {high, low, logEc50, 1};
        }
        return starts;
    }

    private static double cost(double[] doses, double[] responses, double[] p) {
        double sum = 0;
        for (int i = 0; i < doses.length; i++) {
            double r = responses[i] - evaluate(p, doses[i]);
            sum += r * r;
        }
        return sum;
    }

    private static double[][] jacobian(double[] doses, double[] p) {
        double[][] j = new double[doses.length][PARAMETERS];
        for (int i = 0; i < doses.length; i++) {
            for (int k = 0; k < PARAMETERS; k++) {
                double step = 1e-6 * Math.max(1, Math.abs(p[k]));
                double[] plus = p.clone();
                double[] minus = p.clone();
                plus[k] += step;
                minus[k] -= step;
                j[i][k] = (evaluate(plus, doses[i]) - evaluate(minus, doses[i])) / (2 * step);
            }
        }
        return j;
    }

    private static double[] levenbergMarquardt(double[] doses, double[] responses,
                                               double[] start) {
        double[] p = start.clone();
        double cost = cost(doses, responses, p);
        double lambda = 1e-3;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[][] j = jacobian(doses, p);
            double[][] jtj = new double[PARAMETERS][PARAMETERS];
            double[] jtr = new double[PARAMETERS];
            for (int i = 0; i < doses.length; i++) {
                double r = responses[i] - evaluate(p, doses[i]);
                for (int a = 0; a < PARAMETERS; a++) {
                    jtr[a] += j[i][a] * r;
                    for (int b = 0; b < PARAMETERS; b++) {
                        jtj[a][b] += j[i][a] * j[i][b];
                    }
                }
            }
            boolean improved = false;
            while (lambda < 1e12) {
                double[][] damped = new double[PARAMETERS][PARAMETERS];
                for (int a = 0; a < PARAMETERS; a++) {
                    damped[a] = jtj[a].clone();
                    damped[a][a] += lambda * Math.max(jtj[a][a], 1e-12);
                }
                double[] delta = solve(damped, jtr);
                if (delta != null) {
                    double[] candidate = new double[PARAMETERS];
                    for (int a = 0; a < PARAMETERS; a++) {
                        candidate[a] = p[a] + delta[a];
                    }
                    double candidateCost = cost(doses, responses, candidate);
                    if (candidateCost < cost) {
                        double relative = (cost - candidateCost) / Math.max(cost, 1e-300);
                        p = candidate;
                        cost = candidateCost;
                        lambda = Math.max(lambda / 10, 1e-12);
                        improved = true;
                        if (relative < 1e-12) {
                            return p;
                        }
                        break;
                    }
                }
                lambda *= 10;
            }
            if (!improved) {
                return p;
            }
        }
        return p;
    }

    /** 95 % interval of EC50 from the standard error of log10(EC50), with n - 4 degrees. */
    private static double[] ec50Interval(double[] doses, double[] responses, double[] p,
                                         double cost) {
        int degrees = doses.length - PARAMETERS;
        double[][] j = jacobian(doses, p);
        double[][] jtj = new double[PARAMETERS][PARAMETERS];
        for (double[] row : j) {
            for (int a = 0; a < PARAMETERS; a++) {
                for (int b = 0; b < PARAMETERS; b++) {
                    jtj[a][b] += row[a] * row[b];
                }
            }
        }
        double[] unit = new double[PARAMETERS];
        unit[2] = 1;
        double[] column = solve(jtj, unit);
        if (column == null || !(column[2] > 0)) {
            return new double[] {Double.NaN, Double.NaN};
        }
        double variance = cost / degrees * column[2];
        double half = Distributions.studentQuantile(0.975, degrees) * Math.sqrt(variance);
        return new double[] {Math.pow(10, p[2] - half), Math.pow(10, p[2] + half)};
    }

    /** Solves a small linear system by Gaussian elimination; null if it is singular. */
    static double[] solve(double[][] matrix, double[] vector) {
        int n = vector.length;
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
            a[i][n] = vector[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(a[pivot][col]) < 1e-300) {
                return null;
            }
            double[] swap = a[col];
            a[col] = a[pivot];
            a[pivot] = swap;
            for (int row = col + 1; row < n; row++) {
                double factor = a[row][col] / a[col][col];
                for (int k = col; k <= n; k++) {
                    a[row][k] -= factor * a[col][k];
                }
            }
        }
        double[] x = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double sum = a[row][n];
            for (int k = row + 1; k < n; k++) {
                sum -= a[row][k] * x[k];
            }
            x[row] = sum / a[row][row];
        }
        return x;
    }
}
