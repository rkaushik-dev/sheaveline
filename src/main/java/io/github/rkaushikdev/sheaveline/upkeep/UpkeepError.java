package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** An interval or a record was described in a way that cannot hold. */
public class UpkeepError extends SheavelineError {

    /**
     * An error carrying the sentence the mechanic reads.
     *
     * @param message the sentence
     */
    public UpkeepError(String message) {
        super(message);
    }
}
