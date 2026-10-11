package com.bioinfo.cytoscope.report;

import com.bioinfo.cytoscope.experiment.Metric;
import com.bioinfo.cytoscope.gating.GatingTree;
import com.bioinfo.cytoscope.gating.Population;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Texts of the report in one language, read from {@code report.properties} (English) and {@code
 * report_fr.properties} (French). Numbers and dates follow the conventions of the language.
 */
public final class ReportTexts {

    private static final String BUNDLE = "com.bioinfo.cytoscope.report.report";

    private final Locale locale;
    private final ZoneId zone;
    private final ResourceBundle bundle;

    /**
     * Loads the texts of a language.
     *
     * @param locale language of the report; languages other than French fall back to English
     * @param zone time zone of the dates
     */
    public ReportTexts(Locale locale, ZoneId zone) {
        this.locale = Objects.requireNonNull(locale, "locale");
        this.zone = Objects.requireNonNull(zone, "zone");
        // No fallback to the default locale of the JVM: English must stay English on a French PC.
        this.bundle =
                ResourceBundle.getBundle(
                        BUNDLE,
                        locale,
                        ResourceBundle.Control.getNoFallbackControl(
                                ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /** Returns the language of the texts. */
    public Locale locale() {
        return locale;
    }

    /**
     * Returns a text.
     *
     * @param key key in the properties files
     * @param args values replacing {@code {0}}, {@code {1}}, ...
     * @return the formatted text
     * @throws java.util.MissingResourceException if the key is unknown
     */
    public String get(String key, Object... args) {
        return new MessageFormat(bundle.getString(key), locale).format(args);
    }

    /**
     * Returns the text of an enumerated value, such as {@code risk.HIGH}.
     *
     * @param prefix group of the key
     * @param value the value
     * @return the text
     */
    public String of(String prefix, Enum<?> value) {
        return get(prefix + "." + value.name());
    }

    /** Formats a percentage with one decimal, such as {@code 12.5%} or {@code 12,5 %}. */
    public String percent(double value) {
        return get("format.percent", String.format(locale, "%.1f", value));
    }

    /**
     * Formats a number with a fixed number of decimals, or a dash for a missing value.
     *
     * @param value the number
     * @param decimals number of decimals
     * @return the text
     */
    public String number(double value, int decimals) {
        return Double.isNaN(value) ? "–" : String.format(locale, "%." + decimals + "f", value);
    }

    /**
     * Formats a p-value with three significant digits, or {@code < 0.0001}.
     *
     * @param p the p-value
     * @return the text
     */
    public String pValue(double p) {
        if (Double.isNaN(p)) {
            return "–";
        }
        return p < 1e-4 ? "< " + String.format(locale, "%.4f", 1e-4)
                : String.format(locale, "%.3g", p);
    }

    /**
     * Formats a p-value after its relation sign, such as {@code = 0.0123} or {@code < 0.0001}.
     *
     * @param p the p-value
     * @return the text
     */
    public String pRelation(double p) {
        String value = pValue(p);
        return value.startsWith("<") ? value : "= " + value;
    }

    /**
     * Names the value compared between groups, such as {@code % apoptotic cells}.
     *
     * @param metric the value
     * @param gating the hierarchy, to name a population
     * @return the name
     */
    public String metric(Metric metric, GatingTree gating) {
        return switch (metric.kind()) {
            case APOPTOTIC -> get("metric.APOPTOTIC");
            case CELL_FATE -> get("metric.CELL_FATE", get("fate." + metric.target()));
            case CELL_CYCLE -> get("metric.CELL_CYCLE", get("phase." + metric.target()));
            case POPULATION_PERCENT -> get("metric.POPULATION_PERCENT",
                    gating.find(metric.target()).map(Population::name).orElse(metric.target()));
        };
    }

    /** Formats a date and time. */
    public String dateTime(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(instant);
    }
}
