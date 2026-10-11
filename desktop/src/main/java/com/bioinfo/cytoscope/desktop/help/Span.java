package com.bioinfo.cytoscope.desktop.help;

/**
 * Run of text with a single inline style.
 *
 * @param text displayed text
 * @param style inline style
 * @param url target of a link, {@code null} for other styles
 */
public record Span(String text, Style style, String url) {

    /** Inline styles of the guide's Markdown subset. */
    public enum Style {
        /** Normal text. */
        PLAIN,
        /** {@code **bold**} text. */
        BOLD,
        /** {@code *italic*} text. */
        ITALIC,
        /** {@code `code`}, shown in a monospaced font. */
        CODE,
        /** {@code [text](url)}, opened in the browser. */
        LINK
    }
}
