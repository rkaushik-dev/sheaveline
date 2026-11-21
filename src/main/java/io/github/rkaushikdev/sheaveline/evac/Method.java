package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** How the people in one carrier are coming down. */
public enum Method implements Valued {
    /** Low enough to step off onto the snow, with the line crew standing by. */
    STEP_OFF("step off"),
    /** Down a ladder. */
    LADDER("ladder"),
    /** Lowered on a rope system. */
    ROPE("rope");

    private final String value;

    Method(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether a rescue team has to do the work.
     *
     * @return false only for stepping off
     */
    public boolean needsATeam() {
        return this != STEP_OFF;
    }

    /**
     * Whether this is technical rope work.
     *
     * @return true only for the rope
     */
    public boolean isTechnical() {
        return this == ROPE;
    }
}
