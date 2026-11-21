package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** One piece of rescue kit, and what it does. */
public enum KitItem implements Valued {
    /** A ladder, for the carriers low enough to work from the ground. */
    LADDER("ladder"),
    /** A rope system, for lowering people from a carrier out of the ladder's reach. */
    ROPE_SYSTEM("rope system"),
    /** A seat to lower somebody in. */
    SEAT("rescue seat"),
    /** A harness for somebody working aloft. */
    HARNESS("harness"),
    /** A radio. */
    RADIO("radio");

    private final String value;

    KitItem(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether this is kit that decides how high the team can work.
     *
     * @return true for the ladder and the rope system
     */
    public boolean reaches() {
        return this == LADDER || this == ROPE_SYSTEM;
    }
}
