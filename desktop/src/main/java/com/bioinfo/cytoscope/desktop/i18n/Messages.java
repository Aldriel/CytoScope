package com.bioinfo.cytoscope.desktop.i18n;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Localized texts of the application for one language.
 *
 * <p>Texts are {@link MessageFormat} patterns stored in {@code messages_<lang>.properties}. The
 * bundles use the typographic apostrophe (’) because a plain one is a quoting character in {@link
 * MessageFormat} patterns.
 */
public final class Messages {

    /** Languages the user interface is translated into. */
    public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH);

    private static final String BUNDLE = "com.bioinfo.cytoscope.desktop.i18n.messages";

    private final Locale locale;
    private final ResourceBundle bundle;

    private Messages(Locale locale) {
        this.locale = locale;
        this.bundle =
                ResourceBundle.getBundle(
                        BUNDLE,
                        locale,
                        ResourceBundle.Control.getNoFallbackControl(
                                ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /**
     * Returns the texts of the closest supported language; unsupported languages fall back to
     * English.
     */
    public static Messages forLocale(Locale locale) {
        return new Messages(supportedLocale(locale));
    }

    /** Returns the supported locale with the same language, or English. */
    public static Locale supportedLocale(Locale locale) {
        if (locale != null) {
            for (Locale supported : SUPPORTED_LOCALES) {
                if (supported.getLanguage().equals(locale.getLanguage())) {
                    return supported;
                }
            }
        }
        return Locale.ENGLISH;
    }

    public Locale locale() {
        return locale;
    }

    /** Returns whether {@code key} is a message key of the bundle. */
    public boolean has(String key) {
        return key != null && bundle.containsKey(key);
    }

    /**
     * Formats a message.
     *
     * @param key message key
     * @param args values of the {@code {0}}, {@code {1}}… placeholders
     * @return the formatted message, or {@code !key!} if the key is unknown
     */
    public String get(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        return new MessageFormat(pattern, locale).format(args);
    }

    /** Returns every message key of the bundle. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(bundle.keySet());
    }

    /** Returns the raw pattern of a message, or {@code null} if the key is unknown. */
    String pattern(String key) {
        return bundle.containsKey(key) ? bundle.getString(key) : null;
    }
}
