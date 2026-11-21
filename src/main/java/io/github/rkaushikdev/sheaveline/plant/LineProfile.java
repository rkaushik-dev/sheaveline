package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * The ground under the line, as the survey crew wrote it down.
 *
 * <p>Every clearance question needs two numbers at the same station, the rope and the ground, and
 * only one of them is on the tower schedule. The survey gives the other as a list of shots along the
 * centreline, so the ground between two shots is a straight line and anything else is guessing.
 *
 * <p>Sampling matters more than it looks. A clearance check that only reads the shot stations will
 * walk straight past a knoll that sits between two of them, so the sampler steps at a fixed interval
 * and puts the shot stations in as well.
 *
 * @param points the shots, in station order
 */
public record LineProfile(List<GroundPoint> points) {

    /** The sampling step a clearance check uses unless told otherwise. */
    public static final Length DEFAULT_STEP = Length.ofFeet(25);

    /**
     * A survey, put in station order and checked.
     *
     * @throws PlantError when there are fewer than two shots, or two share a station
     */
    public LineProfile {
        points = points.stream()
                .sorted(Comparator.comparingLong(point -> point.station().mils()))
                .toList();
        if (points.size() < 2) {
            throw new PlantError("a profile needs at least two survey shots");
        }
        for (int at = 1; at < points.size(); at++) {
            Length station = points.get(at).station();
            if (station.equals(points.get(at - 1).station())) {
                throw new PlantError("two shots share station " + Decimals.fixed(station.feet(), 0));
            }
        }
    }

    /**
     * A survey from its table of shots, each a station and an elevation in whole feet.
     *
     * @param shots pairs of {@code {station, elevation}}
     * @return the profile
     */
    public static LineProfile fromSurvey(long[][] shots) {
        return new LineProfile(Arrays.stream(shots)
                .map(shot -> GroundPoint.atFeet(shot[0], shot[1]))
                .toList());
    }

    /**
     * Where the survey starts.
     *
     * @return the first shot's station
     */
    public Length firstStation() {
        return points.get(0).station();
    }

    /**
     * Where the survey ends.
     *
     * @return the last shot's station
     */
    public Length lastStation() {
        return points.get(points.size() - 1).station();
    }

    /**
     * Whether the survey reaches a station.
     *
     * @param station the station
     * @return true from the first shot to the last
     */
    public boolean covers(Length station) {
        return firstStation().compareTo(station) <= 0 && station.compareTo(lastStation()) <= 0;
    }

    /**
     * The ground at a station: a straight line between the two shots either side of it.
     *
     * @param station the station
     * @return the ground elevation
     * @throws PlantError when the station is off the survey
     */
    public Length elevationAt(Length station) {
        if (!covers(station)) {
            throw new PlantError("station " + Decimals.fixed(station.feet(), 0) + " is off the survey");
        }
        for (int at = 1; at < points.size(); at++) {
            GroundPoint lower = points.get(at - 1);
            GroundPoint upper = points.get(at);
            if (lower.station().compareTo(station) <= 0 && station.compareTo(upper.station()) <= 0) {
                Length run = upper.station().minus(lower.station());
                BigDecimal along = station.minus(lower.station()).ratio(run);
                return lower.elevation()
                        .plus(upper.elevation().minus(lower.elevation()).scaled(along));
            }
        }
        throw new PlantError("station " + Decimals.fixed(station.feet(), 0) + " fell between no two shots");
    }

    /**
     * The highest shot, the first of them on a tie.
     *
     * @return that shot
     */
    public GroundPoint highest() {
        GroundPoint best = points.get(0);
        for (GroundPoint point : points) {
            if (point.elevation().mils() > best.elevation().mils()) {
                best = point;
            }
        }
        return best;
    }

    /**
     * The lowest shot, the first of them on a tie.
     *
     * @return that shot
     */
    public GroundPoint lowest() {
        GroundPoint best = points.get(0);
        for (GroundPoint point : points) {
            if (point.elevation().mils() < best.elevation().mils()) {
                best = point;
            }
        }
        return best;
    }

    /**
     * Every station to test inside a span at the default step.
     *
     * @param span the span
     * @return the stations in order
     */
    public List<Length> stations(Span span) {
        return stations(span, DEFAULT_STEP);
    }

    /**
     * Every station to test inside a span: both ends, every step, and the survey shots inside it so
     * a knoll is not missed.
     *
     * @param span the span
     * @param step the sampling step
     * @return the stations in order, each once
     * @throws PlantError when the step is not positive
     */
    public List<Length> stations(Span span, Length step) {
        if (step.mils() <= 0) {
            throw new PlantError("a sampling step has to be positive");
        }
        Length start = span.lower().station();
        Length end = span.upper().station();
        TreeSet<Long> marks = new TreeSet<>(List.of(start.mils(), end.mils()));
        for (Length walk = start; walk.compareTo(end) < 0; walk = walk.plus(step)) {
            marks.add(walk.mils());
        }
        points.stream()
                .map(GroundPoint::station)
                .filter(station -> start.compareTo(station) <= 0 && station.compareTo(end) <= 0)
                .forEach(station -> marks.add(station.mils()));
        return marks.stream().map(Length::new).toList();
    }

    /**
     * The clearance at one station of a span.
     *
     * @param span the span
     * @param station the station
     * @return the rope and the ground there
     */
    public Clearance clearanceAt(Span span, Length station) {
        return new Clearance(station, span.chordElevationAt(station), elevationAt(station));
    }

    /**
     * Every sampled clearance in a span at the default step.
     *
     * @param span the span
     * @return the clearances in station order
     */
    public List<Clearance> clearances(Span span) {
        return clearances(span, DEFAULT_STEP);
    }

    /**
     * Every sampled clearance in a span.
     *
     * @param span the span
     * @param step the sampling step
     * @return the clearances in station order
     */
    public List<Clearance> clearances(Span span, Length step) {
        return stations(span, step).stream()
                .map(station -> clearanceAt(span, station))
                .toList();
    }

    /**
     * The worst clearance in a span at the default step.
     *
     * @param span the span
     * @return the lowest clearance
     */
    public Clearance tightest(Span span) {
        return tightest(span, DEFAULT_STEP);
    }

    /**
     * The worst clearance in a span, which is the only one anyone acts on. The first of them on a
     * tie.
     *
     * @param span the span
     * @param step the sampling step
     * @return the lowest clearance
     */
    public Clearance tightest(Span span, Length step) {
        Clearance worst = null;
        for (Clearance clearance : clearances(span, step)) {
            if (worst == null || clearance.height().mils() < worst.height().mils()) {
                worst = clearance;
            }
        }
        return worst;
    }
}
