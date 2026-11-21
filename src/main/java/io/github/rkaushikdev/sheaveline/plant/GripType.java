package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** How a carrier is attached to the haul rope. */
public enum GripType implements Valued {
    /** Clamped to the rope for good. */
    FIXED("fixed"),
    /** Lets go of the rope in the terminal so the carrier can slow down. */
    DETACHABLE("detachable"),
    /** A tow hanger, which is not a grip in the sense the slip test means. */
    TOW("tow");

    private final String value;

    GripType(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the carrier comes off the rope in the terminals.
     *
     * @return true for a detachable grip
     */
    public boolean detaches() {
        return this == DETACHABLE;
    }

    /**
     * Whether the grip is slip tested. A tow hanger is not a grip and never is.
     *
     * @return false for a tow
     */
    public boolean isTestedForSlip() {
        return this != TOW;
    }
}
