package com.bioinfo.cytoscope.gating;

/**
 * A region of one or two channels that selects events. The coordinates are data values, so a
 * gate keeps the same meaning whatever the scale used to draw it.
 *
 * <p>Each kind of region is an implementation (Strategy pattern); the gating engine only asks
 * whether an event lies inside.
 */
public sealed interface Gate permits IntervalGate, RectangleGate, PolygonGate, QuadrantGate {

    /**
     * Returns the channel of the horizontal axis.
     *
     * @return the channel name
     */
    String xChannel();

    /**
     * Returns the channel of the vertical axis.
     *
     * @return the channel name, or null for a gate on a single channel
     */
    String yChannel();

    /**
     * Tells whether an event lies inside the gate.
     *
     * @param x value of the event on the horizontal channel
     * @param y value on the vertical channel; ignored by gates on a single channel
     * @return true if the event is selected
     */
    boolean contains(double x, double y);
}
