package com.bioinfo.cytoscope.desktop.i18n;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.util.Locale;
import java.util.function.Function;

/**
 * Current language of the user interface.
 *
 * <p>Texts are exposed as bindings recomputed whenever the language changes, so the whole interface
 * switches language without being rebuilt.
 */
public final class I18n {

    private final ReadOnlyObjectWrapper<Messages> messages;

    /**
     * Creates the language holder.
     *
     * @param locale initial language; unsupported languages fall back to English
     */
    public I18n(Locale locale) {
        messages = new ReadOnlyObjectWrapper<>(Messages.forLocale(locale));
    }

    public ReadOnlyObjectProperty<Messages> messagesProperty() {
        return messages.getReadOnlyProperty();
    }

    public Messages messages() {
        return messages.get();
    }

    public Locale locale() {
        return messages.get().locale();
    }

    /** Switches the language; unsupported languages fall back to English. */
    public void setLocale(Locale locale) {
        if (!Messages.supportedLocale(locale).equals(locale())) {
            messages.set(Messages.forLocale(locale));
        }
    }

    /** Returns a binding to a message, updated when the language changes. */
    public StringBinding text(String key, Object... args) {
        return text(m -> m.get(key, args));
    }

    /** Returns a binding to a computed text, updated when the language changes. */
    public StringBinding text(Function<Messages, String> text) {
        return Bindings.createStringBinding(() -> text.apply(messages.get()), messages);
    }
}
