package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;

/**
 * One carrier, where it stopped and how far it is off the ground.
 *
 * @param number the number painted on the carrier
 * @param side which side of the line it hangs on
 * @param span the span it stopped in
 * @param station the station under it
 * @param ropeElevation where the rope is above that station
 * @param groundElevation where the ground is, snow included
 * @param seatHeight where the seat is, which is the rope less the hanger
 */
public record CarrierStop(
        int number,
        Side side,
        Span span,
        Length station,
        Length ropeElevation,
        Length groundElevation,
        Length seatHeight) {

    /** A stopped carrier. */
    public CarrierStop {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(ropeElevation, "ropeElevation");
        Objects.requireNonNull(groundElevation, "groundElevation");
        Objects.requireNonNull(seatHeight, "seatHeight");
    }

    /**
     * How far the seat is off the ground, which is how far the people in it have to come down.
     *
     * @return the seat height less the ground elevation
     */
    public Length height() {
        return seatHeight.minus(groundElevation);
    }

    /**
     * Whether the seat is higher off the ground than a limit.
     *
     * @param limit the height
     * @return true strictly above it
     */
    public boolean isAbove(Length limit) {
        return height().compareTo(limit) > 0;
    }

    /** Such as {@code carrier 9 up in span 2, 65 ft up}. */
    @Override
    public String toString() {
        return "carrier " + number + " " + side.value() + " in span " + span.index() + ", "
                + Decimals.fixed(height().feet(), 0) + " ft up";
    }
}
