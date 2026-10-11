package com.bioinfo.cytoscope.plot;

import com.bioinfo.cytoscope.experiment.DoseResponse;
import com.bioinfo.cytoscope.experiment.GroupComparison;
import com.bioinfo.cytoscope.experiment.GroupComparison.GroupSummary;
import com.bioinfo.cytoscope.gating.Gate;
import com.bioinfo.cytoscope.gating.IntervalGate;
import com.bioinfo.cytoscope.gating.PolygonGate;
import com.bioinfo.cytoscope.gating.RectangleGate;
import com.bioinfo.cytoscope.scale.LinearScale;
import com.bioinfo.cytoscope.scale.Scale;
import com.bioinfo.cytoscope.scale.ScaleType;
import com.bioinfo.cytoscope.stats.FourParameterLogistic;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Draws the figures of the report as images: density plots, DNA histograms, group bar charts
 * and dose-response curves. The pictures are drawn at twice their size, for a sharp print.
 */
public final class Charts {

    /** Width of a figure, in points of the page. */
    public static final int WIDTH = 250;
    /** Height of a figure, in points of the page. */
    public static final int HEIGHT = 220;

    private static final int SCALE = 2;
    private static final int LEFT = 46;
    private static final int RIGHT = 12;
    private static final int TOP = 24;
    private static final int BOTTOM = 34;
    private static final int BINS = 128;
    private static final Color TEXT = new Color(0x0f172a);
    private static final Color MUTED = new Color(0x64748b);
    private static final Color GRID = new Color(0xe2e8f0);
    private static final Color ACCENT = new Color(0x0f766e);
    private static final Color GATE = new Color(0xdc2626);
    private static final Color[] PHASES = {
        new Color(0x94a3b8), new Color(0x2563eb), new Color(0xd97706), new Color(0x16a34a),
        new Color(0x7c3aed)};

    private Charts() {}

    /**
     * A density plot of two channels.
     *
     * @param title title above the plot
     * @param x values of the horizontal channel
     * @param y values of the vertical channel
     * @param xScale scale of the horizontal axis
     * @param yScale scale of the vertical axis
     * @param xLabel name of the horizontal channel
     * @param yLabel name of the vertical channel
     * @param gates gates drawn over the events
     * @param quadrant thresholds of a quadrant cross, null for none
     * @param quadrantLabels texts in the four corners, from bottom left counterclockwise
     */
    public record DotPlot(String title, double[] x, double[] y, Scale xScale, Scale yScale,
                          String xLabel, String yLabel, List<Gate> gates, double[] quadrant,
                          List<String> quadrantLabels) {}

    /**
     * Draws a density plot: the events are counted on a grid and the colors go from blue for
     * rare to red for frequent, on a logarithmic scale.
     *
     * @param plot the plot
     * @return the picture
     */
    public static BufferedImage dotPlot(DotPlot plot) {
        Canvas c = new Canvas(plot.title());
        Density density = Density.of(plot.x(), plot.y(), plot.xScale(), plot.yScale(), BINS);
        double cell = (double) c.plotWidth() / BINS;
        double cellHeight = (double) c.plotHeight() / BINS;
        for (int bx = 0; bx < BINS; bx++) {
            for (int by = 0; by < BINS; by++) {
                int count = density.count(bx, by);
                if (count > 0) {
                    double level = Math.log1p(count) / Math.log1p(density.max());
                    c.g.setColor(heat(level));
                    c.g.fillRect((int) Math.floor(LEFT + bx * cell),
                            (int) Math.floor(TOP + c.plotHeight() - (by + 1) * cellHeight),
                            (int) Math.ceil(cell), (int) Math.ceil(cellHeight));
                }
            }
        }
        c.axes(plot.xScale(), plot.yScale(), plot.xLabel(), plot.yLabel());
        c.g.setColor(GATE);
        c.g.setStroke(new BasicStroke(1.2f));
        for (Gate gate : plot.gates()) {
            drawGate(c, gate, plot.xScale(), plot.yScale());
        }
        if (plot.quadrant() != null) {
            int qx = c.x(plot.xScale().apply(plot.quadrant()[0]));
            int qy = c.y(plot.yScale().apply(plot.quadrant()[1]));
            c.g.drawLine(qx, TOP, qx, TOP + c.plotHeight());
            c.g.drawLine(LEFT, qy, LEFT + c.plotWidth(), qy);
            List<String> labels = plot.quadrantLabels();
            c.g.setFont(c.font(Font.BOLD, 8));
            c.corner(labels.get(0), false, false);
            c.corner(labels.get(1), true, false);
            c.corner(labels.get(2), true, true);
            c.corner(labels.get(3), false, true);
        }
        return c.done();
    }

