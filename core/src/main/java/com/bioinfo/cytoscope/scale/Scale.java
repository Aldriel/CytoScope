package com.bioinfo.cytoscope.scale;

/**
 * Converts the values of a channel to positions on a plot axis, between 0 (bottom or left) and
 * 1 (top or right). Each implementation is one way of spreading the values (Strategy pattern):
 * the gates keep their coordinates in data values, whatever the scale used to draw them.
 */
public interface Scale {

    /**
     * Returns the kind of scale.
     *
     * @return the type
     */
    ScaleType type();

    /**
     * Converts a value to an axis position.
     *
     * @param value the measured value
     * @return the position; 0 to 1 inside the displayed range, outside it otherwise
     */
    double apply(double value);

    /**
     * Converts an axis position back to a value.
     *
     * @param position the position, 0 to 1 inside the displayed range
     * @return the value at this position
     */
    double invert(double position);

    /**
     * Returns the values at which the axis shows a labeled tick.
     *
     * @return tick values, in increasing order, inside the displayed range
     */
    double[] ticks();
}
