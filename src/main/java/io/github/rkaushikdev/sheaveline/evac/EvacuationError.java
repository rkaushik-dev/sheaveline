package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** An evacuation question was asked in a way that has no answer. */
public class EvacuationError extends SheavelineError {

    /**
     * An error carrying the sentence the evacuation planner sees.
     *
     * @param message the sentence
     */
    public EvacuationError(String message) {
        super(message);
    }
}
