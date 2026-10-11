package com.bioinfo.cytoscope.desktop.help;

import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.i18n.I18n;
import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.desktop.model.UiSettings;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

/**
 * User guide window: a search field, the list of sections (one per tab of the application, plus
 * general topics) and the content of the selected section. It follows the language and text size of
 * the main window.
 */
public final class HelpWindow {

    private final Stage stage = new Stage();
    private final UiSettings settings;
    private final I18n i18n;
    private final Consumer<String> openUrl;

    private final TextField searchField = new TextField();
    private final Label resultLabel = new Label();
    private final ListView<HelpDocument.SearchHit> sectionList = new ListView<>();
    private final ScrollPane contentPane = new ScrollPane();
    private final BorderPane root = new BorderPane();

    private HelpDocument document;

    /**
     * Creates the help window, hidden until {@code show} is called.
     *
     * @param owner main window
     * @param settings language and text size of the interface
     * @param stylesheet application stylesheet
     * @param openUrl opens the links of the guide in the browser
     */
    public HelpWindow(
            Window owner, UiSettings settings, String stylesheet, Consumer<String> openUrl) {
        this.settings = settings;
        this.i18n = settings.i18n();
        this.openUrl = openUrl;
        stage.initOwner(owner);
        stage.titleProperty().bind(i18n.text("help.window.title", AppInfo.NAME));

        searchField.promptTextProperty().bind(i18n.text("help.search.prompt"));
        searchField.setId("helpSearchField");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, old, query) -> refresh(selectedId()));
        Button clear = new Button();
        clear.textProperty().bind(i18n.text("help.search.clear"));
        clear.disableProperty().bind(searchField.textProperty().isEmpty());
        clear.setOnAction(
                e -> {
                    searchField.clear();
                    searchField.requestFocus();
                });
        resultLabel.getStyleClass().add("muted");
        Label searchLabel = new Label();
        searchLabel.textProperty().bind(i18n.text("help.search.label"));
        searchLabel.setLabelFor(searchField);
        HBox searchBar = new HBox(8, searchLabel, searchField, clear, resultLabel);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.getStyleClass().add("help-search-bar");

        sectionList.setId("helpSectionList");
        sectionList.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(HelpDocument.SearchHit hit, boolean empty) {
                                super.updateItem(hit, empty);
                                if (empty || hit == null) {
                                    setText(null);
                                } else {
                                    setText(
                                            hit.occurrences() > 0
                                                    ? hit.section().title()
                                                            + "  ("
                                                            + hit.occurrences()
                                                            + ")"
                                                    : hit.section().title());
                                }
                            }
                        });
        sectionList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, hit) -> showSection(hit));

        contentPane.setFitToWidth(true);
        contentPane.getStyleClass().add("help-content");
        SplitPane split = new SplitPane(sectionList, contentPane);
        split.setDividerPositions(0.26);
        SplitPane.setResizableWithParent(sectionList, false);

        root.setTop(searchBar);
        root.setCenter(split);
        root.getStyleClass().add("help-root");
        root.styleProperty()
                .bind(Bindings.format("-fx-font-size: %dpx;", settings.fontSizeProperty()));

        Scene scene = new Scene(root, 1050, 760);
        scene.getStylesheets().add(stylesheet);
        scene.getAccelerators()
                .put(
                        new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                        () -> searchField.requestFocus());
        scene.getAccelerators()
                .put(
                        new KeyCodeCombination(KeyCode.ESCAPE),
                        () -> {
                            if (searchField.getText().isEmpty()) {
                                stage.hide();
                            } else {
                                searchField.clear();
                            }
                        });
        stage.setScene(scene);

        i18n.messagesProperty().addListener((obs, old, messages) -> reload(selectedId()));
        reload(null);
    }

    /** Shows the window on the section with this identifier (the first section if unknown). */
    public void show(String sectionId) {
        searchField.clear();
        refresh(sectionId);
        stage.show();
        stage.toFront();
        searchField.requestFocus();
    }

    /** Shows the window filtered by a search. */
    public void search(String query) {
        stage.show();
        searchField.setText(query);
    }

    public Stage stage() {
        return stage;
    }

    private void reload(String sectionId) {
        document = HelpDocument.load(i18n.locale());
        refresh(sectionId);
    }

    private String selectedId() {
        HelpDocument.SearchHit hit = sectionList.getSelectionModel().getSelectedItem();
        return hit == null ? null : hit.section().id();
    }

    /**
     * Filters the list of sections with the current search, keeping {@code sectionId} selected if
     * it matches.
     */
    private void refresh(String sectionId) {
        List<HelpDocument.SearchHit> hits = document.search(searchField.getText());
        sectionList.getItems().setAll(hits);
        Messages m = i18n.messages();
        if (searchField.getText().isBlank()) {
            resultLabel.setText("");
        } else {
            resultLabel.setText(
                    m.get(
                            "help.search.results",
                            hits.size(),
                            hits.stream().mapToInt(HelpDocument.SearchHit::occurrences).sum()));
        }
        HelpDocument.SearchHit selected =
                hits.stream()
                        .filter(hit -> hit.section().id().equals(sectionId))
                        .findFirst()
                        .orElse(hits.isEmpty() ? null : hits.getFirst());
        if (selected == null) {
            Label none = new Label(m.get("help.search.none", searchField.getText().strip()));
            none.getStyleClass().add("help-paragraph");
            contentPane.setContent(new VBox(none));
            return;
        }
        sectionList.getSelectionModel().select(selected);
        showSection(selected);
    }

    private void showSection(HelpDocument.SearchHit hit) {
        if (hit == null) {
            return;
        }
        HelpRenderer renderer =
                new HelpRenderer(
                        HelpDocument.queryWords(searchField.getText()),
                        contentPane.widthProperty(),
                        openUrl);
        VBox content = renderer.render(hit.section());
        contentPane.setContent(content);
        contentPane.setVvalue(0);
        if (hit.occurrences() > 0) {
            Platform.runLater(() -> scrollToFirstHighlight(content));
        }
    }

    /** Scrolls the content so the first highlighted word is visible. */
    private void scrollToFirstHighlight(VBox content) {
        content.applyCss();
        content.layout();
        Node first = content.lookup("." + HelpRenderer.HIGHLIGHT);
        if (first == null) {
            return;
        }
        Bounds bounds = content.sceneToLocal(first.localToScene(first.getBoundsInLocal()));
        double scrollable =
                content.getBoundsInLocal().getHeight()
                        - contentPane.getViewportBounds().getHeight();
        if (scrollable > 0) {
            contentPane.setVvalue(Math.clamp((bounds.getMinY() - 60) / scrollable, 0, 1));
        }
    }
}
