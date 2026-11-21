package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.util.Valued;

/**
 * Why the rope stopped.
 *
 * <p>Three questions get asked of every stop and they do not have the same answer. Does the board
 * hear about it. Does somebody walk or ride the line before it runs again. Does it count against
 * the machine when the season's availability is worked out. A power cut is not the machine's fault
 * and does not count against it, but the line still gets ridden before it turns after a long one,
 * because carriers have been sitting in the wind.
 */
public enum StopKind implements Valued {
    /** The operator hit the button. */
    OPERATOR("operator stop"),
    /** The loading gate tripped. */
    GATE("loading gate"),
    /** The safety circuit opened. */
    SAFETY_CIRCUIT("safety circuit"),
    /** A rope position detector tripped. */
    ROPE_POSITION("rope position"),
    /** The power went. */
    POWER("power"),
    /** The weather held it. */
    WEATHER("weather"),
    /** The line was evacuated. */
    EVACUATION("evacuation"),
    /** The rope came off a sheave train. */
    DEROPEMENT("deropement"),
    /** A stop somebody planned. */
    SCHEDULED("scheduled");

    private final String value;

    StopKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the board hears about this kind whatever else happens.
     *
     * @return true for a deropement, an evacuation and a rope position stop
     */
    public boolean isReportable() {
        return this == DEROPEMENT || this == EVACUATION || this == ROPE_POSITION;
    }

    /**
     * Whether the line is ridden before it runs again, however short the stop was.
     *
     * @return true for a deropement, a rope position stop, an evacuation and a safety circuit stop
     */
    public boolean alwaysNeedsALineCheck() {
        return this == DEROPEMENT || this == ROPE_POSITION || this == EVACUATION || this == SAFETY_CIRCUIT;
    }

    /**
     * Whether the stop is the machine's fault, which a power cut and a wind hold are not.
     *
     * @return false for power, weather and scheduled stops
     */
    public boolean countsAgainstTheMachine() {
        return this != POWER && this != WEATHER && this != SCHEDULED;
    }
}
