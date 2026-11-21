package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** How much a line geometry finding costs. */
public enum Severity implements Valued {
    /** Written down, nothing more. */
    NOTE("note"),
    /** The line runs with something on the daily sheet. */
    RESTRICT("restrict"),
    /** The line does not turn until something is done. */
    STOP("stop");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether a finding of this severity takes the lift out.
     *
     * @return true for a stop
     */
    public boolean stopsTheLift() {
        return this == STOP;
    }
}
