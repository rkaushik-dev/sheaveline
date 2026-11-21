package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Where the rope actually is between two towers: a weight per foot and a horizontal tension, which
 * is all sag needs.
 *
 * <p>Nothing on the tower schedule tells you where the rope sits at midspan, because the rope is not
 * on the chord. It hangs under it by an amount that depends on what the rope and the carriers on it
 * weigh and on how hard the tension system is pulling. Every clearance figure is wrong without it,
 * and by a long way: a thousand foot span on a quad sags the better part of thirty feet.
 *
 * <p>The parabola is used rather than the true catenary. At the sag to span ratios a passenger
 * ropeway runs at, the two differ by less than the survey does, and the parabola inverts in closed
 * form, which the department needs when it asks the other question: what tension would hold the rope
 * off that knoll.
 *
 * <p>Every figure is worked in decimals to 28 significant digits and rounded to the nearest mil or
 * pound, a half going away from zero.
 *
 * @param weight what the loaded line weighs per foot
 * @param tension the horizontal tension in the rope
 */
public record SagModel(LoadPerFoot weight, Load tension) {

    /**
     * A sag model with something on the rope and something pulling it.
     *
     * @throws LineError when the weight or the tension is not positive
     */
    public SagModel {
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(tension, "tension");
        if (weight.millipoundsPerFoot() <= 0) {
            throw new LineError("a sag model needs a weight on the rope");
        }
        if (tension.pounds() <= 0) {
            throw new LineError("a sag model needs a tension in the rope");
        }
    }

    /**
     * Midspan sag below the chord, w S squared over eight H.
     *
     * @param horizontal the span's horizontal distance
     * @return the sag at midspan
     * @throws LineError when the distance is negative
     */
    public Length sagOver(Length horizontal) {
        if (horizontal.mils() < 0) {
            throw new LineError("a span has a positive horizontal distance");
        }
        BigDecimal spanFeet = horizontal.feet();
        BigDecimal load = Decimals.multiply(Decimals.multiply(weight.poundsPerFoot(), spanFeet), spanFeet);
        return Length.ofFeet(Decimals.divide(load, 8 * tension.pounds()));
    }

    /**
     * Sag below the chord at a point inside the span, on the parabola through both supports.
     *
     * @param horizontal the span's horizontal distance
     * @param along how far the point is from the lower support
     * @return the sag there
     * @throws LineError when the point is outside the span
     */
    public Length sagAt(Length horizontal, Length along) {
        if (along.compareTo(Length.ZERO) < 0 || along.compareTo(horizontal) > 0) {
            throw new LineError("that point is outside the span");
        }
        if (horizontal.mils() == 0) {
            return Length.ZERO;
        }
        Length midspan = sagOver(horizontal);
        BigDecimal x = along.feet();
        BigDecimal s = horizontal.feet();
        BigDecimal rest = s.subtract(x, Decimals.CONTEXT);
        BigDecimal shape =
                Decimals.divide(Decimals.multiply(Decimals.multiply(Decimals.of(4), x), rest), Decimals.multiply(s, s));
        return midspan.scaled(shape);
    }

    /**
     * Where the rope is, which is the chord less the sag.
     *
     * @param span the span
     * @param station a station inside it
     * @return the rope elevation there
     * @throws LineError when the station is outside the span
     */
    public Length ropeElevation(Span span, Length station) {
        Length along = station.minus(span.lower().station());
        return span.chordElevationAt(station).minus(sagAt(span.horizontal(), along));
    }

    /**
     * The station where the rope is lowest, which is not midspan on a slope. On a span steeper than
     * its own sag the low point is the lower support, and on one dropping harder than its sag it is the
     * upper one.
     *
     * @param span the span
     * @return the station of the low point
     */
    public Length lowPoint(Span span) {
        Length horizontal = span.horizontal();
        Length midspan = sagOver(horizontal);
        if (midspan.isZero()) {
            return span.rise().mils() >= 0
                    ? span.lower().station()
                    : span.upper().station();
        }
        BigDecimal offset = span.rise().ratio(midspan.scaled(4));
        BigDecimal fraction = Decimals.divide(BigDecimal.ONE.subtract(offset, Decimals.CONTEXT), 2);
        fraction = fraction.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return span.lower().station().plus(horizontal.scaled(fraction));
    }

    /**
     * What tension would hold the midspan sag down to that figure.
     *
     * @param horizontal the span's horizontal distance
     * @param wanted the midspan sag wanted
     * @return the tension, to the nearest pound
     * @throws LineError when the wanted sag is not positive
     */
    public Load tensionForSag(Length horizontal, Length wanted) {
        if (wanted.mils() <= 0) {
            throw new LineError("a wanted sag is a positive length");
        }
        BigDecimal spanFeet = horizontal.feet();
        BigDecimal load = Decimals.multiply(Decimals.multiply(weight.poundsPerFoot(), spanFeet), spanFeet);
        return Load.ofPounds(Decimals.divide(load, Decimals.multiply(Decimals.of(8), wanted.feet())));
    }

    /**
     * The same rope under another tension.
     *
     * @param changed the tension
     * @return the new model
     */
    public SagModel withTension(Load changed) {
        return new SagModel(weight, changed);
    }

    /**
     * Another weight on the line under the same tension.
     *
     * @param changed the weight per foot
     * @return the new model
     */
    public SagModel withWeight(LoadPerFoot changed) {
        return new SagModel(changed, tension);
    }

    /**
     * Sag as a fraction of the span, which is how a drawing quotes it.
     *
     * @param horizontal the span's horizontal distance
     * @return the midspan sag over the distance
     * @throws LineError when the distance is zero
     */
    public BigDecimal sagRatio(Length horizontal) {
        if (horizontal.mils() == 0) {
            throw new LineError("a sag ratio needs a span");
        }
        return sagOver(horizontal).ratio(horizontal);
    }

    /** The two numbers, such as {@code 3.12 lb/ft at 34,500 lb}. */
    @Override
    public String toString() {
        return weight + " at " + tension.formatPounds();
    }
}
