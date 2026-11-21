package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The class the board files a machine under, and what that decides about it.
 *
 * <p>Half the rules downstream key off the class. A detachable runs the line fast because the
 * carriers slow down in the terminal, so its line speed limit is not the loading speed limit. A
 * surface lift has no carriers to evacuate, so it has no evacuation plan and never will. A tram runs
 * two cars on a fixed schedule and does not have an interval in the sense the capacity arithmetic
 * means.
 *
 * <p>The pairings between class, grip, carrier and speed limit sit on each constant as one row, so
 * a new machine on the hill is one line.
 */
public enum LiftClass implements Valued {
    /** A fixed grip chairlift. */
    FIXED_CHAIR(
            "fixed grip chairlift",
            GripType.FIXED,
            500,
            EnumSet.of(CarrierType.CHAIR_2, CarrierType.CHAIR_3, CarrierType.CHAIR_4)),
    /** A detachable chairlift. */
    DETACHABLE_CHAIR(
            "detachable chairlift",
            GripType.DETACHABLE,
            1200,
            EnumSet.of(CarrierType.CHAIR_4, CarrierType.CHAIR_6, CarrierType.CABIN_8)),
    /** A pulse gondola, cabins on a fixed grip in groups. */
    PULSE_GONDOLA("pulse gondola", GripType.FIXED, 500, EnumSet.of(CarrierType.CABIN_8)),
    /** A detachable gondola. */
    DETACHABLE_GONDOLA("detachable gondola", GripType.DETACHABLE, 1200, EnumSet.of(CarrierType.CABIN_8)),
    /** An aerial tramway, two cars back and forth. */
    AERIAL_TRAM("aerial tramway", GripType.FIXED, 2400, EnumSet.of(CarrierType.TRAM_CAR)),
    /** A surface lift: a t bar or a platter. */
    SURFACE_TOW("surface lift", GripType.TOW, 750, EnumSet.of(CarrierType.TBAR, CarrierType.PLATTER));

    private final String value;
    private final GripType grip;
    private final Speed lineSpeedLimit;
    private final Set<CarrierType> carriers;

    LiftClass(String value, GripType grip, long limitFpm, EnumSet<CarrierType> carriers) {
        this.value = value;
        this.grip = grip;
        this.lineSpeedLimit = Speed.ofFeetPerMinute(limitFpm);
        this.carriers = Collections.unmodifiableSet(carriers);
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether carriers hang off the rope, so a stalled line strands people in the air.
     *
     * @return false only for a surface lift
     */
    public boolean isAerial() {
        return this != SURFACE_TOW;
    }

    /**
     * Whether the carriers come off the rope in the terminals.
     *
     * @return true for the detachable chair and gondola
     */
    public boolean isDetachable() {
        return this == DETACHABLE_CHAIR || this == DETACHABLE_GONDOLA;
    }

    /**
     * Whether the cars run back and forth rather than round a loop.
     *
     * @return true for a tram
     */
    public boolean isReversible() {
        return this == AERIAL_TRAM;
    }

    /**
     * Whether the board wants an evacuation plan on file for this class.
     *
     * @return true for every machine in the air
     */
    public boolean needsEvacuationPlan() {
        return isAerial();
    }

    /**
     * How the carriers hold the rope.
     *
     * @return the grip this class uses
     */
    public GripType grip() {
        return grip;
    }

    /**
     * The fastest the rope may run for this class of machine.
     *
     * @return the line speed limit
     */
    public Speed lineSpeedLimit() {
        return lineSpeedLimit;
    }

    /**
     * The carriers this class takes.
     *
     * @return an unmodifiable set in declaration order, never empty
     */
    public Set<CarrierType> carriers() {
        return carriers;
    }

    /**
     * Whether this class takes that carrier.
     *
     * @param carrier the carrier
     * @return true when the pairing exists
     */
    public boolean accepts(CarrierType carrier) {
        return carriers.contains(carrier);
    }

    /**
     * Refuse a pairing that does not exist, and pass quietly when it does.
     *
     * @param carrier the carrier
     * @throws PlantError when this class does not carry it
     */
    public void require(CarrierType carrier) {
        if (!accepts(carrier)) {
            throw new PlantError("a " + value + " does not carry a " + carrier.value());
        }
    }
}
