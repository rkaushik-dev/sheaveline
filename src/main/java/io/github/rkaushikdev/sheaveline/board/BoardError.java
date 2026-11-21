package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A filing was built or read in a way that cannot hold. */
public class BoardError extends SheavelineError {

    /**
     * An error carrying the sentence the filing clerk sees.
     *
     * @param message the sentence
     */
    public BoardError(String message) {
        super(message);
    }
}
