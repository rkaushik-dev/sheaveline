package io.github.rkaushikdev.sheaveline.line;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.LineProfile;
import io.github.rkaushikdev.sheaveline.plant.Span;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A walk of one span with the sag, the snow and the ground use all in hand.
 *
 * <p>Two numbers have to be worked out at the same station and they both move. What there is: the
 * chord, less the sag, less the hanger and the carrier under it, less the snow that will be standing
 * there in February. What there has to be: whatever the ground under that station is being used for.
 * So a clearance check is not one number against one limit. It is a walk along the span with both
 * sides of the comparison changing underneath it.
 *
 * @param span the span walked
 * @param ground the survey under it
 * @param sag how the rope hangs
 * @param carrierDrop how far the bottom of a carrier hangs below the rope
 * @param snow the snow allowance
 * @param uses what the ground is used for
 */
public record ClearanceCheck(
        Span span, LineProfile ground, SagModel sag, Length carrierDrop, Length snow, GroundUseMap uses) {

    /** The sampling step a walk uses unless told otherwise. */
    public static final Length SAMPLE_STEP = Length.ofFeet(25);

    /**
     * A check whose carrier hangs below the rope over ground with no negative snow on it.
     *
     * @throws LineError when the carrier drop or the snow is negative
     */
    public ClearanceCheck {
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(sag, "sag");
        Objects.requireNonNull(carrierDrop, "carrierDrop");
        Objects.requireNonNull(snow, "snow");
        Objects.requireNonNull(uses, "uses");
        if (carrierDrop.mils() < 0) {
            throw new LineError("a carrier hangs below the rope, not above it");
        }
        if (snow.mils() < 0) {
            throw new LineError("a snow allowance is not negative");
        }
    }

    /**
     * A check over open ground everywhere.
     *
     * @param span the span walked
     * @param ground the survey under it
     * @param sag how the rope hangs
     * @param carrierDrop how far the bottom of a carrier hangs below the rope
     * @param snow the snow allowance
     */
    public ClearanceCheck(Span span, LineProfile ground, SagModel sag, Length carrierDrop, Length snow) {
        this(span, ground, sag, carrierDrop, snow, new GroundUseMap());
    }

    /**
     * What is there and what is wanted at one station.
     *
     * @param station a station inside the span and the survey
     * @return the reading
     */
    public ClearanceReading readingAt(Length station) {
        Length rope = sag.ropeElevation(span, station);
        GroundUse use = uses.useAt(station);
        return new ClearanceReading(
                station, rope.minus(carrierDrop), ground.elevationAt(station), snow, use.minimum(), use);
    }

    /**
     * Every reading along the span at the usual step.
     *
     * @return the readings in station order
     */
    public List<ClearanceReading> readings() {
        return readings(SAMPLE_STEP);
    }

    /**
     * Every reading along the span: both ends, every step, and the survey shots in between.
     *
     * @param step the sampling step
     * @return the readings in station order
     */
    public List<ClearanceReading> readings(Length step) {
        return ground.stations(span, step).stream().map(this::readingAt).toList();
    }

    /**
     * The station with the least margin at the usual step.
     *
     * @return that reading
     */
    public ClearanceReading tightest() {
        return tightest(SAMPLE_STEP);
    }

    /**
     * The station with the least margin, which is not the station with the least gap. The first of
     * them on a tie.
     *
     * @param step the sampling step
     * @return that reading
     */
    public ClearanceReading tightest(Length step) {
        return least(step, Comparator.comparingLong(reading -> reading.margin().mils()));
    }

    /**
     * The station with the least daylight at the usual step.
     *
     * @return that reading
     */
    public ClearanceReading lowest() {
        return lowest(SAMPLE_STEP);
    }

    /**
     * The station with the least daylight under the carrier, the first of them on a tie.
     *
     * @param step the sampling step
     * @return that reading
     */
    public ClearanceReading lowest(Length step) {
        return least(
                step, Comparator.comparingLong(reading -> reading.available().mils()));
    }

    /**
     * The stations short of their clearance at the usual step.
     *
     * @return the failing readings in station order
     */
    public List<ClearanceReading> failures() {
        return failures(SAMPLE_STEP);
    }

    /**
     * The stations short of their clearance.
     *
     * @param step the sampling step
     * @return the failing readings in station order
     */
    public List<ClearanceReading> failures(Length step) {
        return readings(step).stream().filter(reading -> !reading.passes()).toList();
    }

    /**
     * Whether every station passes at the usual step.
     *
     * @return true with no failures
     */
    public boolean passes() {
        return passes(SAMPLE_STEP);
    }

    /**
     * Whether every station passes.
     *
     * @param step the sampling step
     * @return true with no failures
     */
    public boolean passes(Length step) {
        return failures(step).isEmpty();
    }

    /**
     * The same walk with another snow allowance.
     *
     * @param changed the snow
     * @return the new check
     */
    public ClearanceCheck withSnow(Length changed) {
        return new ClearanceCheck(span, ground, sag, carrierDrop, changed, uses);
    }

    private ClearanceReading least(Length step, Comparator<ClearanceReading> order) {
        return readings(step).stream().min(order).orElseThrow();
    }
}
