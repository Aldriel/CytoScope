package com.bioinfo.cytoscope.desktop;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs test code on the JavaFX application thread, started once for every test class. The tests run
 * with the headless Monocle platform configured in the pom, so no screen is needed.
 */
public final class FxThread {

    private static final long TIMEOUT_SECONDS = 20;

    private FxThread() {}

    /** Starts the JavaFX platform if needed; it stays alive between the test classes. */
    public static synchronized void start() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // Started by a previous test class.
        }
        Platform.setImplicitExit(false);
    }

    /** Code run on the JavaFX thread that may throw. */
    @FunctionalInterface
    public interface Action {
        /**
         * Runs the code.
         *
         * @throws Exception any failure, rethrown in the test thread
         */
        void run() throws Exception;
    }

    /**
     * Runs {@code action} on the JavaFX thread and waits for it.
     *
     * @param action the code to run
     */
    public static void run(Action action) {
        call(
                () -> {
                    action.run();
                    return null;
                });
    }

    /**
     * Runs {@code action} on the JavaFX thread and returns its result; called from the JavaFX
     * thread, runs it at once.
     *
     * @param action the code to run
     * @param <T> type of the result
     * @return the result
     */
    public static <T> T call(Callable<T> action) {
        if (Platform.isFxApplicationThread()) {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(action.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        try {
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("The JavaFX thread did not answer", e);
        }
    }
}
