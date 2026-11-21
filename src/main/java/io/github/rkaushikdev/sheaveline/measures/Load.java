package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A weight or a line pull in whole pounds.
 *
 * <p>Everything the line pulls against is written in pounds. Rope tension and counterweight are
 * tens of thousands of them, a design load is one hundred and seventy per passenger, and a haul
 * rope is quoted by its weight per foot. The manuals that came with the machinery use kilograms, so
 * that face has to read back too.
 *
 * @param pounds the load, signed
 */
public record Load(long pounds) implements Comparable<Load> {

    /** Pounds in a kilogram. */
    public static final BigDecimal POUNDS_PER_KILOGRAM = new BigDecimal("2.20462262184877581");

    /** Pounds in a kip. */
    public static final long POUNDS_PER_KIP = 1000;

    /** Pounds in a short ton. */
    public static final long POUNDS_PER_TON = 2000;

    /** Thousandths of a pound in a pound. */
    public static final long MILLIPOUNDS_PER_POUND = 1000;

    /** No load at all. */
    public static final Load ZERO = new Load(0);

    /** The design load of one passenger. */
    public static final Load PASSENGER_DESIGN_LOAD = new Load(170);

    private static final Pattern LOAD = Pattern.compile(
            "\\s*(?<value>-?[\\d,]+(?:\\.\\d+)?)\\s*"
                    + "(?<unit>lb|lbs|lbf|pound|pounds|kip|kips|kg|kilogram|kilograms|ton|tons)\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * A load in pounds, rounded to the nearest pound with a half going away from zero.
     *
     * @param pounds the load
     * @return the load
     */
    public static Load ofPounds(BigDecimal pounds) {
        return new Load(Decimals.roundHalfAway(pounds));
    }

    /**
     * A load in pounds written as digits.
     *
     * @param pounds the load, such as {@code "34500"}
     * @return the load
     */
    public static Load ofPounds(String pounds) {
        return ofPounds(Decimals.of(pounds));
    }

    /**
     * A load in whole pounds.
     *
     * @param pounds the load
     * @return the load
     */
    public static Load ofPounds(long pounds) {
        return new Load(pounds);
    }

    /**
     * A load in kips.
     *
     * @param kips the load
     * @return the load, to the nearest pound
     */
    public static Load ofKips(BigDecimal kips) {
        return new Load(Decimals.roundHalfAway(Decimals.multiply(kips, POUNDS_PER_KIP)));
    }

    /**
     * A load in kips written as digits.
     *
     * @param kips the load, such as {@code "34.5"}
     * @return the load
     */
    public static Load ofKips(String kips) {
        return ofKips(Decimals.of(kips));
    }

    /**
     * A load in whole kips.
     *
     * @param kips the load
     * @return the load
     */
    public static Load ofKips(long kips) {
        return ofKips(Decimals.of(kips));
    }

    /**
     * A load in kilograms.
     *
     * @param kilograms the load
     * @return the load, to the nearest pound
     */
    public static Load ofKilograms(BigDecimal kilograms) {
        return new Load(Decimals.roundHalfAway(Decimals.multiply(kilograms, POUNDS_PER_KILOGRAM)));
    }

    /**
     * A load in kilograms written as digits.
     *
     * @param kilograms the load
     * @return the load
     */
    public static Load ofKilograms(String kilograms) {
        return ofKilograms(Decimals.of(kilograms));
    }

    /**
     * A load in whole kilograms.
     *
     * @param kilograms the load
     * @return the load
     */
    public static Load ofKilograms(long kilograms) {
        return ofKilograms(Decimals.of(kilograms));
    }

    /**
     * A load in short tons.
     *
     * @param tons the load
     * @return the load, to the nearest pound
     */
    public static Load ofTons(BigDecimal tons) {
        return new Load(Decimals.roundHalfAway(Decimals.multiply(tons, POUNDS_PER_TON)));
    }

    /**
     * A load in short tons written as digits.
     *
     * @param tons the load
     * @return the load
     */
    public static Load ofTons(String tons) {
        return ofTons(Decimals.of(tons));
    }

    /**
     * A load in whole short tons.
     *
     * @param tons the load
     * @return the load
     */
    public static Load ofTons(long tons) {
        return ofTons(Decimals.of(tons));
    }

    /**
     * Read the shapes a spec sheet uses: {@code 34,500 lb}, {@code 34.5 kip}, {@code 1000 kg},
     * {@code 2 tons}. A unit is required.
     *
     * @param text what was written
     * @return the load
     * @throws ParseError when the text is not a load
     */
    public static Load parse(String text) {
        Matcher match = LOAD.matcher(text);
        if (!match.matches()) {
            throw new ParseError(text, "a load");
        }
        String value = match.group("value").replace(",", "");
        String unit = match.group("unit").toLowerCase(Locale.ROOT);
        if (unit.startsWith("kip")) {
            return ofKips(value);
        }
        if (unit.startsWith("k")) {
            return ofKilograms(value);
        }
        if (unit.startsWith("ton")) {
            return ofTons(value);
        }
        return ofPounds(value);
    }

    /**
     * The load in kips.
     *
     * @return the exact number of kips
     */
    public BigDecimal kips() {
        return Decimals.divide(pounds, POUNDS_PER_KIP);
    }

    /**
     * The load in kilograms.
     *
     * @return the number of kilograms, to 28 digits
     */
    public BigDecimal kilograms() {
        return Decimals.divide(Decimals.of(pounds), POUNDS_PER_KILOGRAM);
    }

    /**
     * Spread this weight evenly over a length, the way a rope quote reads.
     *
     * @param distance the length it is spread over
     * @return the weight per foot, to the nearest thousandth of a pound
     * @throws RangeError when the length is zero
     */
    public LoadPerFoot over(Length distance) {
        if (distance.mils() == 0) {
            throw new RangeError("distance", distance, "nonzero", "nonzero");
        }
        BigDecimal perFoot =
                Decimals.divide(Decimals.multiply(Decimals.of(pounds), Length.MILS_PER_FOOT), distance.mils());
        return new LoadPerFoot(Decimals.roundHalfAway(Decimals.multiply(perFoot, MILLIPOUNDS_PER_POUND)));
    }

    /**
     * Pounds with the thousands grouped.
     *
     * @return such as {@code 34,500 lb}
     */
    public String formatPounds() {
        return String.format(Locale.ROOT, "%,d lb", pounds);
    }

    /**
     * The sum of two loads.
     *
     * @param other the load to add
     * @return the sum
     */
    public Load plus(Load other) {
        return new Load(pounds + other.pounds);
    }

    /**
     * The difference of two loads.
     *
     * @param other the load to take away
     * @return the difference
     */
    public Load minus(Load other) {
        return new Load(pounds - other.pounds);
    }

    /**
     * The same load the other way.
     *
     * @return the negated load
     */
    public Load negated() {
        return new Load(-pounds);
    }

    /**
     * This load times a factor, rounded to the nearest pound.
     *
     * @param factor the factor
     * @return the scaled load
     */
    public Load scaled(BigDecimal factor) {
        return new Load(Decimals.roundHalfAway(Decimals.multiply(factor, pounds)));
    }

    /**
     * This load times a factor written as digits.
     *
     * @param factor the factor, such as {@code "1.25"}
     * @return the scaled load
     */
    public Load scaled(String factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * This load times a whole number.
     *
     * @param factor the factor
     * @return the scaled load
     */
    public Load scaled(long factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * How many of the other load fit in this one.
     *
     * @param other the divisor
     * @return the dimensionless ratio
     * @throws RangeError when the other load is zero
     */
    public BigDecimal ratio(Load other) {
        if (other.pounds == 0) {
            throw new RangeError("divisor load", other, "nonzero", "nonzero");
        }
        return Decimals.divide(pounds, other.pounds);
    }

    /**
     * The expression that builds this load.
     *
     * @return such as {@code Load.ofPounds(0)}
     */
    public String repr() {
        return "Load.ofPounds(" + pounds + ")";
    }

    @Override
    public int compareTo(Load other) {
        return Long.compare(pounds, other.pounds);
    }

    /** Pounds with the thousands grouped. */
    @Override
    public String toString() {
        return formatPounds();
    }
}
