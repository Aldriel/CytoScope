package com.bioinfo.cytoscope.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Reference values computed with scipy 1.14 and numpy 2.1. */
class StatisticsTest {

    private static final double[] G0 = {10.2, 11.1, 9.8};
    private static final double[] G1 = {11.5, 12.0, 10.9};
    private static final double[] G2 = {15.3, 16.1, 14.8};
    private static final double[] G3 = {9.9, 10.5, 11.2, 10.0};

    @Test
    void descriptiveStatisticsLikeNumpy() {
        double[] v = {3, 1, 7, 4, 10, 2};
        assertEquals(2.25, Descriptive.percentile(v, 25), 1e-12);
        assertEquals(8.5, Descriptive.percentile(v, 90), 1e-12);
        assertEquals(3.5, Descriptive.median(v), 1e-12);
        assertEquals(3.391164991562634, Descriptive.standardDeviation(v), 1e-12);
        assertEquals(4.5, Descriptive.mean(v), 1e-12);
        assertEquals(1, Descriptive.percentile(v, -5));
        assertEquals(10, Descriptive.percentile(v, 150));
        assertTrue(Double.isNaN(Descriptive.percentile(new double[0], 50)));
        assertTrue(Double.isNaN(Descriptive.mean(new double[0])));
        assertTrue(Double.isNaN(Descriptive.standardDeviation(new double[] {1})));
        assertEquals(2, Descriptive.mean(new double[] {1, Double.NaN, 3}));
        assertEquals(100 * 3.391164991562634 / 4.5, Descriptive.coefficientOfVariation(v), 1e-9);
        assertTrue(Double.isNaN(Descriptive.coefficientOfVariation(new double[] {-1, 1})));
        assertTrue(Double.isNaN(Descriptive.robustCoefficientOfVariation(new double[] {0, 0})));
        assertTrue(Double.isNaN(Descriptive.robustCoefficientOfVariation(new double[0])));
        double[] normal = new double[20_001];
        for (int i = 0; i < normal.length; i++) {
            normal[i] = 1000 + 50 * inverseNormal((i + 0.5) / normal.length);
        }
        assertEquals(5, Descriptive.robustCoefficientOfVariation(normal), 0.01);
    }

