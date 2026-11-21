package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A capacity question was asked in a way that has no answer. */
public class CapacityError extends SheavelineError {

    /**
     * An error carrying the sentence the operations manager reads.
     *
     * @param message the sentence
     */
    public CapacityError(String message) {
        super(message);
    }
}
