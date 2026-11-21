package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A length in whole thousandths of an inch. Immutable, exact, and comparable.
 *
 * <p>Everything on a ropeway drawing is a length and the drawings disagree about the unit. Span
 * lengths are feet, tower heights are feet and inches, rope diameter is inches to three places and
 * rope wear is read in thousandths. One integer base unit carries all of them without a float
 * creeping in, and a mil is small enough that nothing the department measures needs a fraction of
 * one.
 *
 * @param mils the length in thousandths of an inch, signed
 */
public record Length(long mils) implements Comparable<Length> {

    /** Mils in an inch. */
    public static final long MILS_PER_INCH = 1000;

    /** Inches in a foot. */
    public static final long INCHES_PER_FOOT = 12;

    /** Mils in a foot. */
    public static final long MILS_PER_FOOT = MILS_PER_INCH * INCHES_PER_FOOT;

    /** No length at all. */
    public static final Length ZERO = new Length(0);

    private static final Pattern FEET_INCHES = Pattern.compile(
            "\\s*(?:(?<feet>-?\\d+(?:\\.\\d+)?)\\s*(?:'|ft|feet|foot)\\s*)?"
                    + "(?:(?<inches>-?\\d+(?:\\.\\d+)?)\\s*(?:\"|in|inch|inches)?\\s*)?",
            Pattern.CASE_INSENSITIVE);

    /**
     * A length in mils.
     *
     * @param mils the length
     * @return the length
     */
    public static Length ofMils(long mils) {
        return new Length(mils);
    }

    /**
     * A length in inches, rounded to the nearest mil with a half going away from zero.
     *
     * @param inches the length in inches
     * @return the length
     */
    public static Length ofInches(BigDecimal inches) {
        return new Length(Decimals.roundHalfAway(Decimals.multiply(inches, MILS_PER_INCH)));
    }

    /**
     * A length in inches, written as digits.
     *
     * @param inches the length in inches, such as {@code "1.378"}
     * @return the length
     */
    public static Length ofInches(String inches) {
        return ofInches(Decimals.of(inches));
    }

    /**
     * A length in whole inches.
     *
     * @param inches the length in inches
     * @return the length
     */
    public static Length ofInches(long inches) {
        return ofInches(Decimals.of(inches));
    }

    /**
     * A length in feet, rounded to the nearest mil.
     *
     * @param feet the length in feet
     * @return the length
     */
    public static Length ofFeet(BigDecimal feet) {
        return new Length(Decimals.roundHalfAway(Decimals.multiply(feet, MILS_PER_FOOT)));
    }

    /**
     * A length in feet, written as digits.
     *
     * @param feet the length in feet, such as {@code "148.5"}
     * @return the length
     */
    public static Length ofFeet(String feet) {
        return ofFeet(Decimals.of(feet));
    }

    /**
     * A length in whole feet.
     *
     * @param feet the length in feet
     * @return the length
     */
    public static Length ofFeet(long feet) {
        return ofFeet(Decimals.of(feet));
    }

    /**
     * Feet and inches the way a drawing writes them. The sign of the feet carries into the inches,
     * so minus four feet six inches is a length of minus fifty four inches.
     *
     * @param feet whole feet, signed
     * @param inches the inches on top, unsigned
     * @return the length
     */
    public static Length ofFeetInches(long feet, BigDecimal inches) {
        long sign = feet < 0 ? -1 : 1;
        long whole = Math.abs(feet) * MILS_PER_FOOT + Decimals.roundHalfAway(Decimals.multiply(inches, MILS_PER_INCH));
        return new Length(sign * whole);
    }

    /**
     * Feet and inches, the inches written as digits.
     *
     * @param feet whole feet, signed
     * @param inches the inches, such as {@code "6.5"}
     * @return the length
     */
    public static Length ofFeetInches(long feet, String inches) {
        return ofFeetInches(feet, Decimals.of(inches));
    }

    /**
     * Whole feet and whole inches.
     *
     * @param feet whole feet, signed
     * @param inches whole inches
     * @return the length
     */
    public static Length ofFeetInches(long feet, long inches) {
        return ofFeetInches(feet, Decimals.of(inches));
    }

