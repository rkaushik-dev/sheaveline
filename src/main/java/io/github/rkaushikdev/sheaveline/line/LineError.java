package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A line geometry question was asked in a way that has no answer. */
public class LineError extends SheavelineError {

    /**
     * An error carrying the sentence the engineer reads.
     *
     * @param message the sentence
     */
    public LineError(String message) {
        super(message);
    }
}
