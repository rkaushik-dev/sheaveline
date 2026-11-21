package io.github.rkaushikdev.sheaveline;

/**
 * Root of every error the lift department records raise.
 *
 * <p>One root so a caller can catch everything from here without catching the platform's own
 * exceptions, and narrow classes underneath so the http surface can map a failure onto a status
 * code without reading messages.
 */
public class SheavelineError extends RuntimeException {

    /**
     * An error carrying the sentence the person at the counter reads.
     *
     * @param message the sentence
     */
    public SheavelineError(String message) {
        super(message);
    }

    /**
     * An error carrying the sentence and the failure that led to it.
     *
     * @param message the sentence
     * @param cause what went wrong underneath
     */
    public SheavelineError(String message, Throwable cause) {
        super(message, cause);
    }
}
