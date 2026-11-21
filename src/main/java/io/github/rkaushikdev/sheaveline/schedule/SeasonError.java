package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A season was described in a way that cannot hold. */
public class SeasonError extends SheavelineError {

    /**
     * A season that cannot hold.
     *
     * @param message the sentence
     */
    public SeasonError(String message) {
        super(message);
    }
}
