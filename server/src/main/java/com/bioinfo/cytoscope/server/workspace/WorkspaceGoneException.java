package com.bioinfo.cytoscope.server.workspace;

/** Thrown for a workspace that has expired or never existed. */
public final class WorkspaceGoneException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param id identifier of the workspace
     */
    public WorkspaceGoneException(String id) {
        super("The workspace " + id + " has expired: load the files again");
    }
}
