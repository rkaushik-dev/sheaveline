package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A report was laid out in a way that will not print. */
public class ReportError extends SheavelineError {

    /**
     * An error carrying the sentence that says what will not fit.
     *
     * @param message the sentence
     */
    public ReportError(String message) {
        super(message);
    }
}
