package com.bioinfo.cytoscope.fcs;

/**
 * Signals a file that cannot be read as an FCS file: invalid header, missing keyword, or a
 * layout this application does not support.
 */
public final class FcsFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what is wrong with the file, in English
     */
    public FcsFormatException(String message) {
        super(message);
    }
}
