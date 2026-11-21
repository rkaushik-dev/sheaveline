package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A personnel record was built or read in a way that cannot hold. */
public class PeopleError extends SheavelineError {

    /**
     * An error carrying the sentence the supervisor reads.
     *
     * @param message the sentence
     */
    public PeopleError(String message) {
        super(message);
    }
}
