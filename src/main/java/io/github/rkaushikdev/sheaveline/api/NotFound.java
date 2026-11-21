package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** Asked for something that is not on file. */
public class NotFound extends SheavelineError {

    /**
     * Nothing by that name.
     *
     * @param message what was asked for, such as {@code there is no lift ZZ}
     */
    public NotFound(String message) {
        super(message);
    }

    /**
     * Nothing by that name, because the store said so.
     *
     * @param message what was asked for
     * @param cause the store's refusal
     */
    public NotFound(String message, Throwable cause) {
        super(message, cause);
    }
}
