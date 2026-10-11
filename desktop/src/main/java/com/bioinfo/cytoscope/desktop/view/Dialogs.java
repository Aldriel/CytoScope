package com.bioinfo.cytoscope.desktop.view;

import com.bioinfo.cytoscope.desktop.AppInfo;
import com.bioinfo.cytoscope.desktop.i18n.Messages;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Modal dialogs of the main window, styled like the window that owns them. */
public final class Dialogs {

    private final Window owner;
    private final Node styleSource;
    private final String stylesheet;

    /**
     * Creates the dialog factory.
     *
     * @param owner window owning the dialogs
     * @param styleSource node whose inline style (text size) the dialogs copy
     * @param stylesheet application stylesheet
     */
    public Dialogs(Window owner, Node styleSource, String stylesheet) {
        this.owner = owner;
        this.styleSource = styleSource;
        this.stylesheet = stylesheet;
    }

    /** Shows the name, version, author, data sources and disclaimer of the application. */
    public void showAbout(Messages m, Consumer<String> openUrl) {
        Label description = wrapped(m.get("about.description"));
        Label author = new Label(m.get("about.author", AppInfo.AUTHOR));
        author.getStyleClass().add("bold");
        String websiteUrl = m.get("about.website");
        Hyperlink website = new Hyperlink(websiteUrl);
        website.setOnAction(e -> openUrl.accept(websiteUrl));
        Hyperlink contact = new Hyperlink(AppInfo.CONTACT_EMAIL);
        contact.setOnAction(e -> openUrl.accept("mailto:" + AppInfo.CONTACT_EMAIL));

        Label sources = wrapped(m.get("about.sources"));
        Label references = wrapped(m.get("about.references"));
        Label disclaimer = wrapped(m.get("about.disclaimer"));
        disclaimer.getStyleClass().addAll("muted", "small");
        Label runtime =
                new Label(
                        m.get(
                                "about.runtime",
                                System.getProperty("java.version"),
                                System.getProperty("javafx.version")));
        runtime.getStyleClass().addAll("muted", "small");

        VBox content =
                new VBox(
                        10,
                        description,
                        new VBox(author, website, contact),
                        sources,
                        references,
                        disclaimer,
                        runtime);
        content.setPrefWidth(500);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(m.get("about.title", AppInfo.NAME));
        alert.setHeaderText(AppInfo.NAME + "\n" + m.get("about.version", AppInfo.version()));
        appIcon().ifPresent(alert::setGraphic);
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(closeButton(m));
        prepare(alert);
        alert.showAndWait();
    }

    /** Shows the keyboard shortcuts. */
    public void showShortcuts(Messages m) {
        Map<String, String> shortcuts = new LinkedHashMap<>();
        shortcuts.put("F1", m.get("menu.help.guide"));
        shortcuts.put(display("Shortcut+N"), m.get("menu.file.new"));
        shortcuts.put(display("Shortcut+O"), m.get("menu.file.open"));
        shortcuts.put(display("Shortcut+S"), m.get("menu.file.report"));
        shortcuts.put(
                display("Shortcut+1") + " … " + display("Shortcut+6"), m.get("shortcuts.tabs"));
        shortcuts.put("Esc", m.get("shortcuts.escape"));
        shortcuts.put(display("Shortcut+Equals"), m.get("menu.options.zoomIn"));
        shortcuts.put(display("Shortcut+Minus"), m.get("menu.options.zoomOut"));
        shortcuts.put(display("Shortcut+0"), m.get("menu.options.zoomReset"));
        shortcuts.put(display("Shortcut+F"), m.get("shortcuts.helpSearch"));
        shortcuts.put(display("Shortcut+Q"), m.get("menu.file.quit"));

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(6);
        int row = 0;
        for (Map.Entry<String, String> entry : shortcuts.entrySet()) {
            Label keys = new Label(entry.getKey());
            keys.getStyleClass().add("bold");
            grid.addRow(row++, keys, new Label(entry.getValue()));
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(m.get("menu.help.shortcuts"));
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().setAll(closeButton(m));
        prepare(dialog);
        dialog.showAndWait();
    }

    private void prepare(Dialog<?> dialog) {
        dialog.initOwner(owner);
        dialog.getDialogPane().getStylesheets().setAll(List.of(stylesheet));
        dialog.getDialogPane().setStyle(styleSource.getStyle());
    }

    private static Optional<ImageView> appIcon() {
        try (InputStream in = Dialogs.class.getResourceAsStream("icon-64.png")) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(new ImageView(new Image(in)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static ButtonType closeButton(Messages m) {
        return new ButtonType(m.get("dialog.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    /** Returns the combination as the platform displays it, e.g. "Ctrl+S" on Windows. */
    private static String display(String combination) {
        return KeyCombination.keyCombination(combination).getDisplayText();
    }
}
