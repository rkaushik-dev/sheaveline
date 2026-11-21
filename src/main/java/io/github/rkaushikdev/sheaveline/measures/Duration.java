package io.github.rkaushikdev.sheaveline.measures;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An elapsed time in whole seconds. Never negative.
 *
 * <p>A ropeway is timed in three places and they want three different faces on the same quantity.
 * Ride time and evacuation time are read as minutes and seconds, hour meters and inspection
 * intervals are read as hours, and a stop is logged against the wall clock. Whole seconds carries
 * all three, and nothing the department measures is finer than a second.
 *
 * @param seconds the elapsed time
 */
public record Duration(long seconds) implements Comparable<Duration> {

    /** Seconds in a minute. */
    public static final long SECONDS_PER_MINUTE = 60;

    /** Seconds in an hour. */
    public static final long SECONDS_PER_HOUR = 3600;

    /** No time at all. */
    public static final Duration ZERO = new Duration(0);

    private static final Pattern CLOCK = Pattern.compile("\\s*(?:(?<h>\\d+):)?(?<m>\\d{1,2}):(?<s>\\d{2})\\s*");
    private static final Pattern UNIT = Pattern.compile(
            "\\s*(?<value>\\d+(?:\\.\\d+)?)\\s*"
                    + "(?<unit>s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours)\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Long> UNIT_SECONDS = Map.ofEntries(
            Map.entry("s", 1L),
            Map.entry("sec", 1L),
            Map.entry("secs", 1L),
            Map.entry("second", 1L),
            Map.entry("seconds", 1L),
            Map.entry("m", SECONDS_PER_MINUTE),
            Map.entry("min", SECONDS_PER_MINUTE),
            Map.entry("mins", SECONDS_PER_MINUTE),
            Map.entry("minute", SECONDS_PER_MINUTE),
            Map.entry("minutes", SECONDS_PER_MINUTE),
            Map.entry("h", SECONDS_PER_HOUR),
            Map.entry("hr", SECONDS_PER_HOUR),
            Map.entry("hrs", SECONDS_PER_HOUR),
            Map.entry("hour", SECONDS_PER_HOUR),
            Map.entry("hours", SECONDS_PER_HOUR));

    /**
     * An elapsed time.
     *
     * @throws RangeError when the seconds are negative
     */
    public Duration {
        if (seconds < 0) {
            throw new RangeError("duration", seconds, 0, "no limit");
        }
    }

    /**
     * An elapsed time in seconds.
     *
     * @param seconds the time
     * @return the duration
     */
    public static Duration ofSeconds(long seconds) {
        return new Duration(seconds);
    }

    /**
     * An elapsed time in minutes, which must come to whole seconds.
     *
     * @param minutes the time
     * @return the duration
     * @throws RangeError when the minutes are not a whole number of seconds
     */
    public static Duration ofMinutes(BigDecimal minutes) {
        return new Duration(whole(Decimals.multiply(minutes, SECONDS_PER_MINUTE), "minutes"));
    }

    /**
     * An elapsed time in minutes written as digits.
     *
     * @param minutes the time, such as {@code "8.5"}
     * @return the duration
     */
    public static Duration ofMinutes(String minutes) {
        return ofMinutes(Decimals.of(minutes));
    }

    /**
     * An elapsed time in whole minutes.
     *
     * @param minutes the time
     * @return the duration
     */
    public static Duration ofMinutes(long minutes) {
        return ofMinutes(Decimals.of(minutes));
    }

    /**
     * An elapsed time in hours, which must come to whole seconds.
     *
     * @param hours the time
     * @return the duration
     * @throws RangeError when the hours are not a whole number of seconds
     */
    public static Duration ofHours(BigDecimal hours) {
        return new Duration(whole(Decimals.multiply(hours, SECONDS_PER_HOUR), "hours"));
    }

    /**
     * An elapsed time in hours written as digits.
     *
     * @param hours the time, such as {@code "2.5"}
     * @return the duration
     */
    public static Duration ofHours(String hours) {
        return ofHours(Decimals.of(hours));
    }

    /**
     * An elapsed time in whole hours.
     *
     * @param hours the time
     * @return the duration
     */
    public static Duration ofHours(long hours) {
        return ofHours(Decimals.of(hours));
    }

    /**
     * Hours, minutes and seconds added together.
     *
     * @param hours the hours
     * @param minutes the minutes
     * @param seconds the seconds
     * @return the duration
     */
    public static Duration ofHms(long hours, long minutes, long seconds) {
        return new Duration(hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds);
    }

