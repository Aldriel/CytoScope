package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.fcs.Channel;
import com.bioinfo.cytoscope.gating.Quadrant;
import com.bioinfo.cytoscope.report.ReportTexts;
import com.bioinfo.cytoscope.service.ExampleLibrary;

import java.time.ZoneId;
import java.util.List;

/** Texts displayed by the views, shared by the controllers. */
final class Texts {

    /** Shown in place of a missing value. */
    static final String DASH = "—";

    private Texts() {}

    /**
     * Formats a percentage with one decimal, as written in the language of {@code m}.
     *
     * @param m messages of the current language
     * @param value percentage, from 0 to 100; NaN for a missing value
     * @return such as {@code 2.1%} or {@code 2,1 %}
     */
    static String percent(Messages m, double value) {
        return Double.isNaN(value)
                ? DASH
                : m.get("format.percent", String.format(m.locale(), "%.1f", value));
    }

    /**
     * Formats a number with a fixed number of decimals, as written in the language of {@code m}.
     *
     * @param m messages of the current language
     * @param value the number; NaN for a missing value
     * @param decimals number of decimals
     * @return the text
     */
    static String number(Messages m, double value, int decimals) {
        return Double.isNaN(value)
                ? DASH
                : String.format(m.locale(), "%." + decimals + "f", value);
    }

    /**
     * Formats a count of events with thousands separators.
     *
     * @param m messages of the current language
     * @param count the count
     * @return such as {@code 12,345} or {@code 12 345}
     */
    static String count(Messages m, int count) {
        return String.format(m.locale(), "%,d", count);
    }

    /**
     * Formats a value typed by the user, without useless decimals: {@code 2.5}, {@code 10}.
     *
     * @param value the value; NaN for an empty field
     * @return the text of the field
     */
    static String editable(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        double rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.rint(rounded)
                ? Long.toString((long) rounded)
                : Double.toString(rounded);
    }

    /**
     * Reads a number typed by the user, with a point or a comma as decimal separator.
     *
     * @param text the text of the field
     * @return the value, NaN for an empty field, or null if the text is not a number
     */
    static Double parse(String text) {
        String cleaned = text.strip().replace(',', '.').replaceAll("[\\s\u00a0\u202f]", "");
        if (cleaned.isEmpty()) {
            return Double.NaN;
        }
        try {
            double value = Double.parseDouble(cleaned);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the name shown for a channel: its name followed by its stain, if any.
     *
     * @param channels the channels of the tubes
     * @param name name of the channel
     * @return the display name, or the name itself if the channel is unknown
     */
    static String channel(List<Channel> channels, String name) {
        return channels.stream()
                .filter(c -> c.name().equals(name))
                .map(Channel::displayName)
                .findFirst()
                .orElse(name);
    }

    /**
     * Names one region of a quadrant gate, such as {@code FITC-A+ PE-A−}.
     *
     * @param x name of the horizontal channel
     * @param y name of the vertical channel
     * @param quadrant the region
     * @return the name
     */
    static String quadrant(String x, String y, Quadrant quadrant) {
        return x + sign(quadrant.xPositive()) + " " + y + sign(quadrant.yPositive());
    }

    private static String sign(boolean positive) {
        return positive ? "+" : "−";
    }

    /**
     * Names the value compared between groups, with the vocabulary of the report.
     *
     * @param m messages of the current language
     * @param experiment the experiment, to name a population
     * @param metric the value
     * @return such as {@code % apoptotic cells}
     */
    static String metric(Messages m, Experiment experiment, Metric metric) {
        return new ReportTexts(m.locale(), ZoneId.systemDefault())
                .metric(metric, experiment.gating());
    }

    /**
     * Returns the name of the population of all events, in the language of {@code m}.
     *
     * @param m messages of the current language
     * @return such as {@code All events}
     */
    static String rootName(Messages m) {
        return m.get("population.root");
    }

    /**
     * Returns the names of the populations created by the examples, in the language of {@code m}.
     *
     * @param m messages of the current language
     * @return the names
     */
    static ExampleLibrary.PresetNames presetNames(Messages m) {
        return m.locale().getLanguage().equals("fr")
                ? ExampleLibrary.PresetNames.FRENCH
                : ExampleLibrary.PresetNames.ENGLISH;
    }

    /**
     * Returns the translated name of a constant, such as {@code fate.LIVE}.
     *
     * @param m messages of the current language
     * @param prefix group of the key
     * @param value the constant
     * @return the text
     */
    static String of(Messages m, String prefix, Enum<?> value) {
        return m.get(prefix + "." + value.name());
    }
}
