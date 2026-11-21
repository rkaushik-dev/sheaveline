package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One application of rope dressing, and how far the rope had run when it went on.
 *
 * @param ropeSerial the serial of the rope dressed, as written on the sheet
 * @param on the day it went on
 * @param travelAt how far the rope had run by then
 * @param product what went on the rope
 * @param by who put it on
 */
public record Dressing(String ropeSerial, LocalDate on, Length travelAt, String product, String by) {

    /** Feet in a statute mile, which is what rope travel is counted in. */
    public static final long FEET_IN_A_MILE = 5280;

    /**
     * An application that could have been made.
     *
     * @throws RopeworkError when the travel is negative or the product or the person is not named
     */
    public Dressing {
        Objects.requireNonNull(ropeSerial, "ropeSerial");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(travelAt, "travelAt");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(by, "by");
        if (travelAt.mils() < 0) {
            throw new RopeworkError("a rope cannot have run a negative distance");
        }
        if (product.strip().isEmpty()) {
            throw new RopeworkError("a dressing names what went on the rope");
        }
        if (by.strip().isEmpty()) {
            throw new RopeworkError("a dressing names who put it on");
        }
    }

    /**
     * How far the rope had run when the dressing went on.
     *
     * @return rope miles
     */
    public BigDecimal milesAt() {
        return Decimals.divide(travelAt.feet(), FEET_IN_A_MILE);
    }

    /** Such as {@code FAT-2019-04412 dressed 2025-01-15 with Fuchs Ceplattyn at 12500 miles}. */
    @Override
    public String toString() {
        return ropeSerial + " dressed " + on + " with " + product + " at " + Decimals.fixed(milesAt(), 0) + " miles";
    }
}
