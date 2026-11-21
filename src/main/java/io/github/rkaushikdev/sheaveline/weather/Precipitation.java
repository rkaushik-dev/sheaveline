package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** What is falling, which matters more than how much of it. */
public enum Precipitation implements Valued {
    /** Nothing is falling. */
    NONE("none"),
    /** Snow. */
    SNOW("snow"),
    /** Soft hail, snow pellets rimed on the way down. */
    GRAUPEL("graupel"),
    /** Rain. */
    RAIN("rain"),
    /** Rain that freezes on whatever it lands on. */
    FREEZING_RAIN("freezing rain");

    private final String value;

    Precipitation(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the rope runs wet in it.
     *
     * @return true for rain and freezing rain
     */
    public boolean wetsTheRope() {
        return this == RAIN || this == FREEZING_RAIN;
    }

    /**
     * Whether it puts ice on the grips and the towers.
     *
     * @return true only for freezing rain
     */
    public boolean buildsIce() {
        return this == FREEZING_RAIN;
    }
}
