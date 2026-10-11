package com.bioinfo.cytoscope.desktop.view.plot;

/** Drawing tools of the plots. */
public enum PlotTool {
    /** Click a gate to open its population; drag it to move it. */
    SELECT(false, false),
    /** Drag diagonally. */
    RECTANGLE(true, false),
    /** Click each vertex, then double-click or click the first vertex. */
    POLYGON(true, false),
    /** Click the crossing point of four regions. */
    QUADRANT(true, false),
    /** Drag along a histogram. */
    INTERVAL(false, true);

    private final boolean needsDotPlot;
    private final boolean needsHistogram;

    PlotTool(boolean needsDotPlot, boolean needsHistogram) {
        this.needsDotPlot = needsDotPlot;
        this.needsHistogram = needsHistogram;
    }

    /**
     * Returns whether the tool can draw on a plot.
     *
     * @param histogram whether the plot is a histogram
     * @return false for the two-channel gates on a histogram, and the interval on a dot plot
     */
    public boolean availableOn(boolean histogram) {
        return histogram ? !needsDotPlot : !needsHistogram;
    }
}
