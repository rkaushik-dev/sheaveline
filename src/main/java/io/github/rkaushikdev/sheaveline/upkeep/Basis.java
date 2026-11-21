package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** Which clock a job, or a part's life, runs on. */
public enum Basis implements Valued {
    /** The calendar: days, whether the machine ran or not. */
    CALENDAR("calendar"),
    /** The hour meter on the drive. */
    HOURS("running hours"),
    /** The cycle counter on the drive. */
    CYCLES("cycles");

    private final String value;

    Basis(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
