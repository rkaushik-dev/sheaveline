package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.List;
import java.util.Objects;

/**
 * One caliper reading, taken twice at right angles.
 *
 * @param station where along the rope, counted from the splice
 * @param acrossCrowns the caliper across the strand crowns
 * @param acrossValleys the caliper turned a quarter round
 */
public record DiameterReading(Length station, Length acrossCrowns, Length acrossValleys) {

    /**
     * A reading with real numbers in it.
     *
     * @throws RopeworkError when the station is behind the splice or either diameter is not positive
     */
    public DiameterReading {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(acrossCrowns, "acrossCrowns");
        Objects.requireNonNull(acrossValleys, "acrossValleys");
        if (station.mils() < 0) {
            throw new RopeworkError("a reading station is measured from the splice, forwards");
        }
        for (Length value : List.of(acrossCrowns, acrossValleys)) {
            if (value.mils() <= 0) {
                throw new RopeworkError("a caliper reading is a positive diameter");
            }
        }
    }

    /**
     * The mean of the two axes, an odd mil going down.
     *
     * @return the mean diameter
     */
    public Length mean() {
        return new Length(Math.floorDiv(acrossCrowns.mils() + acrossValleys.mils(), 2));
    }

    /**
     * How far from round the rope has gone at this station.
     *
     * @return the gap between the two axes
     */
    public Length ovality() {
        return acrossCrowns.minus(acrossValleys).abs();
    }

    /** As the sheet reads, such as {@code 1.352" at 1200 ft}. */
    @Override
    public String toString() {
        return mean().formatInches(3) + " at " + Decimals.fixed(station.feet(), 0) + " ft";
    }
}
