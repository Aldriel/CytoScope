package com.bioinfo.cytoscope.server.workspace;

/** Thrown when a file does not fit in the memory the server sets aside for the workspaces. */
public final class CapacityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message explanation sent to the client
     */
    public CapacityException(String message) {
        super(message);
    }
}
