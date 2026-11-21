package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** The wire grade the mill drew. */
public enum RopeGrade implements Valued {
    /** Improved plow steel. */
    IPS("improved plow steel"),
    /** Extra improved plow steel. */
    EIP("extra improved plow steel"),
    /** Extra extra improved plow steel. */
    EEIP("extra extra improved plow steel");

    private final String value;

    RopeGrade(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
