package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tightening figure in whole inch pounds. Never negative.
 *
 * <p>Two places on this hill are set with a torque wrench and they use different units on the same
 * wrench. Brake calipers are specified in pound feet on the drive drawing. Grip bolts are specified
 * in inch pounds on the grip manual, and the European manuals give newton metres. Inch pounds is
 * the smallest of the three, so nothing is lost converting between them.
 *
 * @param inchPounds the torque
 */
public record Torque(long inchPounds) implements Comparable<Torque> {

    /** Inch pounds in a pound foot. */
    public static final long INCH_POUNDS_PER_POUND_FOOT = 12;

    /** Inch pounds in a newton metre. */
    public static final BigDecimal INCH_POUNDS_PER_NEWTON_METRE = new BigDecimal("8.85074579");

    private static final Pattern TORQUE = Pattern.compile(
            "\\s*(?<value>-?\\d+(?:\\.\\d+)?)\\s*"
                    + "(?<unit>in\\s*lb|inch\\s*pounds?|lb\\s*ft|ft\\s*lb|pound\\s*feet|nm|newton\\s*metres?)\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * A torque.
     *
     * @throws RangeError when it is negative
     */
    public Torque {
        if (inchPounds < 0) {
            throw new RangeError("torque", inchPounds, 0, "no limit");
        }
    }

    /**
     * A torque in inch pounds, rounded to the nearest one.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofInchPounds(BigDecimal value) {
        return new Torque(Decimals.roundHalfAway(value));
    }

    /**
     * A torque in inch pounds written as digits.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofInchPounds(String value) {
        return ofInchPounds(Decimals.of(value));
    }

    /**
     * A torque in whole inch pounds.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofInchPounds(long value) {
        return new Torque(value);
    }

    /**
     * A torque in pound feet.
     *
     * @param value the torque
     * @return the torque, to the nearest inch pound
     */
    public static Torque ofPoundFeet(BigDecimal value) {
        return new Torque(Decimals.roundHalfAway(Decimals.multiply(value, INCH_POUNDS_PER_POUND_FOOT)));
    }

    /**
     * A torque in pound feet written as digits.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofPoundFeet(String value) {
        return ofPoundFeet(Decimals.of(value));
    }

    /**
     * A torque in whole pound feet.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofPoundFeet(long value) {
        return ofPoundFeet(Decimals.of(value));
    }

    /**
     * A torque in newton metres.
     *
     * @param value the torque
     * @return the torque, to the nearest inch pound
     */
    public static Torque ofNewtonMetres(BigDecimal value) {
        return new Torque(Decimals.roundHalfAway(Decimals.multiply(value, INCH_POUNDS_PER_NEWTON_METRE)));
    }

    /**
     * A torque in newton metres written as digits.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofNewtonMetres(String value) {
        return ofNewtonMetres(Decimals.of(value));
    }

    /**
     * A torque in whole newton metres.
     *
     * @param value the torque
     * @return the torque
     */
    public static Torque ofNewtonMetres(long value) {
        return ofNewtonMetres(Decimals.of(value));
    }

    /**
     * Read {@code 450 in lb}, {@code 900 lb ft}, {@code 900 pound feet} or {@code 100 Nm}. A unit
     * is required.
     *
     * @param text what was written
     * @return the torque
     * @throws ParseError when the text is not a torque
     */
    public static Torque parse(String text) {
        Matcher match = TORQUE.matcher(text);
        if (!match.matches()) {
            throw new ParseError(text, "a torque");
        }
        String unit = match.group("unit").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String value = match.group("value");
        if (unit.startsWith("lbft") || unit.startsWith("ftlb") || unit.startsWith("poundfeet")) {
            return ofPoundFeet(value);
        }
        if (unit.startsWith("nm") || unit.startsWith("newton")) {
            return ofNewtonMetres(value);
        }
        return ofInchPounds(value);
    }

    /**
     * The torque in pound feet.
     *
     * @return the number of pound feet
     */
    public BigDecimal poundFeet() {
        return Decimals.divide(inchPounds, INCH_POUNDS_PER_POUND_FOOT);
    }

    /**
     * The torque in newton metres.
     *
     * @return the number of newton metres, to 28 digits
     */
    public BigDecimal newtonMetres() {
        return Decimals.divide(Decimals.of(inchPounds), INCH_POUNDS_PER_NEWTON_METRE);
    }

    /**
     * Whether this reading sits inside a band round the figure it was set to.
     *
     * @param target the figure the bolt was set to
     * @param fraction the half width of the band as a fraction of the target, such as {@code "0.10"}
     * @return true when the gap is no wider than the band
     */
    public boolean within(Torque target, BigDecimal fraction) {
        Torque margin = target.scaled(fraction);
        return Math.abs(inchPounds - target.inchPounds) <= margin.inchPounds;
    }

    /**
     * Whether this reading sits inside a band written as digits.
     *
     * @param target the figure the bolt was set to
     * @param fraction the half width of the band, such as {@code "0.10"}
     * @return true when the gap is no wider than the band
     */
    public boolean within(Torque target, String fraction) {
        return within(target, Decimals.of(fraction));
    }

    /**
     * This torque times a factor, rounded to the nearest inch pound.
     *
     * @param factor the factor
     * @return the scaled torque
     */
    public Torque scaled(BigDecimal factor) {
        return new Torque(Decimals.roundHalfAway(Decimals.multiply(factor, inchPounds)));
    }

    /**
     * This torque times a factor written as digits.
     *
     * @param factor the factor, such as {@code "0.5"}
     * @return the scaled torque
     */
    public Torque scaled(String factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * The gap between two torques, whichever is the larger.
     *
     * @param other the other torque
     * @return the unsigned gap
     */
    public Torque minus(Torque other) {
        return new Torque(Math.abs(inchPounds - other.inchPounds));
    }

    /**
     * Whole pound feet.
     *
     * @return such as {@code 900 lb ft}
     */
    public String formatPoundFeet() {
        return formatPoundFeet(0);
    }

    /**
     * Pound feet to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed torque
     */
    public String formatPoundFeet(int places) {
        return Decimals.quantize(poundFeet(), places) + " lb ft";
    }

    /**
     * Inch pounds.
     *
     * @return such as {@code 450 in lb}
     */
    public String formatInchPounds() {
        return inchPounds + " in lb";
    }

    /**
     * Whether the bolt carries no torque at all.
     *
     * @return true at zero
     */
    public boolean isSlack() {
        return inchPounds == 0;
    }

    /**
     * The expression that builds this torque.
     *
     * @return such as {@code new Torque(450)}
     */
    public String repr() {
        return "new Torque(" + inchPounds + ")";
    }

    @Override
    public int compareTo(Torque other) {
        return Long.compare(inchPounds, other.inchPounds);
    }

    /** Inch pounds. */
    @Override
    public String toString() {
        return formatInchPounds();
    }
}
