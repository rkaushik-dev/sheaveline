package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What a filing is about. */
public enum FilingKind implements Valued {
    /** A report of an incident. */
    INCIDENT("incident"),
    /** The annual inspection of a machine. */
    ANNUAL_INSPECTION("annual inspection"),
    /** The certificate of how many people a machine may carry. */
    CAPACITY("capacity certificate"),
    /** A change to a machine. */
    MODIFICATION("modification"),
    /** A new haul rope on a machine. */
    ROPE_CHANGE("rope change"),
    /** The month's return of minor incidents. */
    MONTHLY_RETURN("monthly return");

    private final String value;

    FilingKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether it is filed on a calendar rather than because something happened.
     *
     * @return true for the monthly return and the annual inspection
     */
    public boolean isRoutine() {
        return this == MONTHLY_RETURN || this == ANNUAL_INSPECTION;
    }

    /**
     * Whether the board writes back, which it does on anything that changes what a machine may do.
     *
     * @return true for a capacity certificate, a modification and a rope change
     */
    public boolean needsAnAcknowledgement() {
        return this == CAPACITY || this == MODIFICATION || this == ROPE_CHANGE;
    }
}
