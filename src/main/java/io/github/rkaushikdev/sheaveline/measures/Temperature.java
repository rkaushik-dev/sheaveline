package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A thermometer reading in tenths of a degree Fahrenheit.
 *
 * <p>The summit station reports in Fahrenheit to one place and the drive manuals quote gearbox and
 * hydraulic limits in Celsius, so both faces read back. Two readings subtract to a {@link
 * TemperatureDelta}; they do not add, because adding two thermometer readings together is a mistake
 * worth refusing, so there is no method that would do it.
 *
 * @param decidegreesF the reading, signed
 */
public record Temperature(long decidegreesF) implements Comparable<Temperature> {

    /** Tenths in a degree. */
    public static final long DECIDEGREES_PER_DEGREE = 10;

    /** Absolute zero in degrees Fahrenheit. */
    public static final BigDecimal ABSOLUTE_ZERO_F = new BigDecimal("-459.67");

    /** Thirty two degrees Fahrenheit. */
    public static final Temperature FREEZING = new Temperature(320);

    private static final Pattern TEMPERATURE = Pattern.compile(
            "\\s*(?<value>-?\\d+(?:\\.\\d+)?)\\s*(?:deg\\s*)?(?<unit>f|c|fahrenheit|celsius|centigrade)?\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * A reading in degrees Fahrenheit, rounded to the nearest tenth.
     *
     * @param degrees the reading
     * @return the reading
     */
    public static Temperature ofDegreesF(BigDecimal degrees) {
        return new Temperature(Decimals.roundHalfAway(Decimals.multiply(degrees, DECIDEGREES_PER_DEGREE)));
    }

    /**
     * A reading in degrees Fahrenheit written as digits.
     *
     * @param degrees the reading, such as {@code "18.4"}
     * @return the reading
     */
    public static Temperature ofDegreesF(String degrees) {
        return ofDegreesF(Decimals.of(degrees));
    }

    /**
     * A reading in whole degrees Fahrenheit.
     *
     * @param degrees the reading
     * @return the reading
     */
    public static Temperature ofDegreesF(long degrees) {
        return ofDegreesF(Decimals.of(degrees));
    }

    /**
     * A reading in degrees Celsius.
     *
     * @param degrees the reading
     * @return the reading, to the nearest tenth Fahrenheit
     */
    public static Temperature ofDegreesC(BigDecimal degrees) {
        return ofDegreesF(Decimals.divide(Decimals.multiply(degrees, 9), 5).add(Decimals.of(32), Decimals.CONTEXT));
    }

    /**
     * A reading in degrees Celsius written as digits.
     *
     * @param degrees the reading
     * @return the reading
     */
    public static Temperature ofDegreesC(String degrees) {
        return ofDegreesC(Decimals.of(degrees));
    }

    /**
     * A reading in whole degrees Celsius.
     *
     * @param degrees the reading
     * @return the reading
     */
    public static Temperature ofDegreesC(long degrees) {
        return ofDegreesC(Decimals.of(degrees));
    }

    /**
     * Read {@code -12}, {@code -12 F}, {@code 18F} or {@code -24 degC}. A bare number is
     * Fahrenheit.
     *
     * @param text what the station sent
     * @return the reading
     * @throws ParseError when the text is not a temperature
     */
    public static Temperature parse(String text) {
        Matcher match = TEMPERATURE.matcher(text);
        if (!match.matches()) {
            throw new ParseError(text, "a temperature");
        }
        String unit = match.group("unit") == null ? "f" : match.group("unit").toLowerCase(Locale.ROOT);
        String value = match.group("value");
        if (unit.startsWith("c")) {
            return ofDegreesC(value);
        }
        return ofDegreesF(value);
    }

    /**
     * The reading in degrees Fahrenheit.
     *
     * @return the exact number of degrees
     */
    public BigDecimal degreesF() {
        return Decimals.divide(decidegreesF, DECIDEGREES_PER_DEGREE);
    }

    /**
     * The reading in degrees Celsius.
     *
     * @return the number of degrees, to 28 digits
     */
    public BigDecimal degreesC() {
        return Decimals.divide(Decimals.multiply(degreesF().subtract(Decimals.of(32)), 5), 9);
    }

    /**
     * The reading a difference higher.
     *
     * @param delta the difference
     * @return the new reading
     */
    public Temperature plus(TemperatureDelta delta) {
        return new Temperature(decidegreesF + delta.decidegreesF());
    }

    /**
     * The reading a difference lower.
     *
     * @param delta the difference
     * @return the new reading
     */
    public Temperature minus(TemperatureDelta delta) {
        return new Temperature(decidegreesF - delta.decidegreesF());
    }

    /**
     * How far this reading sits above another.
     *
     * @param other the other reading
     * @return the difference, negative when this one is colder
     */
    public TemperatureDelta minus(Temperature other) {
        return new TemperatureDelta(decidegreesF - other.decidegreesF);
    }

    /**
     * Whether the reading is at or below freezing.
     *
     * @return true at thirty two degrees and colder
     */
    public boolean isFreezing() {
        return decidegreesF <= FREEZING.decidegreesF;
    }

    /**
     * Fahrenheit to one place.
     *
     * @return such as {@code -12.4 F}
     */
    public String formatF() {
        return formatF(1);
    }

    /**
     * Fahrenheit to a number of places.
     *
     * @param places digits after the point
     * @return the printed reading
     */
    public String formatF(int places) {
        return Decimals.quantize(degreesF(), places) + " F";
    }

    /**
     * Celsius to one place.
     *
     * @return such as {@code 0.0 C}
     */
    public String formatC() {
        return formatC(1);
    }

    /**
     * Celsius to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed reading
     */
    public String formatC(int places) {
        return Decimals.quantize(degreesC(), places) + " C";
    }

    /**
     * The expression that builds this reading.
     *
     * @return such as {@code new Temperature(180)}
     */
    public String repr() {
        return "new Temperature(" + decidegreesF + ")";
    }

    @Override
    public int compareTo(Temperature other) {
        return Long.compare(decidegreesF, other.decidegreesF);
    }

    /** Fahrenheit to one place. */
    @Override
    public String toString() {
        return formatF();
    }
}