    /**
     * Read a drawing string. Accepts {@code 148}, {@code 148 ft}, {@code 12'6"}, {@code 1.378 in}
     * and {@code 6 feet 3 inches}. A bare number with no unit is inches, which is how a rope
     * diameter is written and how a machinist reads an unlabelled dimension. A decimal number of
     * feet is what a survey prints, and it takes no inches after it.
     *
     * @param text the drawing string
     * @return the length
     * @throws ParseError when the text is not a length
     */
    public static Length parse(String text) {
        Matcher match = FEET_INCHES.matcher(text);
        if (!match.matches() || (match.group("feet") == null && match.group("inches") == null)) {
            throw new ParseError(text, "a length");
        }
        String feet = match.group("feet");
        String inches = match.group("inches");
        if (feet == null) {
            return ofInches(inches);
        }
        if (inches == null) {
            return ofFeet(feet);
        }
        if (feet.contains(".")) {
            throw new ParseError(text, "a length");
        }
        return ofFeetInches(Long.parseLong(feet), inches);
    }

    /**
     * The length in inches.
     *
     * @return the exact number of inches
     */
    public BigDecimal inches() {
        return Decimals.divide(mils, MILS_PER_INCH);
    }

    /**
     * The length in feet.
     *
     * @return the number of feet, to 28 digits when it is not exact
     */
    public BigDecimal feet() {
        return Decimals.divide(mils, MILS_PER_FOOT);
    }

    /**
     * Split into whole feet and the remaining inches, both signed alike.
     *
     * @return the feet and the inches
     */
    public FeetInches splitFeetInches() {
        long sign = mils < 0 ? -1 : 1;
        long magnitude = Math.abs(mils);
        BigDecimal rest = Decimals.divide(magnitude % MILS_PER_FOOT, MILS_PER_INCH);
        return new FeetInches(sign * (magnitude / MILS_PER_FOOT), sign < 0 ? rest.negate() : rest);
    }

    /**
     * Feet and inches the way a drawing prints them, such as {@code 12' 6"}.
     *
     * @return the printed length
     */
    public String formatFeetInches() {
        FeetInches split = splitFeetInches();
        return split.feet() + "' " + Decimals.trim(split.inches().abs()) + "\"";
    }

    /**
     * Inches to three places, the way a rope diameter is printed.
     *
     * @return the printed length, such as {@code 1.378"}
     */
    public String formatInches() {
        return formatInches(3);
    }

    /**
     * Inches to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed length
     */
    public String formatInches(int places) {
        return Decimals.quantize(inches(), places) + "\"";
    }

    /**
     * The sum of two lengths.
     *
     * @param other the length to add
     * @return the sum
     */
    public Length plus(Length other) {
        return new Length(mils + other.mils);
    }

    /**
     * The difference of two lengths.
     *
     * @param other the length to take away
     * @return the difference, which may be negative
     */
    public Length minus(Length other) {
        return new Length(mils - other.mils);
    }

    /**
     * The same length the other way.
     *
     * @return the negated length
     */
    public Length negated() {
        return new Length(-mils);
    }

    /**
     * The length with no sign.
     *
     * @return the magnitude
     */
    public Length abs() {
        return new Length(Math.abs(mils));
    }

    /**
     * This length times a factor, rounded to the nearest mil.
     *
     * @param factor the factor
     * @return the scaled length
     */
    public Length scaled(BigDecimal factor) {
        return new Length(Decimals.roundHalfAway(Decimals.multiply(factor, mils)));
    }

    /**
     * This length times a factor written as digits.
     *
     * @param factor the factor, such as {@code "0.3333"}
     * @return the scaled length
     */
    public Length scaled(String factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * This length times a whole number.
     *
     * @param factor the factor
     * @return the scaled length
     */
    public Length scaled(long factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * How many of the other length fit in this one.
     *
     * @param other the divisor
     * @return the dimensionless ratio
     * @throws RangeError when the other length is zero
     */
    public BigDecimal ratio(Length other) {
        if (other.mils == 0) {
            throw new RangeError("divisor length", other, "nonzero", "nonzero");
        }
        return Decimals.divide(mils, other.mils);
    }

    /**
     * Whether this is no length at all.
     *
     * @return true at zero
     */
    public boolean isZero() {
        return mils == 0;
    }

    /**
     * The expression that builds this length.
     *
     * @return such as {@code Length.ofMils(91000)}
     */
    public String repr() {
        return "Length.ofMils(" + mils + ")";
    }

    @Override
    public int compareTo(Length other) {
        return Long.compare(mils, other.mils);
    }

    /** Feet and inches, the way a drawing prints the length. */
    @Override
    public String toString() {
        return formatFeetInches();
    }

    /**
     * A length split into whole feet and the inches left over, both carrying the same sign.
     *
     * @param feet whole feet
     * @param inches the inches beyond them
     */
    public record FeetInches(long feet, BigDecimal inches) {}
}
