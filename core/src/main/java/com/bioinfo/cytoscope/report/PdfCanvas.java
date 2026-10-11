package com.bioinfo.cytoscope.report;

import com.bioinfo.cytoscope.plot.Charts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out text, tables and footers on the pages of a PDF document, top to bottom, starting a new
 * page when the current one is full. The sections of the report are written by {@link
 * PdfReportWriter}; this class only knows how to draw.
 *
 * <p>The standard PDF fonts are used, so that every reader displays the report without embedded
 * fonts; characters they cannot encode are replaced by {@code ?}.
 */
final class PdfCanvas {

    static final float BODY_SIZE = 9;
    static final float SMALL_SIZE = 7.5f;

    static final float[] TEXT = rgb("#0f172a");
    static final float[] MUTED = rgb("#64748b");
    static final float[] ACCENT = rgb("#0f766e");
    static final float[] HIGH = rgb("#dc2626");
    static final float[] MEDIUM = rgb("#d97706");
    static final float[] LOW = rgb("#16a34a");

    private static final float MARGIN = 50;
    private static final float FOOTER_HEIGHT = 46;
    // Title, caption, table header and two rows.
    private static final float SECTION_START = 120;
    private static final float LINE_SPACING = 1.3f;
    private static final float CELL_PADDING = 4;
    private static final float SWATCH_SIZE = 7;
    private static final float[] RULE = rgb("#cbd5e1");
    private static final float[] HEADER_FILL = rgb("#e2e8f0");
    private static final float[] STRIPE_FILL = rgb("#f8fafc");

    final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final PDDocument document;
    private final PDRectangle pageSize;
    private final float width;
    private PDPageContentStream out;
    private float y;

    /**
     * Prepares a canvas.
     *
     * @param document document receiving the pages
     * @param pageSize size of the pages
     */
    PdfCanvas(PDDocument document, PDRectangle pageSize) {
        this.document = document;
        this.pageSize = pageSize;
        this.width = pageSize.getWidth() - 2 * MARGIN;
    }

    /**
     * A row of a table.
     *
     * @param cells texts of the cells, one per column
     * @param markColumn column preceded by a colored mark, or -1
     * @param markColor color of the mark, or {@code null}
     */
    record TableRow(List<String> cells, int markColumn, float[] markColor) {

        /** Creates a row without mark. */
        static TableRow of(List<String> cells) {
            return new TableRow(cells, -1, null);
        }
    }

    /** Starts a new page and moves the cursor to its top margin. */
    void newPage() throws IOException {
        if (out != null) {
            out.close();
        }
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        out = new PDPageContentStream(document, page);
        y = pageSize.getHeight() - MARGIN;
    }

    /** Closes the content of the last page. */
    void finish() throws IOException {
        out.close();
        out = null;
    }

    /** Moves the cursor down. */
    void gap(float height) {
        y -= height;
    }

    /** Draws a large title line. */
    void title(String value, float size) throws IOException {
        text(value, bold, size, TEXT);
    }

    /** Draws a section title followed by a rule, on a new page if its content would not follow. */
    void sectionTitle(String title) throws IOException {
        ensureSpace(SECTION_START);
        y -= 8;
        text(title, bold, 12.5f, ACCENT);
        y -= 4;
        rule();
        y -= 8;
    }

    /** Draws a label and its wrapped value side by side. */
    void detailRow(String label, String value, float labelWidth) throws IOException {
        List<String> lines = wrap(value, regular, BODY_SIZE, width - labelWidth);
        float lineHeight = BODY_SIZE * LINE_SPACING;
        ensureSpace(lines.size() * lineHeight);
        drawText(label, bold, BODY_SIZE, MARGIN, y - BODY_SIZE, MUTED);
        for (String line : lines) {
            drawText(line, regular, BODY_SIZE, MARGIN + labelWidth, y - BODY_SIZE, TEXT);
            y -= lineHeight;
        }
    }

    /** Draws wrapped text across the full width. */
    void paragraph(String value, PDFont font, float size, float[] color) throws IOException {
        for (String line : wrap(value, font, size, width)) {
            text(line, font, size, color);
        }
    }

    /**
     * Draws a table whose header is repeated on every page it spans.
     *
     * @param headers column titles
     * @param widths share of the width of each column; their sum is 1
     * @param rows the rows
     */
    void table(List<String> headers, float[] widths, List<TableRow> rows) throws IOException {
        TableRow header = TableRow.of(headers);
        float headerHeight = rowHeight(header, bold, widths);
        ensureSpace(headerHeight + BODY_SIZE * 3);
        tableHeader(header, widths, headerHeight);
        boolean striped = false;
        for (TableRow row : rows) {
            float height = rowHeight(row, regular, widths);
            if (ensureSpace(height)) {
                tableHeader(header, widths, headerHeight);
                striped = false;
            }
            if (striped) {
                fillRect(MARGIN, y - height, width, height, STRIPE_FILL);
            }
            tableRow(row, regular, widths, height);
            striped = !striped;
        }
        y -= 6;
    }

