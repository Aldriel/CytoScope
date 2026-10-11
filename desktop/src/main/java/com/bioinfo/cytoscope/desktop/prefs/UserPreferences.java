package com.bioinfo.cytoscope.desktop.prefs;

import com.bioinfo.cytoscope.desktop.i18n.Messages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * User settings remembered between sessions: language, text size, window size and last directory
 * used.
 */
public final class UserPreferences {

    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 24;
    public static final int DEFAULT_FONT_SIZE = 13;

    public static final double DEFAULT_WINDOW_WIDTH = 1200;
    public static final double DEFAULT_WINDOW_HEIGHT = 800;
    private static final double MIN_WINDOW_SIZE = 400;

    private static final String FONT_SIZE = "fontSize";
    private static final String LANGUAGE = "language";
    private static final String WINDOW_WIDTH = "windowWidth";
    private static final String WINDOW_HEIGHT = "windowHeight";
    private static final String WINDOW_MAXIMIZED = "windowMaximized";
    private static final String LAST_DIRECTORY = "lastDirectory";
    private static final String SAVE_DIRECTORY = "saveDirectory";

    private final Preferences node;

    public UserPreferences(Preferences node) {
        this.node = node;
    }

    /** Returns the settings of the current user. */
    public static UserPreferences load() {
        return new UserPreferences(Preferences.userRoot().node("com/bioinfo/cytoscope/desktop"));
    }

    /** Returns the saved text size in pixels, within the supported range. */
    public int fontSize() {
        return clampFontSize(node.getInt(FONT_SIZE, DEFAULT_FONT_SIZE));
    }

    public void setFontSize(int size) {
        node.putInt(FONT_SIZE, clampFontSize(size));
    }

    /** Returns the size limited to [{@value #MIN_FONT_SIZE}, {@value #MAX_FONT_SIZE}]. */
    public static int clampFontSize(int size) {
        return Math.clamp(size, MIN_FONT_SIZE, MAX_FONT_SIZE);
    }

    /** Returns the saved language, or the system language if supported, otherwise English. */
    public Locale locale() {
        String tag = node.get(LANGUAGE, null);
        return Messages.supportedLocale(
                tag == null ? Locale.getDefault() : Locale.forLanguageTag(tag));
    }

    public void setLocale(Locale locale) {
        node.put(LANGUAGE, Messages.supportedLocale(locale).toLanguageTag());
    }

    /** Returns the saved width of the window content, at least 400 pixels. */
    public double windowWidth() {
        return Math.max(MIN_WINDOW_SIZE, node.getDouble(WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH));
    }

    /** Returns the saved height of the window content, at least 400 pixels. */
    public double windowHeight() {
        return Math.max(MIN_WINDOW_SIZE, node.getDouble(WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT));
    }

    public boolean windowMaximized() {
        return node.getBoolean(WINDOW_MAXIMIZED, false);
    }

    /**
     * Saves the window state; the size is kept while maximized so the window returns to it when
     * restored.
     */
    public void setWindowState(double width, double height, boolean maximized) {
        node.putBoolean(WINDOW_MAXIMIZED, maximized);
        if (!maximized) {
            node.putDouble(WINDOW_WIDTH, width);
            node.putDouble(WINDOW_HEIGHT, height);
        }
    }

    /** Returns the last directory used to open a file, if it still exists. */
    public Optional<Path> lastDirectory() {
        return existingDirectory(LAST_DIRECTORY);
    }

    public void setLastDirectory(Path directory) {
        node.put(LAST_DIRECTORY, directory.toAbsolutePath().toString());
    }

    /** Returns the last directory where a downloaded file was saved, if it still exists. */
    public Optional<Path> saveDirectory() {
        return existingDirectory(SAVE_DIRECTORY);
    }

    public void setSaveDirectory(Path directory) {
        node.put(SAVE_DIRECTORY, directory.toAbsolutePath().toString());
    }

    private Optional<Path> existingDirectory(String key) {
        String value = node.get(key, null);
        if (value == null) {
            return Optional.empty();
        }
        Path directory = Path.of(value);
        return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
    }

    /** Writes pending changes to persistent storage. */
    public void flush() {
        try {
            node.flush();
        } catch (BackingStoreException e) {
            // Settings are a convenience: failing to persist them must not prevent closing.
        }
    }
}
