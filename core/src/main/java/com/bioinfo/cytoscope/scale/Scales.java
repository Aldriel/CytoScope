package com.bioinfo.cytoscope.scale;

import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.stats.Descriptive;
import java.util.Locale;

/** Creates the scale of a channel, with bounds suited to its range and to its values. */
public final class Scales {

    /** Cofactor of the arcsinh scale for conventional cytometers. */
    public static final double ARCSINH_COFACTOR = 150;

    private Scales() {}

    /**
     * Creates a scale.
     *
     * @param type kind of scale chosen by the user
     * @param channel the channel shown on the axis
     * @param values values of the channel, used to fit the bounds; may be empty
     * @return the scale
     */
    public static Scale create(ScaleType type, Channel channel, double[] values) {
        double range = channel.range();
        double low = values.length == 0 ? 0 : Descriptive.percentile(values, 0.5);
        return switch (type) {
            case LINEAR -> new LinearScale(Math.min(0, low), range);
            case LOG -> new LogScale(Math.max(1, range / 1e5), range);
            case ARCSINH -> new ArcsinhScale(Math.min(-ARCSINH_COFACTOR, low * 1.1), range,
                    ARCSINH_COFACTOR);
            case LOGICLE -> LogicleScale.forData(range,
                    values.length == 0 ? 0 : Descriptive.percentile(values, 5));
        };
    }

    /**
     * Returns the scale that suits a channel by default: linear for light scatter, time and pulse
     * width ({@code -W}), logicle for fluorescence.
     *
     * @param channel the channel
     * @return the type of scale
     */
    public static ScaleType defaultType(Channel channel) {
        boolean width = channel.name().toUpperCase(Locale.ROOT).endsWith("-W");
        return channel.isFluorescence() && !width ? ScaleType.LOGICLE : ScaleType.LINEAR;
    }

    /**
     * Returns the default scale of a channel, linear for the DNA content of a cell cycle
     * analysis, whose peaks are read on a linear axis.
     *
     * @param channel the channel
     * @param dnaChannel name of the DNA channel, or null
     * @return the type of scale
     */
    public static ScaleType defaultType(Channel channel, String dnaChannel) {
        return channel.name().equalsIgnoreCase(dnaChannel) ? ScaleType.LINEAR
                : defaultType(channel);
    }
}