    private static void drawGate(Canvas c, Gate gate, Scale xScale, Scale yScale) {
        if (gate instanceof RectangleGate r) {
            int x1 = c.x(xScale.apply(r.xMin()));
            int x2 = c.x(xScale.apply(r.xMax()));
            int y1 = c.y(yScale.apply(r.yMax()));
            int y2 = c.y(yScale.apply(r.yMin()));
            c.g.drawRect(x1, y1, x2 - x1, y2 - y1);
        } else if (gate instanceof IntervalGate i) {
            int x1 = c.x(xScale.apply(i.min()));
            int x2 = c.x(xScale.apply(i.max()));
            c.g.drawLine(x1, TOP, x1, TOP + c.plotHeight());
            c.g.drawLine(x2, TOP, x2, TOP + c.plotHeight());
        } else if (gate instanceof PolygonGate p) {
            Path2D path = new Path2D.Double();
            double[] xs = p.xs();
            double[] ys = p.ys();
            for (int i = 0; i < xs.length; i++) {
                double px = c.x(xScale.apply(xs[i]));
                double py = c.y(yScale.apply(ys[i]));
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            path.closePath();
            c.g.draw(path);
        }
    }

    /**
     * Draws a histogram of DNA content with the bounds of the phases, each phase in its color.
     *
     * @param title title above the plot
     * @param values DNA content of the cells
     * @param scale scale of the axis
     * @param label name of the channel
     * @param bounds the four bounds between the five phases
     * @return the picture
     */
    public static BufferedImage histogram(String title, double[] values, Scale scale,
                                          String label, double[] bounds) {
        Canvas c = new Canvas(title);
        int[] counts = Density.histogram(values, scale, BINS * 2);
        int max = Math.max(1, Arrays.stream(counts).max().orElse(0));
        double width = (double) c.plotWidth() / counts.length;
        for (int b = 0; b < counts.length; b++) {
            double center = scale.invert((b + 0.5) / counts.length);
            int phase = 0;
            while (phase < bounds.length && center >= bounds[phase]) {
                phase++;
            }
            c.g.setColor(PHASES[phase]);
            int h = (int) Math.round((double) counts[b] / max * c.plotHeight() * 0.95);
            c.g.fillRect((int) Math.floor(LEFT + b * width), TOP + c.plotHeight() - h,
                    (int) Math.ceil(width), h);
        }
        c.axes(scale, new LinearScale(0, max / 0.95), label, "");
        return c.done();
    }

    /**
     * Draws the mean and standard deviation of each group, with the replicates as dots and the
     * stars of Dunnett's test above the treated groups.
     *
     * @param title title above the plot
     * @param comparison the comparison
     * @param yLabel name of the compared value
     * @return the picture
     */
    public static BufferedImage barChart(String title, GroupComparison comparison,
                                         String yLabel) {
        Canvas c = new Canvas(title);
        List<GroupSummary> groups = comparison.groups();
        double top = 0;
        for (GroupSummary g : groups) {
            top = Math.max(top, g.mean() + (Double.isNaN(g.standardDeviation()) ? 0
                    : g.standardDeviation()));
            for (double v : g.values()) {
                top = Math.max(top, v);
            }
        }
        LinearScale yScale = new LinearScale(0, top <= 0 ? 1 : top * 1.18);
        c.axes(null, yScale, "", yLabel);
        double slot = (double) c.plotWidth() / groups.size();
        c.g.setFont(c.font(Font.PLAIN, 7));
        FontMetrics metrics = c.g.getFontMetrics();
        for (int i = 0; i < groups.size(); i++) {
            GroupSummary g = groups.get(i);
            int center = (int) Math.round(LEFT + slot * (i + 0.5));
            int half = (int) Math.max(3, slot * 0.3);
            int barTop = c.y(yScale.apply(g.mean()));
            c.g.setColor(i == 0 ? new Color(0xcbd5e1) : new Color(0x99f6e4));
            c.g.fillRect(center - half, barTop, 2 * half, TOP + c.plotHeight() - barTop);
            c.g.setColor(TEXT);
            c.g.drawRect(center - half, barTop, 2 * half, TOP + c.plotHeight() - barTop);
            if (!Double.isNaN(g.standardDeviation())) {
                int hi = c.y(yScale.apply(g.mean() + g.standardDeviation()));
                int lo = c.y(yScale.apply(Math.max(0, g.mean() - g.standardDeviation())));
                c.g.drawLine(center, hi, center, lo);
                c.g.drawLine(center - half / 2, hi, center + half / 2, hi);
            }
            double[] values = g.values();
            for (int k = 0; k < values.length; k++) {
                int dx = values.length == 1 ? 0
                        : (int) Math.round((k - (values.length - 1) / 2.0) * half / values.length);
                int dy = c.y(yScale.apply(values[k]));
                c.g.setColor(ACCENT);
                c.g.fillOval(center + dx - 2, dy - 2, 4, 4);
            }
            c.g.setColor(TEXT);
            String stars = g.stars();
            if (!stars.isEmpty()) {
                double upper = g.mean() + (Double.isNaN(g.standardDeviation()) ? 0
                        : g.standardDeviation());
                for (double v : values) {
                    upper = Math.max(upper, v);
                }
                c.g.setFont(c.font(Font.BOLD, 9));
                int sw = c.g.getFontMetrics().stringWidth(stars);
                c.g.drawString(stars, center - sw / 2, c.y(yScale.apply(upper)) - 3);
                c.g.setFont(c.font(Font.PLAIN, 7));
            }
            String name = fit(g.name(), metrics, (int) slot - 2);
            c.g.drawString(name, center - metrics.stringWidth(name) / 2,
                    TOP + c.plotHeight() + 11);
        }
        return c.done();
    }

    /**
     * Draws the dose-response points and the fitted curve on a logarithmic dose axis; the
     * samples without dose (dose 0) are drawn at the left edge, past a break of the axis.
     *
     * @param title title above the plot
     * @param curve the curve
     * @param xLabel name of the dose, with its unit
     * @param yLabel name of the value
     * @return the picture
     */
    public static BufferedImage doseResponse(String title, DoseResponse curve, String xLabel,
                                             String yLabel) {
        Canvas c = new Canvas(title);
        double minDose = Double.MAX_VALUE;
        double maxDose = 0;
        double top = 0;
        double bottom = 0;
        for (DoseResponse.Point p : curve.points()) {
            if (p.dose() > 0) {
                minDose = Math.min(minDose, p.dose());
                maxDose = Math.max(maxDose, p.dose());
            }
            top = Math.max(top, p.value());
            bottom = Math.min(bottom, p.value());
        }
        double logMin = Math.floor(Math.log10(minDose)) - 0.3;
        double logMax = Math.ceil(Math.log10(maxDose) + 0.05);
        LinearScale yScale = new LinearScale(bottom, top * 1.12);
        c.axes(null, yScale, xLabel, yLabel);
        double zeroSlot = 0.08;
        c.g.setFont(c.font(Font.PLAIN, 7));
        c.g.setColor(MUTED);
        for (int e = (int) Math.ceil(logMin); e <= logMax; e++) {
            int px = c.x(zeroSlot + (1 - zeroSlot) * (e - logMin) / (logMax - logMin));
            c.g.drawLine(px, TOP + c.plotHeight(), px, TOP + c.plotHeight() + 3);
            String text = tick(Math.pow(10, e));
            c.g.drawString(text, px - c.g.getFontMetrics().stringWidth(text) / 2,
                    TOP + c.plotHeight() + 11);
        }
        boolean hasZero = curve.points().stream().anyMatch(p -> p.dose() <= 0);
        if (hasZero) {
            int px = c.x(zeroSlot / 2);
            c.g.drawString("0", px - 2, TOP + c.plotHeight() + 11);
            int breakX = c.x(zeroSlot * 0.85);
            c.g.setColor(Color.WHITE);
            c.g.fillRect(breakX - 2, TOP + c.plotHeight() - 2, 4, 5);
            c.g.setColor(MUTED);
            c.g.drawLine(breakX - 3, TOP + c.plotHeight() + 3, breakX, TOP + c.plotHeight() - 3);
            c.g.drawLine(breakX, TOP + c.plotHeight() + 3, breakX + 3, TOP + c.plotHeight() - 3);
        }
        FourParameterLogistic fit = curve.fit();
        c.g.setColor(ACCENT);
        c.g.setStroke(new BasicStroke(1.4f));
        Path2D path = new Path2D.Double();
        for (int i = 0; i <= 200; i++) {
            double logDose = logMin + (logMax - logMin) * i / 200.0;
            double px = c.x(zeroSlot + (1 - zeroSlot) * i / 200.0);
            double py = c.y(yScale.apply(fit.predict(Math.pow(10, logDose))));
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        c.g.draw(path);
        if (fit.ec50() > Math.pow(10, logMin) && fit.ec50() < Math.pow(10, logMax)) {
            int ex = c.x(zeroSlot + (1 - zeroSlot)
                    * (Math.log10(fit.ec50()) - logMin) / (logMax - logMin));
            c.g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1,
                    new float[] {3, 3}, 0));
            c.g.drawLine(ex, c.y(yScale.apply(fit.predict(fit.ec50()))), ex,
                    TOP + c.plotHeight());
        }
        c.g.setColor(TEXT);
        for (DoseResponse.Point p : curve.points()) {
            double position = p.dose() <= 0 ? zeroSlot / 2
                    : zeroSlot + (1 - zeroSlot) * (Math.log10(p.dose()) - logMin)
                    / (logMax - logMin);
            c.g.fillOval(c.x(position) - 2, c.y(yScale.apply(p.value())) - 2, 4, 4);
        }
        return c.done();
    }

