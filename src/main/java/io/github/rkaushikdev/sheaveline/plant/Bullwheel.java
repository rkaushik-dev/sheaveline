package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The wheel the rope wraps, measured to the bottom of the groove.
 *
 * @param diameter the groove diameter
 * @param liner what the groove is lined with
 */
public record Bullwheel(Length diameter, String liner) {

    /** The wheel is at least sixty times the diameter of the rope it carries. */
    public static final int MINIMUM_WRAP_RATIO = 60;

    private static final BigDecimal PI = Decimals.of(Math.PI);

    /**
     * A wheel with a size and a liner.
     *
     * @throws PlantError when it has no diameter or no liner
     */
    public Bullwheel {
        Objects.requireNonNull(diameter, "diameter");
        Objects.requireNonNull(liner, "liner");
        if (diameter.mils() <= 0) {
            throw new PlantError("a bullwheel has a diameter");
        }
        if (liner.strip().isEmpty()) {
            throw new PlantError("a bullwheel groove has a liner");
        }
    }

    /**
     * The distance round the groove.
     *
     * @return the diameter times pi, to the nearest mil
     */
    public Length circumference() {
        return diameter.scaled(PI);
    }

    /**
     * How fast the rope runs when the wheel turns at that rate.
     *
     * @param revolutionsPerMinute the wheel's speed
     * @return the rope speed
     */
    public Speed ropeSpeedAt(BigDecimal revolutionsPerMinute) {
        return new Speed(circumference().scaled(revolutionsPerMinute).mils());
    }

    /**
     * How fast the rope runs at a whole number of revolutions a minute.
     *
     * @param revolutionsPerMinute the wheel's speed
     * @return the rope speed
     */
    public Speed ropeSpeedAt(long revolutionsPerMinute) {
        return ropeSpeedAt(Decimals.of(revolutionsPerMinute));
    }

    /**
     * How fast the wheel turns for a rope speed.
     *
     * @param speed the rope speed
     * @return revolutions a minute
     */
    public BigDecimal revolutionsFor(Speed speed) {
        return Decimals.divide(speed.milsPerMinute(), circumference().mils());
    }

    /**
     * The thickest rope this wheel may carry, sixty to one on the diameter.
     *
     * @return the largest rope diameter
     */
    public Length largestRope() {
        return diameter.scaled(Decimals.divide(1, MINIMUM_WRAP_RATIO));
    }

    /**
     * Whether a rope is thin enough for this wheel.
     *
     * @param ropeDiameter the rope's diameter
     * @return true up to and including the largest rope
     */
    public boolean acceptsRope(Length ropeDiameter) {
        return ropeDiameter.compareTo(largestRope()) <= 0;
    }

    /** Such as {@code 11' 6" bullwheel, polyurethane liner}. */
    @Override
    public String toString() {
        return diameter.formatFeetInches() + " bullwheel, " + liner + " liner";
    }
}
