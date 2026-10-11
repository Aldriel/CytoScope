package com.bioinfo.cytoscope.desktop.help;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Builds the JavaFX nodes of a help section, highlighting the words of the current search. */
final class HelpRenderer {

    /** Scale at which the screenshots are captured; they are displayed at their logical size. */
    private static final double SCREENSHOT_SCALE = 1.5;

    static final String HIGHLIGHT = "help-highlight";

    /** Table columns whose cells never exceed this length are not wrapped. */
    private static final int SHORT_COLUMN_CHARS = 40;

    private final List<String> words;
    private final ObservableDoubleValue availableWidth;
    private final Consumer<String> openUrl;

    /**
     * Creates a renderer.
     *
     * @param words folded words to highlight, possibly empty
     * @param availableWidth width available for the content, used to shrink large images
     * @param openUrl opens the links of the guide
     */
    HelpRenderer(
            List<String> words, ObservableDoubleValue availableWidth, Consumer<String> openUrl) {
        this.words = words;
        this.availableWidth = availableWidth;
        this.openUrl = openUrl;
    }

    VBox render(HelpSection section) {
        VBox box = new VBox();
        box.getStyleClass().add("help-section");
        Label title = new Label();
        title.setGraphic(flow(List.of(new Span(section.title(), Span.Style.PLAIN, null))));
        title.getStyleClass().add("help-title");
        box.getChildren().add(title);
        for (Block block : section.blocks()) {
            box.getChildren().add(render(block));
        }
        return box;
    }

    private Node render(Block block) {
        return switch (block) {
            case Block.Heading h -> styled(flow(h.spans()), h.level() == 2 ? "help-h2" : "help-h3");
            case Block.Paragraph p -> styled(flow(p.spans()), "help-paragraph");
            case Block.Note n -> styled(flow(n.spans()), "help-note");
            case Block.ListBlock l -> list(l);
            case Block.Image i -> image(i);
            case Block.Table t -> table(t);
            case Block.Code c -> code(c);
        };
    }

    private Node list(Block.ListBlock list) {
        VBox box = new VBox();
        box.getStyleClass().add("help-list");
        int number = 1;
        for (List<Span> item : list.items()) {
            Label marker = new Label(list.numbered() ? (number++) + "." : "•");
            marker.getStyleClass().add("help-list-marker");
            marker.setMinWidth(Label.USE_PREF_SIZE);
            TextFlow text = flow(item);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(marker, text);
            row.setAlignment(Pos.TOP_LEFT);
            row.getStyleClass().add("help-list-item");
            box.getChildren().add(row);
        }
        return box;
    }

    private Node image(Block.Image block) {
        URL url = HelpRenderer.class.getResource(HelpDocument.RESOURCE_ROOT + block.path());
        if (url == null) {
            return styled(
                    flow(List.of(new Span("[" + block.alt() + "]", Span.Style.ITALIC, null))),
                    "help-paragraph");
        }
        Image image = new Image(url.toExternalForm(), true);
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.fitWidthProperty()
                .bind(
                        Bindings.createDoubleBinding(
                                () ->
                                        Math.max(
                                                100,
                                                Math.min(
                                                        image.getWidth() / SCREENSHOT_SCALE,
                                                        availableWidth.get() - 40)),
                                image.widthProperty(),
                                availableWidth));
        VBox frame = new VBox(view);
        frame.getStyleClass().add("help-image");
        frame.setMaxWidth(VBox.USE_PREF_SIZE);
        return frame;
    }

    private Node table(Block.Table table) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("help-table");
        int columns = table.header().size();
        for (int c = 0; c < columns; c++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setFillWidth(true);
            if (c < columns - 1 && longestCell(table, c) <= SHORT_COLUMN_CHARS) {
                // Short columns (numbers, names, file names) keep their text on one line.
                constraints.setMinWidth(Region.USE_PREF_SIZE);
                constraints.setHgrow(Priority.NEVER);
            } else {
                constraints.setHgrow(Priority.ALWAYS);
            }
            grid.getColumnConstraints().add(constraints);
        }
        addRow(grid, 0, table.header(), "help-table-header");
        for (int r = 0; r < table.rows().size(); r++) {
            addRow(
                    grid,
                    r + 1,
                    table.rows().get(r),
                    r % 2 == 0 ? "help-table-cell" : "help-table-cell-alt");
        }
        return grid;
    }

    private static int longestCell(Block.Table table, int column) {
        int longest = Block.text(table.header().get(column)).length();
        for (List<List<Span>> row : table.rows()) {
            if (column < row.size()) {
                longest = Math.max(longest, Block.text(row.get(column)).length());
            }
        }
        return longest;
    }

    private void addRow(GridPane grid, int row, List<List<Span>> cells, String styleClass) {
        for (int c = 0; c < cells.size(); c++) {
            TextFlow flow = flow(cells.get(c));
            VBox cell = new VBox(flow);
            cell.getStyleClass().add(styleClass);
            cell.setMaxWidth(Double.MAX_VALUE);
            cell.setMaxHeight(Double.MAX_VALUE);
            grid.add(cell, c, row);
        }
    }

    private Node code(Block.Code block) {
        TextArea area = new TextArea(block.text());
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount((int) block.text().lines().count());
        area.getStyleClass().add("help-code");
        if (!words.isEmpty()
                && words.stream().anyMatch(HelpDocument.fold(block.text())::contains)) {
            area.getStyleClass().add(HIGHLIGHT);
        }
        return area;
    }

    private static <T extends Node> T styled(T node, String styleClass) {
        node.getStyleClass().add(styleClass);
        return node;
    }

    private TextFlow flow(List<Span> spans) {
        TextFlow flow = new TextFlow();
        for (Span span : spans) {
            if (span.style() == Span.Style.LINK) {
                Hyperlink link = new Hyperlink(span.text());
                link.setPadding(Insets.EMPTY);
                link.setOnAction(e -> openUrl.accept(span.url()));
                flow.getChildren().add(link);
                continue;
            }
            for (Segment segment : highlight(span.text())) {
                flow.getChildren().add(node(segment, span.style()));
            }
        }
        return flow;
    }

    private Node node(Segment segment, Span.Style style) {
        if (segment.match()) {
            Label label = new Label(segment.text());
            label.getStyleClass().addAll(HIGHLIGHT, "help-" + style.name().toLowerCase());
            return label;
        }
        Text text = new Text(segment.text());
        text.getStyleClass().addAll("help-text", "help-" + style.name().toLowerCase());
        return text;
    }

    private record Segment(String text, boolean match) {}

    /** Splits a text into runs that match or do not match the search words. */
    private List<Segment> highlight(String text) {
        if (words.isEmpty()) {
            return List.of(new Segment(text, false));
        }
        String folded = HelpDocument.fold(text);
        boolean[] marked = new boolean[text.length()];
        for (String word : words) {
            for (int i = folded.indexOf(word);
                    i >= 0;
                    i = folded.indexOf(word, i + word.length())) {
                for (int k = i; k < i + word.length(); k++) {
                    marked[k] = true;
                }
            }
        }
        List<Segment> segments = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i == text.length() || marked[i] != marked[start]) {
                segments.add(new Segment(text.substring(start, i), marked[start]));
                start = i;
            }
        }
        return segments;
    }
}