    /**
     * Draws figures two by two, each with its caption below.
     *
     * @param figures the figures
     */
    void figures(List<Figure> figures) throws IOException {
        float gutter = 14;
        float figureWidth = (width - gutter) / 2;
        float figureHeight = figureWidth * Charts.HEIGHT / Charts.WIDTH;
        float lineHeight = SMALL_SIZE * LINE_SPACING;
        for (int i = 0; i < figures.size(); i += 2) {
            List<Figure> row = figures.subList(i, Math.min(i + 2, figures.size()));
            int captionLines = 0;
            for (Figure figure : row) {
                captionLines = Math.max(captionLines,
                        wrap(figure.caption(), regular, SMALL_SIZE, figureWidth).size());
            }
            float rowHeight = figureHeight + captionLines * lineHeight + 12;
            ensureSpace(rowHeight);
            for (int k = 0; k < row.size(); k++) {
                Figure figure = row.get(k);
                float x = MARGIN + k * (figureWidth + gutter);
                PDImageXObject image = LosslessFactory.createFromImage(document, figure.image());
                out.drawImage(image, x, y - figureHeight, figureWidth, figureHeight);
                float baseline = y - figureHeight - SMALL_SIZE - 2;
                for (String line : wrap(figure.caption(), regular, SMALL_SIZE, figureWidth)) {
                    drawText(line, regular, SMALL_SIZE, x, baseline, MUTED);
                    baseline -= lineHeight;
                }
            }
            y -= rowHeight;
        }
    }

