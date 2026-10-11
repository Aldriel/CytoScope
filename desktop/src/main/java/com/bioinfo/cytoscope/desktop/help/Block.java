package com.bioinfo.cytoscope.desktop.help;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Block-level element of a help section. */
public sealed interface Block {

    /** Returns the text of the block without markup, used by the search. */
    String plainText();

    /**
     * Sub-title inside a section.
     *
     * @param level 2 for {@code ##}, 3 for {@code ###}
     * @param spans formatted text
     */
    record Heading(int level, List<Span> spans) implements Block {
        @Override
        public String plainText() {
            return text(spans);
        }
    }

    /**
     * Paragraph of text.
     *
     * @param spans formatted text
     */
    record Paragraph(List<Span> spans) implements Block {
        @Override
        public String plainText() {
            return text(spans);
        }
    }

    /**
     * Tip or warning shown in a highlighted box.
     *
     * @param spans formatted text
     */
    record Note(List<Span> spans) implements Block {
        @Override
        public String plainText() {
            return text(spans);
        }
    }

    /**
     * Bulleted or numbered list.
     *
     * @param numbered {@code true} for a numbered list
     * @param items formatted text of each item
     */
    record ListBlock(boolean numbered, List<List<Span>> items) implements Block {
        @Override
        public String plainText() {
            return items.stream().map(Block::text).collect(Collectors.joining("\n"));
        }
    }

    /**
     * Image whose path is relative to {@link HelpDocument#RESOURCE_ROOT}.
     *
     * @param alt description of the image, also used by the search
     * @param path resource path of the image
     */
    record Image(String alt, String path) implements Block {
        @Override
        public String plainText() {
            return alt;
        }
    }

    /**
     * Table with a header row.
     *
     * @param header formatted text of each header cell
     * @param rows formatted text of each cell, row by row
     */
    record Table(List<List<Span>> header, List<List<List<Span>>> rows) implements Block {
        @Override
        public String plainText() {
            return Stream.concat(Stream.of(header), rows.stream())
                    .map(row -> row.stream().map(Block::text).collect(Collectors.joining(" ")))
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Preformatted text, shown in a monospaced font.
     *
     * @param text raw text, line breaks included
     */
    record Code(String text) implements Block {
        @Override
        public String plainText() {
            return text;
        }
    }

    /**
     * Concatenates the text of formatted spans.
     *
     * @param spans formatted text
     * @return the text without markup
     */
    static String text(List<Span> spans) {
        return spans.stream().map(Span::text).collect(Collectors.joining());
    }
}
