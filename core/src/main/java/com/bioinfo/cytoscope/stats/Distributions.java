package com.bioinfo.cytoscope.stats;

/**
 * Probability distributions needed by the statistical tests: normal, Student's t and Fisher's F,
 * computed from the gamma and incomplete beta functions (Numerical Recipes, chapter 6).
 */
public final class Distributions {

    private static final double[] LANCZOS = {
        76.18009172947146, -86.50532032941677, 24.01409824083091,
        -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
    private static final double EPSILON = 1e-15;
    private static final double TINY = 1e-300;

    private Distributions() {}

    /**
     * Returns the natural logarithm of the gamma function (Lanczos approximation).
     *
     * @param x a positive number
     * @return ln Γ(x)
     */
    public static double logGamma(double x) {
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double series = 1.000000000190015;
        for (double coefficient : LANCZOS) {
            series += coefficient / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * series / x);
    }

    /**
     * Returns the regularized incomplete beta function I_x(a, b).
     *
     * @param x between 0 and 1
     * @param a first shape parameter, positive
     * @param b second shape parameter, positive
     * @return the value, between 0 and 1
     */
    public static double incompleteBeta(double x, double a, double b) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) {
            return front * betaContinuedFraction(x, a, b) / a;
        }
        return 1 - front * betaContinuedFraction(1 - x, b, a) / b;
    }

    /** Continued fraction of the incomplete beta function, by the modified Lentz method. */
    private static double betaContinuedFraction(double x, double a, double b) {
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        d = Math.abs(d) < TINY ? TINY : d;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 10_000; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            d = Math.abs(d) < TINY ? TINY : d;
            c = 1 + aa / c;
            c = Math.abs(c) < TINY ? TINY : c;
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            d = Math.abs(d) < TINY ? TINY : d;
            c = 1 + aa / c;
            c = Math.abs(c) < TINY ? TINY : c;
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < EPSILON) {
                break;
            }
        }
        return h;
    }

    /**
     * Returns the density of the standard normal distribution.
     *
     * @param z the value
     * @return φ(z)
     */
    public static double normalDensity(double z) {
        return Math.exp(-0.5 * z * z) / Math.sqrt(2 * Math.PI);
    }

    /**
     * Returns the cumulative distribution function of the standard normal distribution.
     *
     * @param z the value
     * @return Φ(z), the probability of a value below z
     */
    public static double normalCdf(double z) {
        return 0.5 * erfc(-z / Math.sqrt(2));
    }

    /**
     * Complementary error function, from the incomplete gamma function of order 1/2: its series
     * below 2, its continued fraction above (Numerical Recipes, section 6.2).
     */
    static double erfc(double x) {
        if (x < 0) {
            return 2 - erfc(-x);
        }
        double x2 = x * x;
        if (x < 2) {
            // erf(x) = 2/sqrt(pi) * sum x^(2n+1) (-1)^n / (n! (2n+1)), via P(1/2, x^2).
            double sum = 1.0 / 0.5;
            double term = 1.0 / 0.5;
            for (int n = 1; n < 500; n++) {
                term *= x2 / (0.5 + n);
                sum += term;
                if (term < sum * EPSILON) {
                    break;
                }
            }
            double lower = sum * Math.exp(-x2 + 0.5 * Math.log(x2) - logGamma(0.5));
            return 1 - lower;
        }
        // Continued fraction of the upper incomplete gamma function Q(1/2, x^2).
        double b = x2 + 0.5;
        double c = 1 / TINY;
        double d = 1 / b;
        double h = d;
        for (int i = 1; i < 500; i++) {
            double an = -i * (i - 0.5);
            b += 2;
            d = an * d + b;
            d = Math.abs(d) < TINY ? TINY : d;
            c = b + an / c;
            c = Math.abs(c) < TINY ? TINY : c;
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < EPSILON) {
                break;
            }
        }
        return Math.exp(-x2 + 0.5 * Math.log(x2) - logGamma(0.5)) * h;
    }

    /**
     * Returns the cumulative distribution function of Student's t distribution.
     *
     * @param t the value
     * @param degrees degrees of freedom, positive
     * @return the probability of a value below t
     */
    public static double studentCdf(double t, double degrees) {
        double tail = 0.5 * incompleteBeta(degrees / (degrees + t * t), degrees / 2, 0.5);
        return t >= 0 ? 1 - tail : tail;
    }

    /**
     * Returns the quantile of Student's t distribution, by bisection.
     *
     * @param probability between 0 and 1, exclusive
     * @param degrees degrees of freedom, positive
     * @return t such that the probability of a value below t is the given probability
     */
    public static double studentQuantile(double probability, double degrees) {
        if (!(probability > 0 && probability < 1)) {
            throw new IllegalArgumentException("The probability must be between 0 and 1");
        }
        double low = -1e6;
        double high = 1e6;
        for (int i = 0; i < 200; i++) {
            double mid = (low + high) / 2;
            if (studentCdf(mid, degrees) < probability) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /**
     * Returns the probability that Fisher's F exceeds a value.
     *
     * @param f the value of the statistic
     * @param numerator degrees of freedom of the numerator
     * @param denominator degrees of freedom of the denominator
     * @return the upper tail probability, the p-value of an F test
     */
    public static double fisherUpperTail(double f, double numerator, double denominator) {
        if (f <= 0) {
            return 1;
        }
        return incompleteBeta(denominator / (denominator + numerator * f),
                denominator / 2, numerator / 2);
    }
}
