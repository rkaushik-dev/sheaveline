package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** The sun does not rise or set on that date at that place, or a job does not fit in the light. */
public class DaylightError extends SheavelineError {

    /**
     * A question the daylight cannot answer.
     *
     * @param message the sentence
     */
    public DaylightError(String message) {
        super(message);
    }
}
