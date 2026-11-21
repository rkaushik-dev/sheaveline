package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * One piece of rope, from the lower support to the upper one.
 *
 * <p>Almost every question about a lift is really a question about one span: where the rope is
 * highest off the ground, how far apart the carriers are when the line stops, which piece a rescue
 * crew would have to work under.
 *
 * @param index the span's number, from one at the bottom
 * @param lower the lower support
 * @param upper the upper support
 */
public record Span(int index, Support lower, Support upper) {

    /**
     * A span that runs up the hill.
     *
     * @throws PlantError when the upper support is not further along than the lower, or the index
     *     is under one
     */
    public Span {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        if (upper.station().compareTo(lower.station()) <= 0) {
            throw new PlantError("span " + index + " does not run up the hill");
        }
        if (index < 1) {
            throw new PlantError("spans are numbered from one");
        }
    }

    /**
     * What the span is called: its two supports.
     *
     * @return such as {@code bottom terminal to JP-T1}
     */
    public String label() {
        return lower.label() + " to " + upper.label();
    }

    /**
     * Distance along the ground, which is what the station numbers give.
     *
     * @return the horizontal length
     */
    public Length horizontal() {
        return upper.station().minus(lower.station());
    }

    /**
     * How far the rope climbs. Signed: a span that drops has a negative rise.
     *
     * @return the rise
     */
    public Length rise() {
        return upper.ropeElevation().minus(lower.ropeElevation());
    }

    /**
     * The straight line between the two supports, ignoring the sag.
     *
     * @return the chord, to the nearest mil with a half going to the even mil
     */
    public Length chord() {
        BigDecimal run = Decimals.of(horizontal().mils());
        BigDecimal climb = Decimals.of(rise().mils());
        BigDecimal squared = run.multiply(run).add(climb.multiply(climb), Decimals.CONTEXT);
        return new Length(squared.sqrt(Decimals.CONTEXT)
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValueExact());
    }

    /**
     * The slope of the chord.
     *
     * @return the angle off the rise and the run
     */
    public Angle slope() {
        return Angle.ofRiseRun(rise(), horizontal());
    }

    /**
     * Whether the rope drops over this span.
     *
     * @return true for a negative rise
     */
    public boolean isDownhill() {
        return rise().mils() < 0;
    }

    /**
     * The station halfway along.
     *
     * @return the midpoint station
     */
    public Length midpointStation() {
        return lower.station().plus(horizontal().scaled("0.5"));
    }

    /**
     * Where the straight chord sits above a station inside the span.
     *
     * @param station a station between the two supports
     * @return the chord elevation there
     * @throws PlantError when the station is outside the span
     */
    public Length chordElevationAt(Length station) {
        if (station.compareTo(lower.station()) < 0 || station.compareTo(upper.station()) > 0) {
            throw new PlantError("station " + Decimals.fixed(station.feet(), 0) + " is outside span " + index);
        }
        BigDecimal along = station.minus(lower.station()).ratio(horizontal());
        return lower.ropeElevation().plus(rise().scaled(along));
    }

    /** Such as {@code span 1, bottom terminal to top terminal, 4800 ft}. */
    @Override
    public String toString() {
        return "span " + index + ", " + label() + ", "
                + Decimals.fixed(horizontal().feet(), 0) + " ft";
    }
}
