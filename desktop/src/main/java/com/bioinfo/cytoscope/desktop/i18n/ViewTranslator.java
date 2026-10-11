package com.bioinfo.cytoscope.desktop.i18n;

import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;

import java.util.List;

/**
 * Translates a view written with message keys in place of texts.
 *
 * <p>Every text, prompt text, tooltip, placeholder, tab, column and menu whose value is a key of
 * the bundle is bound to {@link I18n#text(String, Object...)}, so it follows language changes.
 * Values that are not keys (names such as {@code Trastuzumab} or symbols such as {@code ?}) are
 * left untouched.
 */
public final class ViewTranslator {

    private final I18n i18n;

    public ViewTranslator(I18n i18n) {
        this.i18n = i18n;
    }

    /** Translates {@code root} and everything it contains. */
    public void translate(Node root) {
        visit(root);
    }

    /** Translates the menus of a menu bar and their items. */
    public void translate(MenuBar menuBar) {
        menuBar.getMenus().forEach(this::visitMenuItem);
    }

    private void visit(Node node) {
        if (node == null) {
            return;
        }
        if (node instanceof Labeled labeled) {
            bind(labeled.textProperty());
        }
        if (node instanceof TextInputControl input) {
            bind(input.promptTextProperty());
        }
        if (node instanceof Control control && control.getTooltip() != null) {
            bind(control.getTooltip());
        }
        if (node instanceof MenuBar menuBar) {
            translate(menuBar);
        }
        switch (node) {
            case TabPane tabs -> tabs.getTabs().forEach(this::visitTab);
            case SplitPane split -> split.getItems().forEach(this::visit);
            case TitledPane titled -> visit(titled.getContent());
            case ScrollPane scroll -> visit(scroll.getContent());
            case TableView<?> table -> {
                visit(table.getPlaceholder());
                visitColumns(table.getColumns());
            }
            case ListView<?> list -> visit(list.getPlaceholder());
            default -> {
                // Children are visited below.
            }
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(this::visit);
        }
    }

    private void visitTab(Tab tab) {
        bind(tab.textProperty());
        if (tab.getTooltip() != null) {
            bind(tab.getTooltip());
        }
        visit(tab.getContent());
    }

    private void visitColumns(List<? extends TableColumnBase<?, ?>> columns) {
        for (TableColumnBase<?, ?> column : columns) {
            bind(column.textProperty());
            visitColumns(column.getColumns());
        }
    }

    private void visitMenuItem(MenuItem item) {
        bind(item.textProperty());
        if (item instanceof Menu menu) {
            menu.getItems().forEach(this::visitMenuItem);
        }
    }

    private void bind(Tooltip tooltip) {
        bind(tooltip.textProperty());
    }

    private void bind(Property<String> property) {
        if (property.isBound()) {
            return;
        }
        String key = property.getValue();
        if (i18n.messages().has(key)) {
            property.bind(i18n.text(key));
        }
    }
}