    /**
     * Formats a tick value: 0, powers of ten as {@code 10³}, thousands as {@code 50K}.
     *
     * @param value the value
     * @return the label
     */
    public static String tick(double value) {
        if (value == 0) {
            return "0";
        }
        double magnitude = Math.abs(value);
        double exponent = Math.log10(magnitude);
        String sign = value < 0 ? "-" : "";
        if (magnitude >= 100 && Math.abs(exponent - Math.rint(exponent)) < 1e-9) {
            return sign + "10" + superscript((int) Math.rint(exponent));
        }
        if (magnitude >= 1000) {
            return sign + trim(magnitude / 1000) + "K";
        }
        return sign + trim(magnitude);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "")
                        .replaceAll("\\.$", "");
    }

    private static String superscript(int exponent) {
        String digits = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        StringBuilder text = new StringBuilder();
        for (char ch : Integer.toString(exponent).toCharArray()) {
            text.append(ch == '-' ? '⁻' : digits.charAt(ch - '0'));
        }
        return text.toString();
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && metrics.stringWidth(shortened + "…") > width) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    /**
     * Returns the color of a density level: blue, cyan, green, yellow, red, the usual colors of
     * cytometry density plots. The desktop application paints its plots with it too.
     *
     * @param level density, from 0 (rare) to 1 (most frequent); clamped to this range
     * @return the color
     */
    public static Color heat(double level) {
        float[][] stops = {{0.12f, 0.25f, 0.85f}, {0.0f, 0.75f, 0.95f}, {0.2f, 0.8f, 0.3f},
            {0.98f, 0.85f, 0.1f}, {0.9f, 0.15f, 0.1f}};
        double position = Math.max(0, Math.min(1, level)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) position);
        float t = (float) (position - i);
        return new Color(stops[i][0] + t * (stops[i + 1][0] - stops[i][0]),
                stops[i][1] + t * (stops[i + 1][1] - stops[i][1]),
                stops[i][2] + t * (stops[i + 1][2] - stops[i][2]));
    }

    /**
     * Returns the ticks of a scale, with 0 and the large values first so that they keep their
     * label when two labels would overlap near 0.
     */
    private static double[] labeledTicks(Scale scale) {
        return Arrays.stream(scale.ticks()).boxed()
                .sorted(Comparator.comparing((Double t) -> t != 0)
                        .thenComparing(t -> -Math.abs(t)))
                .mapToDouble(Double::doubleValue).toArray();
    }

    /** Reserves an interval of an axis for a label, unless it overlaps one already there. */
    private static boolean free(List<int[]> taken, int from, int to) {
        for (int[] interval : taken) {
            if (from < interval[1] + 3 && to > interval[0] - 3) {
                return false;
            }
        }
        taken.add(new int[] {from, to});
        return true;
    }

    /**
     * Labels a tick: thousands on linear axes, powers of ten on the others.
     *
     * @param value value of the tick
     * @param scale scale of the axis
     * @return the label
     */
    public static String tickLabel(double value, Scale scale) {
        return scale.type() == ScaleType.LINEAR ? plain(value) : tick(value);
    }

    /**
     * Formats a value without exponent, thousands as {@code 50K}, for the texts whose font has
     * no superscript digits.
     *
     * @param value the value
     * @return the text
     */
    public static String plain(double value) {
        String sign = value < 0 ? "-" : "";
        double magnitude = Math.abs(value);
        return magnitude >= 1000 ? sign + trim(magnitude / 1000) + "K" : sign + trim(magnitude);
    }

    /** A picture being drawn, with its plot area and axes. */
    private static final class Canvas {

        private final BufferedImage image;
        private final Graphics2D g;

        Canvas(String title) {
            image = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);
            g = image.createGraphics();
            g.scale(SCALE, SCALE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(TEXT);
            g.setFont(font(Font.BOLD, 9));
            g.drawString(fit(title, g.getFontMetrics(), WIDTH - 8), 4, 13);
        }

        Font font(int style, int size) {
            return new Font(Font.SANS_SERIF, style, size);
        }

        int plotWidth() {
            return WIDTH - LEFT - RIGHT;
        }

        int plotHeight() {
            return HEIGHT - TOP - BOTTOM;
        }

        int x(double position) {
            return (int) Math.round(LEFT + position * plotWidth());
        }

        int y(double position) {
            return (int) Math.round(TOP + (1 - position) * plotHeight());
        }

        /** Draws the frame, the ticks of the given scales and the axis titles. */
        void axes(Scale xScale, Scale yScale, String xLabel, String yLabel) {
            g.setStroke(new BasicStroke(0.8f));
            g.setFont(font(Font.PLAIN, 7));
            FontMetrics metrics = g.getFontMetrics();
            if (xScale != null) {
                List<int[]> taken = new ArrayList<>();
                for (double t : labeledTicks(xScale)) {
                    int px = x(xScale.apply(t));
                    g.setColor(MUTED);
                    g.drawLine(px, TOP + plotHeight(), px, TOP + plotHeight() + 3);
                    String text = tickLabel(t, xScale);
                    int left = px - metrics.stringWidth(text) / 2;
                    if (free(taken, left, left + metrics.stringWidth(text))) {
                        g.drawString(text, left, TOP + plotHeight() + 11);
                    }
                }
            }
            if (yScale != null) {
                List<int[]> taken = new ArrayList<>();
                for (double t : labeledTicks(yScale)) {
                    int py = y(yScale.apply(t));
                    g.setColor(GRID);
                    g.drawLine(LEFT, py, LEFT + plotWidth(), py);
                    g.setColor(MUTED);
                    if (free(taken, py - 4, py + 4)) {
                        String text = tickLabel(t, yScale);
                        g.drawString(text, LEFT - 4 - metrics.stringWidth(text), py + 3);
                    }
                }
            }
            g.setColor(TEXT);
            g.drawRect(LEFT, TOP, plotWidth(), plotHeight());
            g.setFont(font(Font.PLAIN, 8));
            FontMetrics labels = g.getFontMetrics();
            g.drawString(xLabel, LEFT + (plotWidth() - labels.stringWidth(xLabel)) / 2,
                    HEIGHT - 6);
            Graphics2D rotated = (Graphics2D) g.create();
            rotated.rotate(-Math.PI / 2);
            rotated.drawString(yLabel, -(TOP + (plotHeight() + labels.stringWidth(yLabel)) / 2),
                    10);
            rotated.dispose();
        }

        /** Writes a text in a corner of the plot area. */
        void corner(String text, boolean right, boolean top) {
            FontMetrics metrics = g.getFontMetrics();
            int px = right ? LEFT + plotWidth() - 3 - metrics.stringWidth(text) : LEFT + 3;
            int py = top ? TOP + 10 : TOP + plotHeight() - 4;
            g.setColor(new Color(255, 255, 255, 200));
            g.fillRect(px - 1, py - 8, metrics.stringWidth(text) + 2, 10);
            g.setColor(GATE.darker());
            g.drawString(text, px, py);
        }

        BufferedImage done() {
            g.dispose();
            return image;
        }
    }
}
