package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.SpanLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One machine, one set of conditions, and where the carriers can stop.
 *
 * <p>An evacuation plan is not about the lift, it is about the eighty four places a chair can be
 * sitting when the drive fails. Two of those places are twelve feet over a groomed trail and somebody
 * walks people down a ladder. Two more are ninety feet over a gully and a rope team has to get to them
 * first, which is a different plan, a different kit and about forty minutes.
 *
 * <p>Where the carriers sit depends on where the rope stopped, and that is not known in advance. So
 * the planning number is the worst the machine can do: the stop offset that puts the most people in
 * the awkward places.
 *
 * @param lift the machine
 * @param sag how the rope hangs between its supports
 * @param snow the snow on the ground under the line, which brings the ground up to the seats
 */
public record EvacuationProfile(Lift lift, SagModel sag, Length snow) {

    /** How many rope stop positions across one carrier spacing the worst case is sampled at. */
    public static final int OFFSET_SAMPLES = 12;

    /**
     * A profile.
     *
     * @throws EvacuationError when the snow allowance is negative
     */
    public EvacuationProfile {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(sag, "sag");
        Objects.requireNonNull(snow, "snow");
        if (snow.mils() < 0) {
            throw new EvacuationError("a snow allowance is not negative");
        }
    }

    /**
     * A profile with no snow on the ground.
     *
     * @param lift the machine
     * @param sag how the rope hangs between its supports
     */
    public EvacuationProfile(Lift lift, SagModel sag) {
        this(lift, sag, Length.ZERO);
    }

    /**
     * The machine's spans.
     *
     * @return the spans, bottom to top
     */
    public SpanLine spans() {
        return lift.spans();
    }

    /**
     * The rope path from bottom to top.
     *
     * @return the summed chords
     */
    public Length slopeLength() {
        return lift.slopeLength();
    }

    /**
     * Both sides of the line, ignoring the few feet round the bullwheels.
     *
     * @return twice the slope length
     */
    public Length loopLength() {
        return slopeLength().scaled(2);
    }

    /**
     * The rope between one carrier and the next.
     *
     * @return the loop over the carriers on the line, rounded down to the mil
     * @throws EvacuationError when no carrier is on the line
     */
    public Length spacing() {
        int carriers = lift.carriersOnLine();
        if (carriers < 1) {
            throw new EvacuationError("a line with no carriers has nothing to evacuate");
        }
        return new Length(Math.floorDiv(loopLength().mils(), carriers));
    }

    /**
     * Turn a distance measured along the rope into a span and a station.
     *
     * @param along the distance from the bottom terminal along the chords
     * @return the span it lands in, the lower one at a support, and the station under it
     * @throws EvacuationError when the distance is negative or beyond the top terminal
     */
    public Position stationAt(Length along) {
        if (along.mils() < 0 || along.compareTo(slopeLength()) > 0) {
            throw new EvacuationError("that distance is off the line");
        }
        Length walked = Length.ZERO;
        for (Span span : spans()) {
            Length chord = span.chord();
            if (along.compareTo(walked.plus(chord)) <= 0) {
                Length into = along.minus(walked);
                BigDecimal fraction = chord.isZero() ? BigDecimal.ZERO : into.ratio(chord);
                Length station = span.lower().station().plus(span.horizontal().scaled(fraction));
                return new Position(span, station);
            }
            walked = walked.plus(chord);
        }
        SpanLine spans = spans();
        Span last = spans.span(spans.size());
        return new Position(last, last.upper().station());
    }

    /**
     * One carrier stopped at a distance along the rope.
     *
     * @param number the carrier's number
     * @param along the distance from the bottom terminal along the chords
     * @param side which side of the line it hangs on
     * @return where it is and how high, its seat hanging below the rope by the longest hanger on the
     *     line
     * @throws EvacuationError when the distance is off the line or no carrier is on it
     */
    public CarrierStop carrierAt(int number, Length along, Side side) {
        Position position = stationAt(along);
        Length rope = sag.ropeElevation(position.span(), position.station());
        Length ground = lift.ground().elevationAt(position.station()).plus(snow);
        Length hanger = lift.carriers().onLine().stream()
                .map(Carrier::hanger)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new EvacuationError("a line with no carriers has nothing to evacuate"));
        return new CarrierStop(number, side, position.span(), position.station(), rope, ground, rope.minus(hanger));
    }

    /**
     * Every carrier on the line when the rope stops at that offset.
     *
     * @param offset how far the first carrier is past the bottom terminal, inside one spacing
     * @return one stop per carrier on the line, in carrier order
     * @throws EvacuationError when the offset is negative or a whole spacing or more
     */
    public List<CarrierStop> stopsAtOffset(Length offset) {
        Length spacing = spacing();
        if (offset.mils() < 0 || offset.compareTo(spacing) >= 0) {
            throw new EvacuationError("an offset is inside one carrier spacing");
        }
        Length slope = slopeLength();
        Length loop = loopLength();
        int carriers = lift.carriersOnLine();
        List<CarrierStop> stops = new ArrayList<>(carriers);
        for (int index = 0; index < carriers; index++) {
            Length around = new Length(Math.floorMod(offset.mils() + index * spacing.mils(), loop.mils()));
            if (around.compareTo(slope) <= 0) {
                stops.add(carrierAt(index + 1, around, Side.UP));
            } else {
                stops.add(carrierAt(index + 1, loop.minus(around), Side.DOWN));
            }
        }
        return List.copyOf(stops);
    }

    /**
     * The stop that strands the most people above a height, sampled across the spacing.
     *
     * @param limit the height
     * @return the sampled offset with the most carriers above it, the first of them on a tie
     */
    public Length worstOffset(Length limit) {
        Length spacing = spacing();
        Length best = Length.ZERO;
        long most = -1;
        for (int step = 0; step < OFFSET_SAMPLES; step++) {
            Length offset = new Length(Math.floorDiv(spacing.mils() * step, OFFSET_SAMPLES));
            long count = stopsAtOffset(offset).stream()
                    .filter(stop -> stop.isAbove(limit))
                    .count();
            if (count > most) {
                most = count;
                best = offset;
            }
        }
        return best;
    }

    /**
     * The carrier furthest off the ground.
     *
     * @param offset where the rope stopped
     * @return that carrier, the lowest numbered on a tie
     * @throws EvacuationError when no carrier stopped on the line
     */
    public CarrierStop highest(Length offset) {
        List<CarrierStop> stops = stopsAtOffset(offset);
        if (stops.isEmpty()) {
            throw new EvacuationError("no carriers stopped on the line");
        }
        return Ranking.highest(
                stops, Comparator.comparingLong(stop -> stop.height().mils()));
    }

    /**
     * The carriers higher off the ground than a limit.
     *
     * @param offset where the rope stopped
     * @param limit the height
     * @return those carriers, in carrier order
     */
    public List<CarrierStop> above(Length offset, Length limit) {
        return stopsAtOffset(offset).stream()
                .filter(stop -> stop.isAbove(limit))
                .toList();
    }

    /**
     * How many carriers stopped on the line.
     *
     * @param offset where the rope stopped
     * @return the count
     */
    public int countAt(Length offset) {
        return stopsAtOffset(offset).size();
    }

    /**
     * The same machine under a different depth of snow.
     *
     * @param changed the snow allowance
     * @return the new profile
     */
    public EvacuationProfile withSnow(Length changed) {
        return new EvacuationProfile(lift, sag, changed);
    }

    /**
     * Where a distance along the rope lands.
     *
     * @param span the span it lands in
     * @param station the station under it
     */
    public record Position(Span span, Length station) {}
}
