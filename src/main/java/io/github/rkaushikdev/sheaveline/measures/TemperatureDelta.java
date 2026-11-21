package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;

/**
 * A difference between two thermometer readings, in tenths of a degree Fahrenheit.
 *
 * <p>The cold weather rules add and subtract differences all day, and a difference is not a
 * reading: a span of Fahrenheit is five ninths as many Celsius, with no offset.
 *
 * @param decidegreesF the difference, signed
 */
public record TemperatureDelta(long decidegreesF) implements Comparable<TemperatureDelta> {

    /**
     * A difference in degrees Fahrenheit, rounded to the nearest tenth.
     *
     * @param degrees the difference
     * @return the difference
     */
    public static TemperatureDelta ofDegreesF(BigDecimal degrees) {
        return new TemperatureDelta(
                Decimals.roundHalfAway(Decimals.multiply(degrees, Temperature.DECIDEGREES_PER_DEGREE)));
    }

    /**
     * A difference in degrees Fahrenheit written as digits.
     *
     * @param degrees the difference, such as {@code "2.5"}
     * @return the difference
     */
    public static TemperatureDelta ofDegreesF(String degrees) {
        return ofDegreesF(Decimals.of(degrees));
    }

    /**
     * A difference in whole degrees Fahrenheit.
     *
     * @param degrees the difference
     * @return the difference
     */
    public static TemperatureDelta ofDegreesF(long degrees) {
        return ofDegreesF(Decimals.of(degrees));
    }

    /**
     * A difference in degrees Celsius, which is nine fifths as many Fahrenheit.
     *
     * @param degrees the difference
     * @return the difference, to the nearest tenth Fahrenheit
     */
    public static TemperatureDelta ofDegreesC(BigDecimal degrees) {
        return ofDegreesF(Decimals.divide(Decimals.multiply(degrees, 9), 5));
    }

    /**
     * A difference in whole degrees Celsius.
     *
     * @param degrees the difference
     * @return the difference
     */
    public static TemperatureDelta ofDegreesC(long degrees) {
        return ofDegreesC(Decimals.of(degrees));
    }

    /**
     * The difference in degrees Fahrenheit.
     *
     * @return the exact number of degrees
     */
    public BigDecimal degreesF() {
        return Decimals.divide(decidegreesF, Temperature.DECIDEGREES_PER_DEGREE);
    }

    /**
     * The difference in degrees Celsius.
     *
     * @return five ninths of the Fahrenheit figure
     */
    public BigDecimal degreesC() {
        return Decimals.divide(Decimals.multiply(degreesF(), 5), 9);
    }

    /**
     * The sum of two differences.
     *
     * @param other the difference to add
     * @return the sum
     */
    public TemperatureDelta plus(TemperatureDelta other) {
        return new TemperatureDelta(decidegreesF + other.decidegreesF);
    }

    /**
     * The same difference the other way.
     *
     * @return the negated difference
     */
    public TemperatureDelta negated() {
        return new TemperatureDelta(-decidegreesF);
    }

    /**
     * The difference with no sign.
     *
     * @return the magnitude
     */
    public TemperatureDelta abs() {
        return new TemperatureDelta(Math.abs(decidegreesF));
    }

    /**
     * The expression that builds this difference.
     *
     * @return such as {@code new TemperatureDelta(30)}
     */
    public String repr() {
        return "new TemperatureDelta(" + decidegreesF + ")";
    }

    @Override
    public int compareTo(TemperatureDelta other) {
        return Long.compare(decidegreesF, other.decidegreesF);
    }

    /** Degrees Fahrenheit with no trailing zeros, such as {@code 2.5 F}. */
    @Override
    public String toString() {
        return Decimals.trim(degreesF()) + " F";
    }
}
