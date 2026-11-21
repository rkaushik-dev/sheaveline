package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.SequencedMap;
import java.util.regex.Matcher;

/**
 * A compass bearing in whole thousandths of a degree, always inside one circle.
 *
 * <p>The weather station reports wind direction as a bearing, and the hold rules care about how
 * that bearing sits against the line.
 *
 * @param millidegrees the bearing clockwise from north, folded into one circle
 */
public record Bearing(long millidegrees) {

    /** The sixteen points a station writes down, clockwise from north, in millidegrees. */
    public static final SequencedMap<String, Long> COMPASS_POINTS = points();

    private static final List<String> POINT_NAMES = List.copyOf(COMPASS_POINTS.sequencedKeySet());

    /** A bearing, folded into one circle. */
    public Bearing {
        millidegrees = Math.floorMod(millidegrees, Angle.FULL_CIRCLE);
    }

    /**
     * A bearing in degrees, rounded to the nearest millidegree.
     *
     * @param degrees the bearing
     * @return the bearing
     */
    public static Bearing ofDegrees(BigDecimal degrees) {
        return new Bearing(Decimals.roundHalfAway(Decimals.multiply(degrees, Angle.MILLIDEGREES_PER_DEGREE)));
    }

    /**
     * A bearing in degrees written as digits.
     *
     * @param degrees the bearing, such as {@code "247.5"}
     * @return the bearing
     */
    public static Bearing ofDegrees(String degrees) {
        return ofDegrees(Decimals.of(degrees));
    }

    /**
     * A bearing in whole degrees.
     *
     * @param degrees the bearing
     * @return the bearing
     */
    public static Bearing ofDegrees(long degrees) {
        return ofDegrees(Decimals.of(degrees));
    }

    /**
     * A compass point such as {@code SW} or {@code nnw}.
     *
     * @param point the point, in any case
     * @return its bearing
     * @throws ParseError when it is not one of the sixteen
     */
    public static Bearing ofPoint(String point) {
        Long found = COMPASS_POINTS.get(point.strip().toUpperCase(Locale.ROOT));
        if (found == null) {
            throw new ParseError(point, "a compass point");
        }
        return new Bearing(found);
    }

    /**
     * Read either face: a compass point or a number of degrees.
     *
     * @param text what was written
     * @return the bearing
     * @throws ParseError when it is neither
     */
    public static Bearing parse(String text) {
        String stripped = text.strip();
        if (COMPASS_POINTS.containsKey(stripped.toUpperCase(Locale.ROOT))) {
            return ofPoint(stripped);
        }
        Matcher match = Angle.DEGREES.matcher(stripped);
        if (!match.matches()) {
            throw new ParseError(text, "a bearing");
        }
        return ofDegrees(match.group("value"));
    }

    /**
     * The bearing in degrees.
     *
     * @return the exact number of degrees
     */
    public BigDecimal degrees() {
        return Decimals.divide(millidegrees, Angle.MILLIDEGREES_PER_DEGREE);
    }

    /**
     * The bearing in radians.
     *
     * @return the nearest double
     */
    public double radians() {
        return Math.toRadians(degrees().doubleValue());
    }

    /**
     * The compass point the station would have written down.
     *
     * @return the nearest of the sixteen points
     */
    public String nearestPoint() {
        long step = Angle.FULL_CIRCLE / COMPASS_POINTS.size();
        int index = (int) (((millidegrees + step / 2) % Angle.FULL_CIRCLE) / step);
        return POINT_NAMES.get(index);
    }

    /**
     * The other way along the same line.
     *
     * @return the bearing half a turn round
     */
    public Bearing reciprocal() {
        return new Bearing(millidegrees + Angle.FULL_CIRCLE / 2);
    }

    /**
     * The shortest turn from this bearing to the other, signed clockwise.
     *
     * @param other where the turn ends
     * @return the turn, from minus to plus half a circle
     */
    public Angle difference(Bearing other) {
        long raw = Math.floorMod(other.millidegrees - millidegrees, Angle.FULL_CIRCLE);
        if (raw > Angle.FULL_CIRCLE / 2) {
            raw -= Angle.FULL_CIRCLE;
        }
        return new Angle(raw);
    }

    /**
     * How far apart two bearings are, ignoring which way round.
     *
     * @param other the other bearing
     * @return the unsigned separation
     */
    public Angle separation(Bearing other) {
        return difference(other).abs();
    }

    /**
     * Whole degrees.
     *
     * @return such as {@code 225 deg}
     */
    public String formatDegrees() {
        return formatDegrees(0);
    }

    /**
     * Degrees to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed bearing
     */
    public String formatDegrees(int places) {
        return Decimals.quantize(degrees(), places) + " deg";
    }

    /**
     * The expression that builds this bearing.
     *
     * @return such as {@code new Bearing(225000)}
     */
    public String repr() {
        return "new Bearing(" + millidegrees + ")";
    }

    /** Whole degrees and the nearest point, such as {@code 225 deg (SW)}. */
    @Override
    public String toString() {
        return formatDegrees() + " (" + nearestPoint() + ")";
    }

    private static SequencedMap<String, Long> points() {
        String[] names = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        };
        SequencedMap<String, Long> made = new LinkedHashMap<>();
        for (int at = 0; at < names.length; at++) {
            made.put(names[at], at * 22_500L);
        }
        return Collections.unmodifiableSequencedMap(made);
    }
}
