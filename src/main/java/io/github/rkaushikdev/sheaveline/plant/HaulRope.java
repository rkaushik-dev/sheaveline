package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The piece of a rope on this machine: its serial, the date it went on, its length and the
 * splices in it. The specification is what the mill makes; this is the reel that was cut from it.
 *
 * @param serial the mill mark
 * @param spec what the mill makes
 * @param installed the day it went on
 * @param length how much of it is on the machine
 * @param splices how many splices are in it
 */
public record HaulRope(RopeSerial serial, RopeSpec spec, LocalDate installed, Length length, int splices) {

    /**
     * A rope on a machine.
     *
     * @throws PlantError when it has no length, negative splices, or went on before it was drawn
     */
    public HaulRope {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(installed, "installed");
        Objects.requireNonNull(length, "length");
        if (length.mils() <= 0) {
            throw new PlantError("rope " + serial + " has no length");
        }
        if (splices < 0) {
            throw new PlantError("rope " + serial + " cannot have negative splices");
        }
        if (installed.getYear() < serial.year()) {
            throw new PlantError("rope " + serial + " went on before it was drawn");
        }
    }

    /**
     * A rope with the one splice an endless loop has.
     *
     * @param serial the mill mark
     * @param spec what the mill makes
     * @param installed the day it went on
     * @param length how much of it is on the machine
     */
    public HaulRope(RopeSerial serial, RopeSpec spec, LocalDate installed, Length length) {
        this(serial, spec, installed, length, 1);
    }

    /**
     * The diameter, which is the specification's.
     *
     * @return the nominal diameter
     */
    public Length diameter() {
        return spec.nominalDiameter();
    }

    /**
     * Days since the rope went on.
     *
     * @param on the day asked about
     * @return whole days
     * @throws PlantError when the day is before the rope went on
     */
    public long ageInDays(LocalDate on) {
        if (on.isBefore(installed)) {
            throw new PlantError(on + " is before rope " + serial + " went on");
        }
        return ChronoUnit.DAYS.between(installed, on);
    }

    /**
     * Whole years on the machine, which is how the rope file counts.
     *
     * @param on the day asked about
     * @return whole spans of 365 days
     */
    public long seasonsInService(LocalDate on) {
        return ageInDays(on) / 365;
    }

    /**
     * What the rope on the machine weighs.
     *
     * @return its weight
     */
    public Load weight() {
        return spec.weightOf(length);
    }

    /**
     * Whether the rope is a loop. A loop has at least one splice; a tram rope has none and two ends.
     *
     * @return true when spliced
     */
    public boolean isEndless() {
        return splices > 0;
    }

    /**
     * The record after one more splice.
     *
     * @return a new rope with one more splice
     */
    public HaulRope withSplice() {
        return new HaulRope(serial, spec, installed, length, splices + 1);
    }

    /** As the rope file prints it, such as {@code FAT-2019-04412 1.378" 6x25 filler wire, 9800 ft}. */
    @Override
    public String toString() {
        return serial + " " + spec + ", " + Decimals.fixed(length.feet(), 0) + " ft";
    }
}
