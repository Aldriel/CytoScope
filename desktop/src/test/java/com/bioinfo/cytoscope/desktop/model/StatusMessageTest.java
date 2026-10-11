package com.bioinfo.cytoscope.desktop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.cytoscope.desktop.i18n.Messages;
import com.bioinfo.cytoscope.experiment.MissingSampleException;
import com.bioinfo.cytoscope.fcs.FcsFormatException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.stream.Stream;

class StatusMessageTest {

    private static final Messages EN = Messages.forLocale(Locale.ENGLISH);
    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(new FcsFormatException("no TEXT segment"), "error.parsing"),
                Arguments.of(new MissingSampleException("t3"), "error.missingSample"),
                Arguments.of(new IOException("disk full"), "error.file"),
                Arguments.of(
                        new UncheckedIOException(new IOException("gone")), "error.file"),
                Arguments.of(new IllegalArgumentException("bad input"), "error.invalidInput"),
                Arguments.of(new IllegalStateException("bad state"), "error.invalidState"),
                Arguments.of(new UnsupportedOperationException(), "error.unexpected"));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void exceptionsMapToTranslatedKeys(Exception e, String key) {
        StatusMessage status = StatusMessage.fromException(e);
        assertEquals(key, status.key());
        assertTrue(status.error());
        assertTrue(EN.has(key) && FR.has(key), key);
    }

    @Test
    void exceptionWithoutMessageUsesItsClassName() {
        assertEquals(
                "UnsupportedOperationException",
                StatusMessage.fromException(new UnsupportedOperationException()).detail());
    }

    @Test
    void renderInfoAndErrors() {
        StatusMessage info = StatusMessage.info("status.tubeRemoved", "B2");
        assertEquals("Tube B2 removed.", info.render(EN));
        assertEquals("Tube B2 retiré.", info.render(FR));
        assertNull(info.detail());
        assertFalse(info.error());

        assertEquals(
                "Error: load tubes first.", StatusMessage.error("error.noTubes").render(EN));
        assertEquals(
                "Error: the file could not be read or written (disk full)",
                StatusMessage.fromException(new IOException("disk full")).render(EN));
    }

    @Test
    void choiceFormatsFollowTheNumbers() {
        assertEquals(
                "1 file read; the experiment has 3 tubes.",
                StatusMessage.info("status.filesOpened", 1, 3).render(EN));
        assertEquals(
                "2 fichiers lus ; l’expérience compte 1 tube.",
                StatusMessage.info("status.filesOpened", 2, 1).render(FR));
    }

    @Test
    void argumentsAreDefensivelyCopied() {
        Object[] args = {"a"};
        StatusMessage status = new StatusMessage("status.tubeRemoved", args, false, null);
        args[0] = "b";
        assertEquals("a", status.args()[0]);
        status.args()[0] = "c";
        assertEquals("a", status.args()[0]);
        assertThrows(NullPointerException.class, () -> StatusMessage.info(null));
    }
}
