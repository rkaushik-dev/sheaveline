package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rope or carrier speed in whole mils per minute. Never negative.
 *
 * <p>The department writes rope speed two ways and has to answer in either. The drawings and the
 * board's forms use feet per minute, and the manufacturer's manuals use metres per second because
 * the machinery came from the Alps. Mils per minute is small enough that a round trip through either
 * face comes back where it started.
 *
 * <p>Wind is the same dimension and the summit station reports it in miles an hour, so that face
 * lives here too rather than in a second speed type.
 *
 * @param milsPerMinute the speed
 */
public record Speed(long milsPerMinute) implements Comparable<Speed> {

    /** Mils in a metre. */
    public static final BigDecimal MILS_PER_METRE = new BigDecimal("39370.078740157480315");

    /** Mils in a statute mile. */
    public static final long MILS_PER_MILE = 63_360_000;

    /** A rope that is not moving. */
    public static final Speed STOPPED = new Speed(0);

    private static final BigDecimal MILS_PER_MINUTE_PER_MPS =
            Decimals.multiply(MILS_PER_METRE, Duration.SECONDS_PER_MINUTE);
    private static final BigDecimal MILS_PER_MINUTE_PER_MPH = Decimals.divide(MILS_PER_MILE, 60);

    private static final Pattern SPEED = Pattern.compile(
            "\\s*(?<value>-?\\d+(?:\\.\\d+)?)\\s*"
                    + "(?<unit>fpm|ft/min|feet\\s*per\\s*minute|m/s|mps|metres?\\s*per\\s*second"
                    + "|mph|mi/h|miles\\s*per\\s*hour)\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * A speed.
     *
     * @throws RangeError when it is negative
     */
    public Speed {
        if (milsPerMinute < 0) {
            throw new RangeError("speed", milsPerMinute, 0, "no limit");
        }
    }

    /**
     * A speed in mils per minute.
     *
     * @param mils the speed
     * @return the speed
     */
    public static Speed ofMilsPerMinute(long mils) {
        return new Speed(mils);
    }

    /**
     * A speed in feet per minute, rounded to the nearest mil per minute.
     *
     * @param fpm the speed
     * @return the speed
     */
    public static Speed ofFeetPerMinute(BigDecimal fpm) {
        return new Speed(Decimals.roundHalfAway(Decimals.multiply(fpm, Length.MILS_PER_FOOT)));
    }

    /**
     * A speed in feet per minute written as digits.
     *
     * @param fpm the speed, such as {@code "500"}
     * @return the speed
     */
    public static Speed ofFeetPerMinute(String fpm) {
        return ofFeetPerMinute(Decimals.of(fpm));
    }

    /**
     * A speed in whole feet per minute.
     *
     * @param fpm the speed
     * @return the speed
     */
    public static Speed ofFeetPerMinute(long fpm) {
        return ofFeetPerMinute(Decimals.of(fpm));
    }

    /**
     * A speed in metres per second.
     *
     * @param mps the speed
     * @return the speed, to the nearest mil per minute
     */
    public static Speed ofMetresPerSecond(BigDecimal mps) {
        return new Speed(Decimals.roundHalfAway(Decimals.multiply(mps, MILS_PER_MINUTE_PER_MPS)));
    }

    /**
     * A speed in metres per second written as digits.
     *
     * @param mps the speed, such as {@code "2.5"}
     * @return the speed
     */
    public static Speed ofMetresPerSecond(String mps) {
        return ofMetresPerSecond(Decimals.of(mps));
    }

    /**
     * A speed in whole metres per second.
     *
     * @param mps the speed
     * @return the speed
     */
    public static Speed ofMetresPerSecond(long mps) {
        return ofMetresPerSecond(Decimals.of(mps));
    }

    /**
     * A speed in miles an hour, which is how the summit station reports wind.
     *
     * @param mph the speed
     * @return the speed, to the nearest mil per minute
     */
    public static Speed ofMilesPerHour(BigDecimal mph) {
        return new Speed(Decimals.roundHalfAway(Decimals.multiply(mph, MILS_PER_MINUTE_PER_MPH)));
    }

    /**
     * A speed in miles an hour written as digits.
     *
     * @param mph the speed, such as {@code "24"}
     * @return the speed
     */
    public static Speed ofMilesPerHour(String mph) {
        return ofMilesPerHour(Decimals.of(mph));
    }

    /**
     * A speed in whole miles an hour.
     *
     * @param mph the speed
     * @return the speed
     */
    public static Speed ofMilesPerHour(long mph) {
        return ofMilesPerHour(Decimals.of(mph));
    }

    /**
     * Read {@code 500 fpm}, {@code 450 ft/min}, {@code 2.5 m/s} or {@code 35 mph}. A unit is
     * required.
     *
     * @param text what was written
     * @return the speed
     * @throws ParseError when the text is not a speed
     */
    public static Speed parse(String text) {
        Matcher match = SPEED.matcher(text);
        if (!match.matches()) {
            throw new ParseError(text, "a speed");
        }
        String unit = match.group("unit").toLowerCase(Locale.ROOT);
        String value = match.group("value");
        if (unit.startsWith("m/s") || unit.startsWith("mps") || unit.startsWith("metre") || unit.startsWith("meter")) {
            return ofMetresPerSecond(value);
        }
        if (unit.startsWith("mph") || unit.startsWith("mi/h") || unit.startsWith("mile")) {
            return ofMilesPerHour(value);
        }
        return ofFeetPerMinute(value);
    }

    /**
     * The speed in feet per minute.
     *
     * @return the number of feet each minute
     */
    public BigDecimal feetPerMinute() {
        return Decimals.divide(milsPerMinute, Length.MILS_PER_FOOT);
    }

    /**
     * The speed in metres per second.
     *
     * @return the number of metres each second, to 28 digits
     */
    public BigDecimal metresPerSecond() {
        return Decimals.divide(Decimals.of(milsPerMinute), MILS_PER_MINUTE_PER_MPS);
    }

    /**
     * The speed in miles an hour.
     *
     * @return the number of miles each hour, to 28 digits
     */
    public BigDecimal milesPerHour() {
        return Decimals.divide(Decimals.of(milsPerMinute), MILS_PER_MINUTE_PER_MPH);
    }

    /**
     * How far the rope moves in the given time.
     *
     * @param elapsed the time
     * @return the distance, to the nearest mil
     */
    public Length travel(Duration elapsed) {
        BigDecimal mils = Decimals.divide(
                Decimals.multiply(Decimals.of(milsPerMinute), elapsed.seconds()), Duration.SECONDS_PER_MINUTE);
        return new Length(Decimals.roundHalfAway(mils));
    }

    /**
     * How long the rope takes to carry a carrier over that distance.
     *
     * @param distance the distance
     * @return the time, to the nearest second with a half going up
     * @throws RangeError when the rope is stopped
     */
    public Duration timeToCover(Length distance) {
        if (milsPerMinute == 0) {
            throw new RangeError("speed", 0, "greater than zero", "no limit");
        }
        BigDecimal seconds = Decimals.divide(
                Decimals.multiply(Decimals.of(distance.mils()), Duration.SECONDS_PER_MINUTE), milsPerMinute);
        return new Duration(Decimals.roundHalfUpward(seconds));
    }

    /**
     * Whole feet per minute.
     *
     * @return such as {@code 500 fpm}
     */
    public String formatFpm() {
        return formatFpm(0);
    }

    /**
     * Feet per minute to a number of places, ties going to the even digit.
     *
     * @param places digits after the point
     * @return the printed speed
     */
    public String formatFpm(int places) {
        return Decimals.quantize(feetPerMinute(), places) + " fpm";
    }

    /**
     * Metres per second to two places.
     *
     * @return such as {@code 5.00 m/s}
     */
    public String formatMps() {
        return formatMps(2);
    }

    /**
     * Metres per second to a number of places.
     *
     * @param places digits after the point
     * @return the printed speed
     */
    public String formatMps(int places) {
        return Decimals.quantize(metresPerSecond(), places) + " m/s";
    }

    /**
     * Whole miles an hour.
     *
     * @return such as {@code 35 mph}
     */
    public String formatMph() {
        return formatMph(0);
    }

    /**
     * Miles an hour to a number of places.
     *
     * @param places digits after the point
     * @return the printed speed
     */
    public String formatMph(int places) {
        return Decimals.quantize(milesPerHour(), places) + " mph";
    }

    /**
     * This speed times a factor, rounded to the nearest mil per minute.
     *
     * @param factor the factor
     * @return the scaled speed
     */
    public Speed scaled(BigDecimal factor) {
        return new Speed(Decimals.roundHalfAway(Decimals.multiply(factor, milsPerMinute)));
    }

    /**
     * This speed times a factor written as digits, which is how a reduced speed is written.
     *
     * @param factor the factor, such as {@code "0.5"}
     * @return the scaled speed
     */
    public Speed scaled(String factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * Whether the rope is stopped.
     *
     * @return true at zero
     */
    public boolean isStopped() {
        return milsPerMinute == 0;
    }

    /**
     * The expression that builds this speed.
     *
     * @return such as {@code Speed.ofMilsPerMinute(6000000)}
     */
    public String repr() {
        return "Speed.ofMilsPerMinute(" + milsPerMinute + ")";
    }

    @Override
    public int compareTo(Speed other) {
        return Long.compare(milsPerMinute, other.milsPerMinute);
    }

    /** Whole feet per minute. */
    @Override
    public String toString() {
        return formatFpm();
    }
}
