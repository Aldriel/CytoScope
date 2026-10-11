package com.bioinfo.cytoscope.server;

/** Error mapped to an HTTP status code by {@link JsonHandler}. */
public class HttpError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    /**
     * Creates an error.
     *
     * @param status HTTP status code sent to the client, such as 400 or 404
     * @param message explanation sent to the client
     */
    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status code sent to the client.
     *
     * @return the status
     */
    public int status() {
        return status;
    }
}
