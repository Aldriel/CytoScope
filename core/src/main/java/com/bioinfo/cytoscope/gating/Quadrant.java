package com.bioinfo.cytoscope.gating;

/**
 * The four regions of a quadrant gate, named after the sign of each channel: the first sign is
 * the horizontal channel, the second the vertical one.
 */
public enum Quadrant {
    /** Negative for both channels (bottom left). */
    NEGATIVE_NEGATIVE(false, false),
    /** Positive for the horizontal channel only (bottom right). */
    POSITIVE_NEGATIVE(true, false),
    /** Positive for both channels (top right). */
    POSITIVE_POSITIVE(true, true),
    /** Positive for the vertical channel only (top left). */
    NEGATIVE_POSITIVE(false, true);

    private final boolean xPositive;
    private final boolean yPositive;

    Quadrant(boolean xPositive, boolean yPositive) {
        this.xPositive = xPositive;
        this.yPositive = yPositive;
    }

    /**
     * Tells whether the region is above the horizontal threshold.
     *
     * @return true for the regions on the right
     */
    public boolean xPositive() {
        return xPositive;
    }

    /**
     * Tells whether the region is above the vertical threshold.
     *
     * @return true for the upper regions
     */
    public boolean yPositive() {
        return yPositive;
    }

    /**
     * Returns the short notation used in cytometry, such as {@code +/-}.
     *
     * @return the signs of the horizontal and vertical channels
     */
    public String signs() {
        return (xPositive ? "+" : "-") + "/" + (yPositive ? "+" : "-");
    }
}
