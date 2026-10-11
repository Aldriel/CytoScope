package com.bioinfo.cytoscope.desktop.model;

import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.experiment.MissingSampleException;
import com.bioinfo.cytoscope.fcs.FcsFormatException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Message of the status bar, kept as a key and arguments so it is translated again when the
 * language changes.
 *
 * @param key message key
 * @param args message arguments
 * @param error whether the message reports a failure
 * @param detail technical detail appended to errors (messages of the core library, in English); may
 *     be {@code null}
 */
public record StatusMessage(String key, Object[] args, boolean error, String detail) {

    /** Message shown when the application starts. */
    public static final StatusMessage READY = info("status.ready");

    /** Validates the message and copies its arguments. */
    public StatusMessage {
        Objects.requireNonNull(key, "key");
        args = args.clone();
    }

    /**
     * Returns an information message.
     *
     * @param key message key
     * @param args message arguments
     * @return the message
     */
    public static StatusMessage info(String key, Object... args) {
        return new StatusMessage(key, args, false, null);
    }

    /**
     * Returns an error message without technical detail.
     *
     * @param key message key
     * @param args message arguments
     * @return the message
     */
    public static StatusMessage error(String key, Object... args) {
        return new StatusMessage(key, args, true, null);
    }

    /**
     * Returns an error message describing {@code e}, with a translated explanation and the raw
     * detail.
     *
     * @param e failure of an operation
     * @return the message
     */
    public static StatusMessage fromException(Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String key =
                switch (e) {
                    case FcsFormatException parsing -> "error.parsing";
                    case MissingSampleException missing -> "error.missingSample";
                    case IOException io -> "error.file";
                    case UncheckedIOException io -> "error.file";
                    case IllegalArgumentException invalid -> "error.invalidInput";
                    case IllegalStateException state -> "error.invalidState";
                    default -> "error.unexpected";
                };
        return new StatusMessage(key, new Object[0], true, detail);
    }

    @Override
    public Object[] args() {
        return args.clone();
    }

    /**
     * Returns the message in the language of {@code m}.
     *
     * @param m messages of the current language
     * @return the text of the status bar
     */
    public String render(Messages m) {
        String text = m.get(key, args);
        if (!error) {
            return text;
        }
        String message = detail == null ? text : m.get("status.errorDetail", text, detail);
        return m.get("status.error", message);
    }
}
