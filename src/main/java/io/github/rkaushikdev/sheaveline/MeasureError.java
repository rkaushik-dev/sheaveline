package io.github.rkaushikdev.sheaveline;

/**
 * A measured quantity was built or read wrong.
 *
 * <p>The http surface turns this family into a field level complaint rather than a server error,
 * because a value that does not read as a measure is something the client sent.
 */
public class MeasureError extends SheavelineError {

    /**
     * A measure error with its sentence.
     *
     * @param message the sentence
     */
    public MeasureError(String message) {
        super(message);
    }
}