    /**
     * Adds the footer of every page: signature with clickable website and e-mail, page number,
     * disclaimer and provenance.
     *
     * @param texts texts of the report language
     * @param provenance generation date and application version
     * @param email contact e-mail of the author
     */
    void footers(ReportTexts texts, String provenance, String email) throws IOException {
        int count = document.getNumberOfPages();
        for (int i = 0; i < count; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream footer =
                    new PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                out = footer;
                float top = MARGIN + FOOTER_HEIGHT - 10;
                strokeLine(MARGIN, top, MARGIN + width, top);
                String pageNumber = texts.get("footer.page", i + 1, count);
                signature(page, texts, email, top - 11);
                drawText(
                        pageNumber,
                        bold,
                        SMALL_SIZE,
                        MARGIN + width - textWidth(pageNumber, bold, SMALL_SIZE),
                        top - 11,
                        TEXT);
                drawText(
                        fit(texts.get("footer.disclaimer"), regular, SMALL_SIZE, width),
                        regular,
                        SMALL_SIZE,
                        MARGIN,
                        top - 22,
                        MUTED);
                drawText(
                        fit(provenance, regular, SMALL_SIZE, width),
                        regular,
                        SMALL_SIZE,
                        MARGIN,
                        top - 33,
                        MUTED);
            }
        }
        out = null;
    }

    private void signature(PDPage page, ReportTexts texts, String email, float baseline)
            throws IOException {
        String separator = "  ·  ";
        String title = texts.get("brand.title");
        String website = texts.get("brand.website");
        float x = MARGIN;
        drawText(title, bold, SMALL_SIZE, x, baseline, ACCENT);
        x += textWidth(encodable(title, bold), bold, SMALL_SIZE);
        drawText(separator, regular, SMALL_SIZE, x, baseline, MUTED);
        x += textWidth(separator, regular, SMALL_SIZE);
        x += link(page, website, website, x, baseline);
        drawText(separator, regular, SMALL_SIZE, x, baseline, MUTED);
        x += textWidth(separator, regular, SMALL_SIZE);
        link(page, email, "mailto:" + email, x, baseline);
    }

    private float link(PDPage page, String value, String uri, float x, float baseline)
            throws IOException {
        drawText(value, regular, SMALL_SIZE, x, baseline, ACCENT);
        float textWidth = textWidth(encodable(value, regular), regular, SMALL_SIZE);
        PDActionURI action = new PDActionURI();
        action.setURI(uri);
        PDAnnotationLink link = new PDAnnotationLink();
        link.setAction(action);
        link.setRectangle(new PDRectangle(x, baseline - 2, textWidth, SMALL_SIZE + 3));
        PDBorderStyleDictionary noBorder = new PDBorderStyleDictionary();
        noBorder.setWidth(0);
        link.setBorderStyle(noBorder);
        page.getAnnotations().add(link);
        return textWidth;
    }

    private boolean ensureSpace(float height) throws IOException {
        if (y - height < MARGIN + FOOTER_HEIGHT) {
            newPage();
            return true;
        }
        return false;
    }

    private void text(String value, PDFont font, float size, float[] color) throws IOException {
        float lineHeight = size * LINE_SPACING;
        ensureSpace(lineHeight);
        drawText(value, font, size, MARGIN, y - size, color);
        y -= lineHeight;
    }

    private void rule() throws IOException {
        strokeLine(MARGIN, y, MARGIN + width, y);
    }

    private void tableHeader(TableRow header, float[] widths, float height) throws IOException {
        fillRect(MARGIN, y - height, width, height, HEADER_FILL);
        tableRow(header, bold, widths, height);
    }

    private void tableRow(TableRow row, PDFont font, float[] widths, float height)
            throws IOException {
        float x = MARGIN;
        float lineHeight = BODY_SIZE * LINE_SPACING;
        for (int column = 0; column < row.cells().size(); column++) {
            float textX = x + CELL_PADDING;
            float baseline = y - CELL_PADDING - BODY_SIZE;
            if (column == row.markColumn()) {
                fillRect(textX, baseline, SWATCH_SIZE, SWATCH_SIZE, row.markColor());
                textX += SWATCH_SIZE + 4;
            }
            for (String line :
                    wrap(row.cells().get(column), font, BODY_SIZE, cell(row, column, widths))) {
                drawText(line, font, BODY_SIZE, textX, baseline, TEXT);
                baseline -= lineHeight;
            }
            x += width * widths[column];
        }
        y -= height;
        strokeLine(MARGIN, y, MARGIN + width, y);
    }

    private float rowHeight(TableRow row, PDFont font, float[] widths) throws IOException {
        int lines = 1;
        for (int column = 0; column < row.cells().size(); column++) {
            lines =
                    Math.max(
                            lines,
                            wrap(
                                            row.cells().get(column),
                                            font,
                                            BODY_SIZE,
                                            cell(row, column, widths))
                                    .size());
        }
        return lines * BODY_SIZE * LINE_SPACING + 2 * CELL_PADDING;
    }

    /** Width available for the text of a cell, leaving room for its mark. */
    private float cell(TableRow row, int column, float[] widths) {
        float available = width * widths[column] - 2 * CELL_PADDING;
        return column == row.markColumn() ? available - SWATCH_SIZE - 4 : available;
    }

    private void drawText(
            String value, PDFont font, float size, float x, float baseline, float[] color)
            throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.setNonStrokingColor(color[0], color[1], color[2]);
        out.newLineAtOffset(x, baseline);
        out.showText(encodable(value, font));
        out.endText();
    }

    private void fillRect(float x, float bottom, float w, float h, float[] color)
            throws IOException {
        out.setNonStrokingColor(color[0], color[1], color[2]);
        out.addRect(x, bottom, w, h);
        out.fill();
    }

    private void strokeLine(float x1, float y1, float x2, float y2) throws IOException {
        out.setStrokingColor(RULE[0], RULE[1], RULE[2]);
        out.setLineWidth(0.5f);
        out.moveTo(x1, y1);
        out.lineTo(x2, y2);
        out.stroke();
    }

    /**
     * Splits text into lines that fit a width, breaking the words too long for a line, such as
     * protein sequences.
     *
     * @return the lines, at least one
     */
    static List<String> wrap(String value, PDFont font, float size, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : encodable(value, font).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(candidate, font, size) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            for (char c : word.toCharArray()) {
                if (!line.isEmpty() && textWidth(line.toString() + c, font, size) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(c);
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Shortens text with an ellipsis so that it fits a width. */
    static String fit(String value, PDFont font, float size, float maxWidth) throws IOException {
        String text = encodable(value, font);
        if (textWidth(text, font, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end) + ellipsis, font, size) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static float textWidth(String value, PDFont font, float size) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    /**
     * Makes text drawable with a standard font: line breaks and special spaces become spaces, and
     * the characters that the font cannot encode become {@code ?}.
     */
    static String encodable(String value, PDFont font) {
        StringBuilder text = new StringBuilder(value.length());
        value.codePoints()
                .forEach(
                        codePoint -> {
                            if (Character.isWhitespace(codePoint)
                                    || Character.isSpaceChar(codePoint)) {
                                text.append(' ');
                                return;
                            }
                            String character = Character.toString(codePoint);
                            try {
                                font.encode(character);
                                text.append(character);
                            } catch (IOException | IllegalArgumentException e) {
                                text.append('?');
                            }
                        });
        return text.toString();
    }

    /** Converts a CSS color such as {@code #0f766e} to PDF components between 0 and 1. */
    static float[] rgb(String hex) {
        int value = Integer.parseInt(hex.substring(1), 16);
        return new float[] {
            ((value >> 16) & 0xFF) / 255f, ((value >> 8) & 0xFF) / 255f, (value & 0xFF) / 255f
        };
    }
}
