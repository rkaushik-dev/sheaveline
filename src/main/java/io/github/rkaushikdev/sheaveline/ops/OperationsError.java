package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A daily record was built or read in a way that cannot hold. */
public class OperationsError extends SheavelineError {

    /**
     * An error carrying the sentence the operator or the mechanic reads.
     *
     * @param message the sentence
     */
    public OperationsError(String message) {
        super(message);
    }
}
