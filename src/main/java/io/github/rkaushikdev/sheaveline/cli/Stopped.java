package io.github.rkaushikdev.sheaveline.cli;

/** The person at the keyboard said no at a prompt, or walked away from it. Nothing was changed. */
final class Stopped extends RuntimeException {

    Stopped() {
        super("Aborted!");
    }
}
