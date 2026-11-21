package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A rope measurement was taken or recorded in a way that cannot hold. */
public class RopeworkError extends SheavelineError {

    /**
     * An error carrying the sentence the rope file reader sees.
     *
     * @param message the sentence
     */
    public RopeworkError(String message) {
        super(message);
    }
}
