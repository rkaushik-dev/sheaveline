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
 *
 * <p>A fourth question came later: did the machine stop itself. A run of those inside an hour calls
 * the mechanic out. So each kind answers all four in its own row, read across, rather than four
 * lists of kinds kept in step by hand.
 */
public enum StopKind implements Valued {
    /** The operator hit the button. */
    OPERATOR("operator stop", false, false, true, false),
    /** The loading gate tripped. */
    GATE("loading gate", false, false, true, true),
    /** The safety circuit opened. */
    SAFETY_CIRCUIT("safety circuit", false, true, true, true),
    /** A rope position detector tripped. */
    ROPE_POSITION("rope position", true, true, true, true),
    /** The power went. */
    POWER("power", false, false, false, false),
    /** The weather held it. */
    WEATHER("weather", false, false, false, false),
    /** The line was evacuated. */
    EVACUATION("evacuation", true, true, true, false),
    /** The rope came off a sheave train. */
    DEROPEMENT("deropement", true, true, true, false),
    /** A stop somebody planned. */
    SCHEDULED("scheduled", false, false, false, false);

    private final String value;
    private final boolean reportable;
    private final boolean lineCheck;
    private final boolean chargeable;
    private final boolean trip;

    StopKind(String value, boolean reportable, boolean lineCheck, boolean chargeable, boolean trip) {
        this.value = value;
        this.reportable = reportable;
        this.lineCheck = lineCheck;
        this.chargeable = chargeable;
        this.trip = trip;
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
        return reportable;
    }

    /**
     * Whether the line is ridden before it runs again, however short the stop was.
     *
     * @return true for a deropement, a rope position stop, an evacuation and a safety circuit stop
     */
    public boolean alwaysNeedsALineCheck() {
        return lineCheck;
    }

    /**
     * Whether the stop is the machine's fault, which a power cut and a wind hold are not.
     *
     * @return false for power, weather and scheduled stops
     */
    public boolean countsAgainstTheMachine() {
        return chargeable;
    }

    /**
     * Whether the machine stopped itself rather than somebody stopping it, which is what a run of
     * repeated stops is counted on.
     *
     * @return true for a loading gate, a safety circuit and a rope position stop
     */
    public boolean isATrip() {
        return trip;
    }
}
