package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What kind of operating day a date is. Every staffing and hours rule reads this. */
public enum DayType implements Valued {
    /** Outside the season, or on the closure list. */
    CLOSED("closed"),
    /** The machines run for the crew but the public does not ride. */
    PREVIEW("preview"),
    /** Inside a holiday period. */
    HOLIDAY("holiday"),
    /** On or after the start of the spring schedule. */
    SPRING("spring"),
    /** A Saturday or a Sunday. */
    WEEKEND("weekend"),
    /** Anything else. */
    MIDWEEK("midweek");

    private final String value;

    DayType(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the machines run and the crew is on the hill.
     *
     * @return false only on a closed day
     */
    public boolean isOperating() {
        return this != CLOSED;
    }

    /**
     * Whether the public may ride.
     *
     * @return true on an operating day that is not a preview
     */
    public boolean isPublic() {
        return isOperating() && this != PREVIEW;
    }
}
