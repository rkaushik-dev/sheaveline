package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** Something went wrong between the domain and the database. */
public class StorageError extends SheavelineError {

    /**
     * A failure with the sentence the person at the counter reads.
     *
     * @param message the sentence
     */
    public StorageError(String message) {
        super(message);
    }

    /**
     * A failure with the sentence and what went wrong underneath.
     *
     * @param message the sentence
     * @param cause the underlying failure
     */
    public StorageError(String message, Throwable cause) {
        super(message, cause);
    }
}
