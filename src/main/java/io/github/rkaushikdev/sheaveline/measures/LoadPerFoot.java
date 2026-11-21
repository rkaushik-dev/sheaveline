package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;

/**
 * A weight per foot of rope, held as whole thousandths of a pound. Never negative.
 *
 * @param millipoundsPerFoot thousandths of a pound in each foot
 */
public record LoadPerFoot(long millipoundsPerFoot) implements Comparable<LoadPerFoot> {

    /**
     * A weight per foot.
     *
     * @throws RangeError when it is negative
     */
    public LoadPerFoot {
        if (millipoundsPerFoot < 0) {
            throw new RangeError("weight per foot", millipoundsPerFoot, 0, "no limit");
        }
    }

    /**
     * A weight per foot in pounds, rounded to the nearest thousandth.
     *
     * @param value pounds per foot
     * @return the weight per foot
     */
    public static LoadPerFoot ofPoundsPerFoot(BigDecimal value) {
        return new LoadPerFoot(Decimals.roundHalfAway(Decimals.multiply(value, Load.MILLIPOUNDS_PER_POUND)));
    }

    /**
     * A weight per foot in pounds written as digits.
     *
     * @param value pounds per foot, such as {@code "3.12"}
     * @return the weight per foot
     */
    public static LoadPerFoot ofPoundsPerFoot(String value) {
        return ofPoundsPerFoot(Decimals.of(value));
    }

    /**
     * A weight per foot in whole pounds.
     *
     * @param value pounds per foot
     * @return the weight per foot
     */
    public static LoadPerFoot ofPoundsPerFoot(long value) {
        return ofPoundsPerFoot(Decimals.of(value));
    }

    /**
     * The weight in pounds per foot.
     *
     * @return the exact number of pounds
     */
    public BigDecimal poundsPerFoot() {
        return Decimals.divide(millipoundsPerFoot, Load.MILLIPOUNDS_PER_POUND);
    }

    /**
     * What a run of rope that long weighs.
     *
     * @param distance the run
     * @return its weight, to the nearest pound
     */
    public Load times(Length distance) {
        BigDecimal pounds = Decimals.divide(
                Decimals.multiply(Decimals.of(millipoundsPerFoot), distance.mils()),
                Load.MILLIPOUNDS_PER_POUND * Length.MILS_PER_FOOT);
        return new Load(Decimals.roundHalfAway(pounds));
    }

    /**
     * The expression that builds this weight per foot.
     *
     * @return such as {@code new LoadPerFoot(3120)}
     */
    public String repr() {
        return "new LoadPerFoot(" + millipoundsPerFoot + ")";
    }

    @Override
    public int compareTo(LoadPerFoot other) {
        return Long.compare(millipoundsPerFoot, other.millipoundsPerFoot);
    }

    /** Pounds per foot with no trailing zeros, such as {@code 3.12 lb/ft}. */
    @Override
    public String toString() {
        return Decimals.trim(poundsPerFoot()) + " lb/ft";
    }
}
