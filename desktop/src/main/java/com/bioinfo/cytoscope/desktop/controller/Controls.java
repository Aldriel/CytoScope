package com.bioinfo.cytoscope.desktop.controller;

import com.bioinfo.cytoscope.desktop.i18n.I18n;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.function.Consumer;
import java.util.function.Function;

/** Configuration helpers for the JavaFX controls, shared by the controllers. */
final class Controls {

    private Controls() {}

    /**
     * Fills a combo box with constants displayed through translated labels. The labels follow
     * language changes.
     *
     * @param box combo box to fill
     * @param values selectable values
     * @param initial value selected at start
     * @param key message key of the label of each value
     * @param i18n current language
     */
    static <T> void translatedChoices(
            ComboBox<T> box, T[] values, T initial, Function<T, String> key, I18n i18n) {
        box.setItems(FXCollections.observableArrayList(values));
        box.setValue(initial);
        labels(box, value -> i18n.messages().get(key.apply(value)));
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) ->
                                labels(box, value -> messages.get(key.apply(value))));
    }

    /**
     * Shows the values of a combo box through computed labels.
     *
     * @param box the combo box
     * @param label text of each value
     */
    static <T> void labels(ComboBox<T> box, Function<T, String> label) {
        box.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(T value) {
                        return value == null ? "" : label.apply(value);
                    }

                    @Override
                    public T fromString(String text) {
                        throw new UnsupportedOperationException("Combo boxes are not editable");
                    }
                });
        box.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(T value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(empty || value == null ? null : label.apply(value));
                            }
                        });
        box.setButtonCell(box.getCellFactory().call(null));
    }

    /**
     * Calls {@code commit} with the text of a field when the user presses Enter or leaves it.
     *
     * @param field the field
     * @param commit receives the text
     */
    static void onCommit(TextField field, Consumer<String> commit) {
        field.setOnAction(e -> commit.accept(field.getText()));
        field.focusedProperty()
                .addListener(
                        (obs, was, focused) -> {
                            if (!focused) {
                                commit.accept(field.getText());
                            }
                        });
    }

    /**
     * Creates a menu item whose text follows language changes.
     *
     * @param key message key of the text
     * @param i18n current language
     * @param action run when the item is chosen
     * @return the menu item
     */
    static MenuItem menuItem(String key, I18n i18n, Runnable action) {
        MenuItem item = new MenuItem();
        item.textProperty().bind(i18n.text(key));
        item.setOnAction(e -> action.run());
        return item;
    }

    /**
     * Creates a table column showing a text computed from each row.
     *
     * @param title header of the column
     * @param numeric whether the column holds numbers, aligned to the right
     * @param getter computes the text of the cell from the row
     * @return the column
     */
    static <S> TableColumn<S, String> column(
            String title, boolean numeric, Function<S, String> getter) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(
                cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
        column.setSortable(false);
        if (numeric) {
            column.getStyleClass().add("numeric");
        }
        return column;
    }
}
