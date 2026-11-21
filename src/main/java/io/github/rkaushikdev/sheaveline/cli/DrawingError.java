package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A drawing file could not be read. */
final class DrawingError extends SheavelineError {

    DrawingError(String message) {
        super(message);
    }

    DrawingError(String message, Throwable cause) {
        super(message, cause);
    }
}
