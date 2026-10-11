package com.bioinfo.cytoscope.experiment;

import com.bioinfo.cytoscope.stats.FourParameterLogistic;
import java.util.List;

/**
 * Dose-response curve of a metric: the points of the samples that have a dose, and the fitted
 * four-parameter logistic model.
 *
 * @param points dose and value of each sample, by increasing dose
 * @param fit the fitted curve
 */
public record DoseResponse(List<Point> points, FourParameterLogistic fit) {

    /**
     * One sample of the curve.
     *
     * @param sampleName the sample
     * @param dose its dose
     * @param value its value of the metric
     */
    public record Point(String sampleName, double dose, double value) {}

    /** Creates the curve and copies the points. */
    public DoseResponse {
        points = List.copyOf(points);
    }

    /**
     * Fits the curve to points.
     *
     * @param points dose and value of each sample
     * @return the curve
     * @throws IllegalArgumentException if the points are too few to fit the model
     */
    public static DoseResponse of(List<Point> points) {
        List<Point> sorted = points.stream()
                .filter(p -> !Double.isNaN(p.dose()) && !Double.isNaN(p.value()))
                .sorted((a, b) -> Double.compare(a.dose(), b.dose()))
                .toList();
        double[] doses = sorted.stream().mapToDouble(Point::dose).toArray();
        double[] values = sorted.stream().mapToDouble(Point::value).toArray();
        return new DoseResponse(sorted, FourParameterLogistic.fit(doses, values));
    }
}
