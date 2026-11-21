package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What is holding the machine. */
public enum HoldReason implements Valued {
    /** The peak wind is over the class's gust limit. */
    GUST("gust"),
    /** The wind across the line is over the class's crosswind limit. */
    CROSSWIND("crosswind"),
    /** The wind chill is under the class's floor. */
    CHILL("wind chill"),
    /** Ice on the grips of a machine that holds on it. */
    ICING("icing"),
    /** A strike inside the lightning radius. */
    LIGHTNING("lightning"),
    /** The operator cannot see the line from the terminal. */
    VISIBILITY("visibility");

    private final String value;

    HoldReason(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether a fresh reading inside the limit is enough to run again.
     *
     * @return false only for lightning, which runs on a clock
     */
    public boolean clearsOnReading() {
        return this != LIGHTNING;
    }
}