    /**
     * Read {@code 8:30}, {@code 1:08:30}, {@code 90 s}, {@code 12 min} or {@code 2.5 hours}.
     *
     * @param text what was written
     * @return the duration
     * @throws ParseError when the text is not a duration
     */
    public static Duration parse(String text) {
        Matcher clock = CLOCK.matcher(text);
        if (clock.matches()) {
            String hours = clock.group("h");
            long minutes = Long.parseLong(clock.group("m"));
            long seconds = Long.parseLong(clock.group("s"));
            if (seconds > 59 || (hours != null && minutes > 59)) {
                throw new ParseError(text, "a duration");
            }
            return ofHms(hours == null ? 0 : Long.parseLong(hours), minutes, seconds);
        }
        Matcher unit = UNIT.matcher(text);
        if (!unit.matches()) {
            throw new ParseError(text, "a duration");
        }
        long factor = UNIT_SECONDS.get(unit.group("unit").toLowerCase(Locale.ROOT));
        return new Duration(whole(Decimals.multiply(Decimals.of(unit.group("value")), factor), "duration"));
    }

    /**
     * The time in minutes.
     *
     * @return the number of minutes
     */
    public BigDecimal minutes() {
        return Decimals.divide(seconds, SECONDS_PER_MINUTE);
    }

    /**
     * The time in hours.
     *
     * @return the number of hours
     */
    public BigDecimal hours() {
        return Decimals.divide(seconds, SECONDS_PER_HOUR);
    }

    /**
     * Split into whole hours, minutes and seconds.
     *
     * @return the three parts
     */
    public Hms splitHms() {
        return new Hms(seconds / SECONDS_PER_HOUR, seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE, seconds % 60);
    }

    /**
     * Minutes and seconds, which is how a ride time is written on the sign: {@code 68:30}.
     *
     * @return the clock face
     */
    public String formatClock() {
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
    }

    /**
     * Hours, minutes and seconds: {@code 1:08:30}.
     *
     * @return the face with the hours
     */
    public String formatHms() {
        Hms split = splitHms();
        return String.format(Locale.ROOT, "%d:%02d:%02d", split.hours(), split.minutes(), split.seconds());
    }

    /**
     * The sum of two durations.
     *
     * @param other the duration to add
     * @return the sum
     */
    public Duration plus(Duration other) {
        return new Duration(seconds + other.seconds);
    }

    /**
     * The difference of two durations.
     *
     * @param other the duration to take away
     * @return the difference
     * @throws RangeError when the other is the longer, because a duration is never negative
     */
    public Duration minus(Duration other) {
        return new Duration(seconds - other.seconds);
    }

    /**
     * This duration times a factor, rounded to the nearest second with a half going up.
     *
     * @param factor the factor
     * @return the scaled duration
     */
    public Duration scaled(BigDecimal factor) {
        return new Duration(Decimals.roundHalfUpward(Decimals.multiply(factor, seconds)));
    }

    /**
     * This duration times a factor written as digits.
     *
     * @param factor the factor, such as {@code "1.5"}
     * @return the scaled duration
     */
    public Duration scaled(String factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * This duration times a whole number.
     *
     * @param factor the factor
     * @return the scaled duration
     */
    public Duration scaled(long factor) {
        return scaled(Decimals.of(factor));
    }

    /**
     * Whether no time passed.
     *
     * @return true at zero
     */
    public boolean isZero() {
        return seconds == 0;
    }

    /**
     * The expression that builds this duration.
     *
     * @return such as {@code Duration.ofSeconds(0)}
     */
    public String repr() {
        return "Duration.ofSeconds(" + seconds + ")";
    }

    @Override
    public int compareTo(Duration other) {
        return Long.compare(seconds, other.seconds);
    }

    /** Hours, minutes and seconds. */
    @Override
    public String toString() {
        return formatHms();
    }

    private static long whole(BigDecimal value, String what) {
        if (!Decimals.isWhole(value)) {
            throw new RangeError(what, value, "a whole number of seconds", "");
        }
        return value.longValueExact();
    }

    /**
     * A duration split into whole hours, minutes and seconds.
     *
     * @param hours whole hours
     * @param minutes the minutes beyond them, under sixty
     * @param seconds the seconds beyond those, under sixty
     */
    public record Hms(long hours, long minutes, long seconds) {}
}
