package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What turns the bullwheel. */
public enum DriveType implements Valued {
    /** A direct current motor. */
    DC_MOTOR("direct current motor"),
    /** An alternating current drive. */
    AC_DRIVE("alternating current drive"),
    /** A diesel engine driving a hydraulic motor. */
    DIESEL_HYDRAULIC("diesel hydraulic");

    private final String value;

    DriveType(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the drive can run the rope at more than one speed.
     *
     * @return false for a diesel
     */
    public boolean hasVariableSpeed() {
        return this != DIESEL_HYDRAULIC;
    }

    /**
     * Whether the drive is its own power, so it also serves as the standby.
     *
     * @return true for a diesel
     */
    public boolean isPrimeMover() {
        return this == DIESEL_HYDRAULIC;
    }
}
