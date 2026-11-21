package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * Where a brake acts, which decides what it can stop.
 *
 * <p>Every aerial lift carries a service brake on the drive train and an emergency brake on the
 * bullwheel, and any lift that could run away downhill under load carries a backstop as well.
 */
public enum BrakeKind implements Valued {
    /** The brake on the drive train. */
    SERVICE("service"),
    /** The brake that grips the bullwheel. */
    EMERGENCY("emergency"),
    /** The mechanical stop against rollback. */
    BACKSTOP("backstop");

    private final String value;

    BrakeKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the brake grips the bullwheel, so it works with the shaft broken.
     *
     * @return true for the emergency brake
     */
    public boolean actsOnBullwheel() {
        return this == EMERGENCY;
    }

    /**
     * Whether the brake holds the line against running back downhill.
     *
     * @return true for the backstop
     */
    public boolean holdsAgainstRollback() {
        return this == BACKSTOP;
    }
}
