package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A signed angle in whole thousandths of a degree.
 *
 * <p>The line has a slope, written as a percent grade on the profile drawing and as degrees on the
 * tower schedule, and a sheave assembly has a deviation angle, which is how far the rope bends over
 * it and is written to two places. Both are this one quantity.
 *
 * <p>The trigonometry runs in IEEE 754 doubles through {@link StrictMath}, so a slope taken off a
 * survey reads the same on every machine.
 *
 * @param millidegrees the angle, positive uphill
 */
public record Angle(long millidegrees) implements Comparable<Angle> {

    /** Millidegrees in a degree. */
    public static final long MILLIDEGREES_PER_DEGREE = 1000;

    /** A full turn, in millidegrees. */
    public static final long FULL_CIRCLE = 360 * MILLIDEGREES_PER_DEGREE;

    /** No angle at all: a flat line. */
    public static final Angle ZERO = new Angle(0);

    static final Pattern DEGREES = Pattern.compile(
            "\\s*(?<value>-?\\d+(?:\\.\\d+)?)\\s*(?:deg|degs|degree|degrees|d)?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADE = Pattern.compile("\\s*(?<value>-?\\d+(?:\\.\\d+)?)\\s*%\\s*");

    /**
     * An angle in millidegrees.
     *
     * @param millidegrees the angle
     * @return the angle
     */
    public static Angle ofMillidegrees(long millidegrees) {
        return new Angle(millidegrees);
    }

    /**
     * An angle in degrees, rounded to the nearest millidegree with a half going away from zero.
     *
     * @param degrees the angle
     * @return the angle
     */
    public static Angle ofDegrees(BigDecimal degrees) {
        return new Angle(Decimals.roundHalfAway(Decimals.multiply(degrees, MILLIDEGREES_PER_DEGREE)));
    }

    /**
     * An angle in degrees written as digits.
     *
     * @param degrees the angle, such as {@code "2.75"}
     * @return the angle
     */
    public static Angle ofDegrees(String degrees) {
        return ofDegrees(Decimals.of(degrees));
    }

    /**
     * An angle in whole degrees.
     *
     * @param degrees the angle
     * @return the angle
     */
    public static Angle ofDegrees(long degrees) {
        return ofDegrees(Decimals.of(degrees));
    }

    /**
     * An angle in radians, taken to degrees through the shortest decimal the double prints as.
     *
     * @param radians the angle
     * @return the angle, to the nearest millidegree
     */
    public static Angle ofRadians(double radians) {
        return ofDegrees(Decimals.of(Math.toDegrees(radians)));
    }

    /**
     * A slope given as a percent grade, rise over run times one hundred.
     *
     * @param percent the grade
     * @return the angle
     */
    public static Angle ofPercentGrade(BigDecimal percent) {
        return ofRadians(StrictMath.atan(Decimals.divide(percent, 100).doubleValue()));
    }

    /**
     * A slope given as a percent grade written as digits.
     *
     * @param percent the grade, such as {@code "25"}
     * @return the angle
     */
    public static Angle ofPercentGrade(String percent) {
        return ofPercentGrade(Decimals.of(percent));
    }

    /**
     * A slope given as a whole percent grade.
     *
     * @param percent the grade
     * @return the angle
     */
    public static Angle ofPercentGrade(long percent) {
        return ofPercentGrade(Decimals.of(percent));
    }

    /**
     * The slope of a span, taken off the two numbers a survey gives.
     *
     * @param rise how far the line climbs, negative downhill
     * @param run how far it goes across
     * @return the slope
     * @throws RangeError when the run is zero
     */
    public static Angle ofRiseRun(Length rise, Length run) {
        if (run.mils() == 0) {
            throw new RangeError("run", run, "nonzero", "nonzero");
        }
        return ofRadians(StrictMath.atan2(rise.mils(), run.mils()));
    }

    /**
     * Read {@code 3}, {@code 2.75 degrees}, {@code -1.5 d}, or a grade such as {@code 25%}.
     *
     * @param text what was written
     * @return the angle
     * @throws ParseError when the text is not an angle
     */
    public static Angle parse(String text) {
        Matcher grade = GRADE.matcher(text);
        if (grade.matches()) {
            return ofPercentGrade(grade.group("value"));
        }
        Matcher degrees = DEGREES.matcher(text);
        if (!degrees.matches()) {
            throw new ParseError(text, "an angle");
        }
        return ofDegrees(degrees.group("value"));
    }

    /**
     * The angle in degrees.
     *
     * @return the exact number of degrees
     */
    public BigDecimal degrees() {
        return Decimals.divide(millidegrees, MILLIDEGREES_PER_DEGREE);
    }

    /**
     * The angle in radians.
     *
     * @return the nearest double
     */
    public double radians() {
        return Math.toRadians(degrees().doubleValue());
    }

    /**
     * Rise over run as a percentage, which is how a profile drawing reads, to three places.
     *
     * @return the grade
     */
    public BigDecimal percentGrade() {
        return Decimals.quantize(Decimals.of(StrictMath.tan(radians()) * 100), 3);
    }

    /**
     * Degrees to two places.
     *
     * @return such as {@code 2.76 deg}
     */
    public String formatDegrees() {
        return formatDegrees(2);
    }

    /**
     * Degrees to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed angle
     */
    public String formatDegrees(int places) {
        return Decimals.quantize(degrees(), places) + " deg";
    }

    /**
     * The grade to one place.
     *
     * @return such as {@code 25.0%}
     */
    public String formatGrade() {
        return formatGrade(1);
    }

    /**
     * The grade to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed grade
     */
    public String formatGrade(int places) {
        return Decimals.quantize(percentGrade(), places) + "%";
    }

    /**
     * The sum of two angles.
     *
     * @param other the angle to add
     * @return the sum
     */
    public Angle plus(Angle other) {
        return new Angle(millidegrees + other.millidegrees);
    }

    /**
     * The difference of two angles.
     *
     * @param other the angle to take away
     * @return the difference
     */
    public Angle minus(Angle other) {
        return new Angle(millidegrees - other.millidegrees);
    }

    /**
     * The same angle the other way.
     *
     * @return the negated angle
     */
    public Angle negated() {
        return new Angle(-millidegrees);
    }

    /**
     * The angle with no sign.
     *
     * @return the magnitude
     */
    public Angle abs() {
        return new Angle(Math.abs(millidegrees));
    }

    /**
     * Whether the slope climbs.
     *
     * @return true above zero
     */
    public boolean isUphill() {
        return millidegrees > 0;
    }

    /**
     * Whether there is no slope at all.
     *
     * @return true at zero
     */
    public boolean isFlat() {
        return millidegrees == 0;
    }

    /**
     * The expression that builds this angle.
     *
     * @return such as {@code Angle.ofMillidegrees(3000)}
     */
    public String repr() {
        return "Angle.ofMillidegrees(" + millidegrees + ")";
    }

    @Override
    public int compareTo(Angle other) {
        return Long.compare(millidegrees, other.millidegrees);
    }

    /** Degrees to two places. */
    @Override
    public String toString() {
        return formatDegrees();
    }
}
