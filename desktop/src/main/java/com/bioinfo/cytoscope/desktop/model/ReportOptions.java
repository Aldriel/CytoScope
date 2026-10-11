package com.bioinfo.cytoscope.desktop.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Content of the PDF report chosen in the Report tab.
 *
 * @param title title of the report; blank for the default title
 * @param locale language of the report; its country also chooses the paper size
 * @param analyst name of the analyst; blank to omit it
 * @param notes free-text notes; blank to omit them
 */
public record ReportOptions(String title, Locale locale, String analyst, String notes) {

    /** Validates the options. */
    public ReportOptions {
        Objects.requireNonNull(locale, "locale");
    }
}
