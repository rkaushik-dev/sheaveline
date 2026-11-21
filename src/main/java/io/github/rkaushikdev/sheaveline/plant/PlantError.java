package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A machine was described in a way that does not exist. */
public class PlantError extends SheavelineError {

    /**
     * A description that cannot be built.
     *
     * @param message the sentence
     */
    public PlantError(String message) {
        super(message);
    }

    /**
     * A description that cannot be built, and what failed underneath.
     *
     * @param message the sentence
     * @param cause the underlying failure
     */
    public PlantError(String message, Throwable cause) {
        super(message, cause);
    }
}
