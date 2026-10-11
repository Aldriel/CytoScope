package com.bioinfo.cytoscope.experiment;

/**
 * Signals a tube whose values are no longer available, for example after the web server
 * dropped them; the client then sends the file again.
 */
public final class MissingSampleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Identifier of the missing tube. */
    private final String sampleId;

    /**
     * Creates the exception.
     *
     * @param sampleId identifier of the missing tube
     */
    public MissingSampleException(String sampleId) {
        super("The tube " + sampleId + " is no longer available");
        this.sampleId = sampleId;
    }

    /**
     * Returns the identifier of the missing tube.
     *
     * @return the identifier
     */
    public String sampleId() {
        return sampleId;
    }
}
