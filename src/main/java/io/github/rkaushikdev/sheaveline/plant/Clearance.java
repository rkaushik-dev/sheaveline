package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;

/**
 * How much daylight there is under the rope at one station.
 *
 * @param station where along the line
 * @param ropeElevation the rope there
 * @param groundElevation the ground there
 */
public record Clearance(Length station, Length ropeElevation, Length groundElevation) {

    /** A clearance, nothing left null. */
    public Clearance {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(ropeElevation, "ropeElevation");
        Objects.requireNonNull(groundElevation, "groundElevation");
    }

    /**
     * The gap between rope and ground, negative when the ground is above the rope.
     *
     * @return the height
     */
    public Length height() {
        return ropeElevation.minus(groundElevation);
    }

    /**
     * Whether the gap is short of a minimum.
     *
     * @param minimum the clearance required
     * @return true when the height is less than it
     */
    public boolean isBelow(Length minimum) {
        return height().compareTo(minimum) < 0;
    }

    /** Such as {@code 32.0 ft at station 600}. */
    @Override
    public String toString() {
        return Decimals.fixed(height().feet(), 1) + " ft at station " + Decimals.fixed(station.feet(), 0);
    }
}
