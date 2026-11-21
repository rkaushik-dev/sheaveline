package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What a terminal does, which is not the same as where it is. */
public enum TerminalRole implements Valued {
    /** Turns the rope. */
    DRIVE("drive"),
    /** Brings the rope round. */
    RETURN("return"),
    /** Turns the rope and holds its tension. */
    DRIVE_TENSION("drive tension"),
    /** Brings the rope round and holds its tension. */
    RETURN_TENSION("return tension");

    private final String value;

    TerminalRole(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether this terminal turns the rope.
     *
     * @return true for either drive role
     */
    public boolean drives() {
        return this == DRIVE || this == DRIVE_TENSION;
    }

    /**
     * Whether this terminal holds the rope's tension.
     *
     * @return true for either tension role
     */
    public boolean tensions() {
        return this == DRIVE_TENSION || this == RETURN_TENSION;
    }
}
