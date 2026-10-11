package com.bioinfo.cytoscope.gating;

import java.util.Objects;

/**
 * One of the four regions cut by a horizontal and a vertical threshold. A set of quadrants is
 * four sibling populations that share the same thresholds.
 *
 * @param xChannel channel of the horizontal axis
 * @param yChannel channel of the vertical axis
 * @param xThreshold value that separates negative and positive events horizontally
 * @param yThreshold value that separates negative and positive events vertically
 * @param quadrant the region selected
 */
public record QuadrantGate(String xChannel, String yChannel, double xThreshold,
                           double yThreshold, Quadrant quadrant) implements Gate {

    /** Creates the gate and checks its fields. */
    public QuadrantGate {
        Objects.requireNonNull(xChannel, "xChannel");
        Objects.requireNonNull(yChannel, "yChannel");
        Objects.requireNonNull(quadrant, "quadrant");
        if (!Double.isFinite(xThreshold) || !Double.isFinite(yThreshold)) {
            throw new IllegalArgumentException("The thresholds must be numbers");
        }
    }

    /** An event exactly on a threshold is counted as positive. */
    @Override
    public boolean contains(double x, double y) {
        return (x >= xThreshold) == quadrant.xPositive()
                && (y >= yThreshold) == quadrant.yPositive();
    }

    /**
     * Returns the same region with other thresholds.
     *
     * @param newX new horizontal threshold
     * @param newY new vertical threshold
     * @return the moved gate
     */
    public QuadrantGate withThresholds(double newX, double newY) {
        return new QuadrantGate(xChannel, yChannel, newX, newY, quadrant);
    }
}
