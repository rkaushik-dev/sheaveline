package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * Which way a sheave assembly bends the rope.
 *
 * <p>The sign convention is the one the drawings use. A positive deviation bends the rope downward,
 * which is a support. A negative deviation bends it upward, which is a hold down. Where the profile
 * changes sign inside one tower the assembly carries both, and those are the towers that give
 * trouble, because a rope that lifts off a hold down is a deropement waiting to happen.
 */
public enum AssemblyKind implements Valued {
    /** The rope is pushed down onto the sheaves on a rise. */
    SUPPORT("support"),
    /** The rope is pulled up against the sheaves in a dip. */
    HOLD_DOWN("hold down"),
    /** Both, where the profile changes sign inside one tower. */
    COMBINATION("combination");

    private final String value;

    AssemblyKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the rope presses onto the sheaves rather than lifting off them.
     *
     * @return true for a support or a combination
     */
    public boolean carriesDownForce() {
        return this == SUPPORT || this == COMBINATION;
    }

    /**
     * Whether the rope pulls up against the sheaves.
     *
     * @return true for a hold down or a combination
     */
    public boolean carriesUpForce() {
        return this == HOLD_DOWN || this == COMBINATION;
    }
}
