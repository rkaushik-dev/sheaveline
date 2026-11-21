package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * What a walk of the whole line says about it. Three outcomes, because two is not enough: a line can
 * be fine, it can be runnable with something written on the daily sheet, or it can be a line that
 * does not turn until something is done about it.
 */
public enum GeometryVerdict implements Valued {
    /** Nothing wrong. */
    FIT("fit"),
    /** Runnable with a restriction on the daily sheet. */
    RESTRICTED("restricted"),
    /** Not to run. */
    UNFIT("unfit");

    private final String value;

    GeometryVerdict(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the line may run today.
     *
     * @return true unless unfit
     */
    public boolean mayRun() {
        return this != UNFIT;
    }
}
