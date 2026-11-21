package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;

/**
 * One station, what is there and what is wanted.
 *
 * @param station where along the line
 * @param carrierBottom the elevation of the bottom of the carrier
 * @param ground the ground elevation
 * @param snow the snow allowance standing on the ground
 * @param required the clearance the ground use wants
 * @param use what the ground is used for
 */
public record ClearanceReading(
        Length station, Length carrierBottom, Length ground, Length snow, Length required, GroundUse use) {

    /** A reading, nothing left null. */
    public ClearanceReading {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(carrierBottom, "carrierBottom");
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(snow, "snow");
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(use, "use");
    }

    /**
     * The daylight under the carrier once the snow is on the ground.
     *
     * @return the carrier bottom less the ground and the snow
     */
    public Length available() {
        return carrierBottom.minus(ground.plus(snow));
    }

    /**
     * What is there less what is wanted.
     *
     * @return the margin, negative when the station fails
     */
    public Length margin() {
        return available().minus(required);
    }

    /**
     * Whether the station has the clearance its ground use wants.
     *
     * @return true with no margin short
     */
    public boolean passes() {
        return margin().mils() >= 0;
    }

    /** Both sides of the comparison, such as {@code 23.0 ft of 12 ft wanted at station 0}. */
    @Override
    public String toString() {
        return Decimals.fixed(available().feet(), 1) + " ft of " + Decimals.fixed(required.feet(), 0)
                + " ft wanted at station " + Decimals.fixed(station.feet(), 0);
    }
}