    private static double inverseNormal(double p) {
        double low = -10;
        double high = 10;
        for (int i = 0; i < 100; i++) {
            double mid = (low + high) / 2;
            if (Distributions.normalCdf(mid) < p) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    @Test
    void distributionsLikeScipy() {
        assertEquals(0.9490302605850708, Distributions.studentCdf(2, 5), 1e-10);
        assertEquals(0.10900858554175705, Distributions.studentCdf(-1.3, 12), 1e-10);
        assertEquals(2.2281388519862744, Distributions.studentQuantile(0.975, 10), 1e-8);
        assertEquals(1.637744353696209, Distributions.studentQuantile(0.9, 3), 1e-8);
        assertEquals(0.5248, Distributions.incompleteBeta(0.4, 2, 3), 1e-12);
        assertEquals(0.9999999932897103, Distributions.incompleteBeta(0.9, 0.5, 7.5), 1e-12);
        assertEquals(0, Distributions.incompleteBeta(0, 2, 3));
        assertEquals(1, Distributions.incompleteBeta(1, 2, 3));
        assertEquals(0.03481542554547851, Distributions.fisherUpperTail(4.5, 2, 12), 1e-10);
        assertEquals(1, Distributions.fisherUpperTail(0, 2, 12));
        assertEquals(0.9031995154143897, Distributions.normalCdf(1.3), 1e-12);
        assertEquals(1 - 0.9031995154143897, Distributions.normalCdf(-1.3), 1e-12);
        assertEquals(0.9986501019683699, Distributions.normalCdf(3), 1e-12);
        assertEquals(0.17136859204780736, Distributions.normalDensity(1.3), 1e-14);
        assertEquals(2.4537365708424423, Distributions.logGamma(4.5), 1e-10);
        assertEquals(1.0957979948180756, Distributions.logGamma(0.3), 1e-10);
        assertThrows(IllegalArgumentException.class, () -> Distributions.studentQuantile(1, 3));
        assertThrows(IllegalArgumentException.class, () -> Distributions.studentQuantile(0, 3));
    }

    @Test
    void anovaLikeScipy() {
        OneWayAnova anova = OneWayAnova.of(List.of(G0, G1, G2, G3));
        assertEquals(46.77178485576924, anova.f(), 1e-9);
        assertEquals(8.173608663608451e-06, anova.pValue(), 1e-12);
        assertEquals(3, anova.betweenDegrees());
        assertEquals(9, anova.withinDegrees());
        assertEquals(4, anova.groupCount());
        assertEquals(13, anova.totalCount());
        OneWayAnova withEmpty = OneWayAnova.of(List.of(G0, new double[] {Double.NaN}, G2));
        assertEquals(2, withEmpty.groupCount());
        OneWayAnova constant = OneWayAnova.of(List.of(new double[] {1, 1}, new double[] {2, 2}));
        assertEquals(Double.POSITIVE_INFINITY, constant.f());
        assertEquals(0, constant.pValue());
        OneWayAnova same = OneWayAnova.of(List.of(new double[] {1, 1}, new double[] {1, 1}));
        assertEquals(0, same.f());
        assertEquals(1, same.pValue());
        assertThrows(IllegalArgumentException.class, () -> OneWayAnova.of(List.of(G0)));
        assertThrows(IllegalArgumentException.class,
                () -> OneWayAnova.of(List.of(new double[] {1}, new double[] {2})));
    }

    @Test
    void dunnettLikeScipy() {
        List<Dunnett.Comparison> c = Dunnett.compare(G0, List.of(G1, G2, G3));
        assertEquals(2.187611604296, c.get(0).t(), 1e-9);
        assertEquals(10.009980371172, c.get(1).t(), 1e-9);
        assertEquals(0.070868338689, c.get(2).t(), 1e-9);
        // scipy.stats.dunnett integrates by quasi-Monte Carlo (0.1306, 4e-6 or 2e-6 depending
        // on the seed); these values come from adaptive quadrature of the same integral.
        assertEquals(0.13070047799234416, c.get(0).adjustedP(), 1e-6);
        assertEquals(9.516404313574279e-06, c.get(1).adjustedP(), 1e-10);
        assertEquals(0.9997147457877062, c.get(2).adjustedP(), 1e-6);
        assertEquals(1.1, c.get(0).difference(), 1e-9);
        List<Dunnett.Comparison> single = Dunnett.compare(new double[] {10.0, 11.0, 10.4},
                List.of(new double[] {12.0, 13.1, 12.5}));
        // With one treated group, Dunnett's test is Student's t test (scipy.stats.ttest_ind).
        assertEquals(0.008662664363522907, single.get(0).adjustedP(), 1e-10);
        assertThrows(IllegalArgumentException.class,
                () -> Dunnett.compare(new double[0], List.of(G1)));
    }

    @Test
    void dunnettWithoutVariance() {
        List<Dunnett.Comparison> c = Dunnett.compare(new double[] {1, 1},
                List.of(new double[] {2, 2}, new double[] {1, 1}));
        assertEquals(0, c.get(0).adjustedP(), 1e-12);
        assertEquals(0, c.get(1).t());
        assertEquals(1, c.get(1).adjustedP(), 1e-9);
    }

    /** Dunnett's tables (two-sided, alpha 0.05). */
    @Test
    void dunnettCriticalValuesLikeTheTables() {
        assertEquals(2.21, Dunnett.criticalValue(0.05, new int[] {10, 10}, 10,
                Double.POSITIVE_INFINITY), 0.01);
        assertEquals(2.68, Dunnett.criticalValue(0.05, new int[] {4, 4, 4}, 4, 12), 0.01);
        assertEquals(2.81, Dunnett.criticalValue(0.05, new int[] {4, 4, 4, 4}, 4, 12), 0.01);
        assertEquals(2.57, Dunnett.criticalValue(0.05, new int[] {6}, 6, 5), 0.01);
    }

    @Test
    void fourParameterLogisticLikeCurveFit() {
        double[] doses = {0.1, 0.1, 0.1, 0.3, 0.3, 0.3, 1, 1, 1, 3, 3, 3, 10, 10, 10, 30, 30, 30,
            100, 100, 100};
        double[] responses = {5.8891, 6.6329, 5.2006, 5.9809, 7.0707, 5.7283, 17.0968, 20.297,
            15.7159, 34.5395, 37.3153, 36.9829, 61.5267, 58.937, 61.19, 75.6018, 70.503, 72.7195,
            73.7035, 75.2327, 73.8522};
        FourParameterLogistic fit = FourParameterLogistic.fit(doses, responses);
        assertEquals(4.458573440588, fit.bottom(), 1e-3);
        assertEquals(76.210772136055, fit.top(), 1e-3);
        assertEquals(3.5303041144849816, fit.ec50(), 1e-4);
        assertEquals(1.255292309238, fit.hill(), 1e-4);
        assertEquals(3.1614600086284264, fit.ec50Low(), 1e-3);
        assertEquals(3.942180861606591, fit.ec50High(), 1e-3);
        assertEquals(0.9967243047713481, fit.rSquared(), 1e-6);
        assertEquals(21, fit.pointCount());
        assertEquals(fit.bottom(), fit.predict(0), 1e-12);
        assertEquals((fit.bottom() + fit.top()) / 2, fit.predict(fit.ec50()), 1e-9);
    }

    @Test
    void fallingCurveIsReportedWithAPositiveSlope() {
        double[] doses = {0, 1, 2, 4, 8, 16, 32};
        double[] responses = new double[doses.length];
        for (int i = 0; i < doses.length; i++) {
            responses[i] = doses[i] == 0 ? 100 : 10 + 90 / (1 + Math.pow(doses[i] / 5, 2));
        }
        FourParameterLogistic fit = FourParameterLogistic.fit(doses, responses);
        assertEquals(100, fit.bottom(), 1e-3);
        assertEquals(10, fit.top(), 1e-3);
        assertEquals(5, fit.ec50(), 1e-3);
        assertEquals(2, fit.hill(), 1e-3);
        assertEquals(1, fit.rSquared(), 1e-9);
        assertTrue(Double.isNaN(fit.ec50Low()) || fit.ec50Low() <= 5.0001);
    }

    @Test
    void rejectsPointsThatCannotBeFitted() {
        assertThrows(IllegalArgumentException.class,
                () -> FourParameterLogistic.fit(new double[] {1, 2}, new double[] {1}));
        assertThrows(IllegalArgumentException.class,
                () -> FourParameterLogistic.fit(new double[] {1, 2, 3, 4}, new double[4]));
        assertThrows(IllegalArgumentException.class, () -> FourParameterLogistic.fit(
                new double[] {1, 2, -3, 4, 5}, new double[5]));
        assertThrows(IllegalArgumentException.class, () -> FourParameterLogistic.fit(
                new double[] {0, 0, 1, 1, 1}, new double[5]));
        FourParameterLogistic flat = FourParameterLogistic.fit(new double[] {1, 2, 3, 4, 5},
                new double[] {2, 2, 2, 2, 2});
        assertEquals(1, flat.rSquared());
    }

    @Test
    void solvesLinearSystems() {
        assertArrayEquals(new double[] {1, 2},
                FourParameterLogistic.solve(new double[][] {{0, 1}, {1, 0}}, new double[] {2, 1}),
                1e-12);
        assertNull(FourParameterLogistic.solve(new double[][] {{0, 0}, {0, 0}},
                new double[] {1, 1}));
    }
}
