package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A key was presented that will not do: missing, malformed, wrong, lapsed or too junior. */
public class AuthError extends SheavelineError {

    /**
     * A refusal.
     *
     * @param message why the key will not do
     */
    public AuthError(String message) {
        super(message);
    }
}
