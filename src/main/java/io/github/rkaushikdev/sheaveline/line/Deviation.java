package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.util.Decimals;

/**
 * The slopes the rope actually runs at, which are not the chord slope.
 *
 * <p>The sag tips the rope up at both ends of every span, so it arrives at the upper support steeper
 * than the chord and leaves the lower support flatter. On a long span that correction is bigger than
 * the deviation designed into the tower.
 */
public final class Deviation {

    private Deviation() {}

    /**
     * How far the sag tips the rope up at either end, four d over S. A span cannot be built with no
     * length, so there is nothing to guard here.
     *
     * @param sag how the rope hangs
     * @param span the span
     * @return the tip at each end
     */
    public static Angle sagSlope(SagModel sag, Span span) {
        Length horizontal = span.horizontal();
        return Angle.ofPercentGrade(Decimals.multiply(sag.sagOver(horizontal).ratio(horizontal), 400));
    }

    /**
     * The slope the rope is running at as it reaches the upper support.
     *
     * @param sag how the rope hangs
     * @param span the span
     * @return the chord slope plus the sag's tip
     */
    public static Angle arrivalSlope(SagModel sag, Span span) {
        return span.slope().plus(sagSlope(sag, span));
    }

    /**
     * The slope the rope is running at as it leaves the lower support.
     *
     * @param sag how the rope hangs
     * @param span the span
     * @return the chord slope less the sag's tip
     */
    public static Angle departureSlope(SagModel sag, Span span) {
        return span.slope().minus(sagSlope(sag, span));
    }
}
