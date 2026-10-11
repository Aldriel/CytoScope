package com.bioinfo.cytoscope.report;

import com.bioinfo.cytoscope.experiment.Experiment;
import com.bioinfo.cytoscope.experiment.ExperimentResults;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything printed in a report: the experiment, its results, the figures and the details
 * given by the user. Created with {@link #builder(Experiment, ExperimentResults)}.
 *
 * @param title title of the report, such as the name of the experiment
 * @param experiment the set-up
 * @param results the results
 * @param figures the figures, in order
 * @param analyst person who ran the analysis, if given
 * @param notes free text printed at the end, if given
 * @param source credit of the data, such as for the examples, if given
 * @param locale language of the report
 * @param zone time zone of the dates
 * @param generatedAt time of generation
 * @param appVersion version of CytoScope
 */
public record CytoReport(String title, Experiment experiment, ExperimentResults results,
                         List<Figure> figures, Optional<String> analyst, Optional<String> notes,
                         Optional<String> source, Locale locale, ZoneId zone, Instant generatedAt,
                         String appVersion) {

    /** Creates the report and copies the figures. */
    public CytoReport {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(experiment, "experiment");
        Objects.requireNonNull(results, "results");
        figures = List.copyOf(figures);
    }

    /**
     * Starts a report.
     *
     * @param experiment the set-up
     * @param results its results
     * @return the builder
     */
    public static Builder builder(Experiment experiment, ExperimentResults results) {
        return new Builder(experiment, results);
    }

    /** Collects the optional parts of a report (Builder pattern). */
    public static final class Builder {

        private final Experiment experiment;
        private final ExperimentResults results;
        private final List<Figure> figures = new ArrayList<>();
        private String title = "CytoScope";
        private String analyst;
        private String notes;
        private String source;
        private Locale locale = Locale.ENGLISH;
        private ZoneId zone = ZoneId.systemDefault();
        private Instant generatedAt;
        private String appVersion = "dev";

        private Builder(Experiment experiment, ExperimentResults results) {
            this.experiment = Objects.requireNonNull(experiment, "experiment");
            this.results = Objects.requireNonNull(results, "results");
        }

        /**
         * Sets the title.
         *
         * @param value the title; blank keeps the default
         * @return this builder
         */
        public Builder title(String value) {
            if (value != null && !value.isBlank()) {
                title = value.strip();
            }
            return this;
        }

        /**
         * Sets the analyst.
         *
         * @param value the name; blank for none
         * @return this builder
         */
        public Builder analyst(String value) {
            analyst = blankToNull(value);
            return this;
        }

        /**
         * Sets the notes.
         *
         * @param value the text; blank for none
         * @return this builder
         */
        public Builder notes(String value) {
            notes = blankToNull(value);
            return this;
        }

        /**
         * Sets the credit of the data.
         *
         * @param value the text; blank for none
         * @return this builder
         */
        public Builder source(String value) {
            source = blankToNull(value);
            return this;
        }

        /**
         * Sets the language and time zone.
         *
         * @param newLocale the language
         * @param newZone the time zone
         * @return this builder
         */
        public Builder locale(Locale newLocale, ZoneId newZone) {
            locale = Objects.requireNonNull(newLocale, "locale");
            zone = Objects.requireNonNull(newZone, "zone");
            return this;
        }

        /**
         * Sets the time of generation.
         *
         * @param value the time
         * @return this builder
         */
        public Builder generatedAt(Instant value) {
            generatedAt = value;
            return this;
        }

        /**
         * Sets the version of the application.
         *
         * @param value the version
         * @return this builder
         */
        public Builder appVersion(String value) {
            appVersion = Objects.requireNonNull(value, "appVersion");
            return this;
        }

        /**
         * Adds figures.
         *
         * @param more the figures
         * @return this builder
         */
        public Builder figures(List<Figure> more) {
            figures.addAll(more);
            return this;
        }

        /**
         * Creates the report.
         *
         * @return the report
         */
        public CytoReport build() {
            return new CytoReport(title, experiment, results, figures,
                    Optional.ofNullable(analyst), Optional.ofNullable(notes),
                    Optional.ofNullable(source), locale, zone,
                    generatedAt == null ? Instant.now() : generatedAt, appVersion);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }
}
