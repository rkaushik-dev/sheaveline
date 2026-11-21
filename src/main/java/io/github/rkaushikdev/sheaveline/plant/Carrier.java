package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import java.util.Objects;
import java.util.Optional;

/**
 * One chair, cabin, car or bar, with the number painted on its back.
 *
 * <p>A carrier is the only part of the machine the public touches, and it is also the part that gets
 * pulled off the line, worked on, and put back somewhere else in the sequence. A carrier that hangs
 * off the rope has a grip; a surface carrier has none, and the record says so with an empty grip
 * rather than a value everyone has to remember to check.
 *
 * @param number the number painted on it, from one
 * @param kind what it is
 * @param tare its empty weight
 * @param hanger the hanger length
 * @param grip its grip, empty for a surface carrier
 * @param restrainingBar whether it has a bar
 * @param inService whether it is on the line
 */
public record Carrier(
        int number,
        CarrierType kind,
        Load tare,
        Length hanger,
        Optional<Grip> grip,
        boolean restrainingBar,
        boolean inService) {

    /**
     * A carrier that describes a real carrier.
     *
     * @throws PlantError for a number under one, no empty weight or hanger, a grip on a surface
     *     carrier or none on an aerial one, or a bar on an enclosed carrier
     */
    public Carrier {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tare, "tare");
        Objects.requireNonNull(hanger, "hanger");
        Objects.requireNonNull(grip, "grip");
        if (number < 1) {
            throw new PlantError("carriers are numbered from one");
        }
        if (tare.pounds() <= 0) {
            throw new PlantError("carrier " + number + " has no empty weight");
        }
        if (hanger.mils() <= 0) {
            throw new PlantError("carrier " + number + " has no hanger length");
        }
        if (kind.isSurface() && grip.isPresent()) {
            throw new PlantError("carrier " + number + " is a surface carrier and has no grip");
        }
        if (!kind.isSurface() && grip.isEmpty()) {
            throw new PlantError("carrier " + number + " hangs off the rope and needs a grip");
        }
        if (restrainingBar && kind.isEnclosed()) {
            throw new PlantError("carrier " + number + " is enclosed and has no bar");
        }
    }

    /**
     * How many it seats.
     *
     * @return the kind's seat count
     */
    public int seats() {
        return kind.seats();
    }

    /**
     * What the board says the carrier is designed to carry.
     *
     * @return the passenger design load times the seats
     */
    public Load designLoad() {
        return Load.PASSENGER_DESIGN_LOAD.scaled(seats());
    }

    /**
     * The carrier loaded to its design load.
     *
     * @return tare plus design load
     */
    public Load gross() {
        return tare.plus(designLoad());
    }

    /**
     * The grip, for a carrier that must have one.
     *
     * @return the grip
     * @throws PlantError when the carrier has none
     */
    public Grip gripOf() {
        return grip.orElseThrow(() -> new PlantError("carrier " + number + " has no grip"));
    }

    /**
     * Whether the carrier belongs on a class of machine: the class takes the kind, and the grip
     * matches the class's grip, or there is none and the class tows.
     *
     * @param liftClass the class
     * @return true when it belongs
     */
    public boolean suits(LiftClass liftClass) {
        if (!liftClass.accepts(kind)) {
            return false;
        }
        return grip.map(one -> one.kind() == liftClass.grip()).orElse(liftClass.grip() == GripType.TOW);
    }

    /**
     * Refuse a carrier that does not belong on the class.
     *
     * @param liftClass the class
     * @throws PlantError when it does not belong
     */
    public void requireSuits(LiftClass liftClass) {
        if (!suits(liftClass)) {
            throw new PlantError("carrier " + number + " does not belong on a " + liftClass.value());
        }
    }

    /**
     * The same carrier, off the line. Records do not get deleted here.
     *
     * @return the withdrawn carrier
     */
    public Carrier withdrawn() {
        return new Carrier(number, kind, tare, hanger, grip, restrainingBar, false);
    }

    /** Such as {@code carrier 1, six place chair (off line)}. */
    @Override
    public String toString() {
        return "carrier " + number + ", " + kind.value() + (inService ? "" : " (off line)");
    }
}
