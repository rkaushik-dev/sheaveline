package io.github.rkaushikdev.sheaveline.schedule;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A wall clock time in whole minutes since midnight.
 *
 * <p>Three times matter on a lift and the sign at the bottom only prints two of them: first load,
 * last load, and sweep. A window that runs past midnight is not a thing here, so a time of day never
 * wraps.
 *
 * @param minutes minutes since midnight, under {@value #MINUTES_PER_DAY}
 */
public record TimeOfDay(int minutes) implements Comparable<TimeOfDay> {

    /** Minutes in a day. */
    public static final int MINUTES_PER_DAY = 24 * 60;

    private static final Pattern CLOCK = Pattern.compile(
            "\\s*(?<hour>\\d{1,2}):(?<minute>\\d{2})\\s*(?<meridiem>am|pm)?\\s*", Pattern.CASE_INSENSITIVE);

    /**
     * A time of day.
     *
     * @throws RangeError outside midnight to one minute before the next
     */
    public TimeOfDay {
        if (minutes < 0 || minutes >= MINUTES_PER_DAY) {
            throw new RangeError("time of day", minutes, 0, MINUTES_PER_DAY - 1);
        }
    }

    /**
     * A time on the hour.
     *
     * @param hour the hour, nought to twenty three
     * @return the time
     */
    public static TimeOfDay at(int hour) {
        return at(hour, 0);
    }

    /**
     * An hour and a minute.
     *
     * @param hour the hour, nought to twenty three
     * @param minute the minute
     * @return the time
     */
    public static TimeOfDay at(int hour, int minute) {
        return new TimeOfDay(hour * 60 + minute);
    }

    /**
     * The minute a clock time falls in, its seconds dropped.
     *
     * @param value the clock time
     * @return the time of day
     */
    public static TimeOfDay of(LocalTime value) {
        return new TimeOfDay(value.getHour() * 60 + value.getMinute());
    }

    /**
     * Read {@code 8:30}, {@code 08:30} or {@code 4:15 pm}.
     *
     * @param text what was written
     * @return the time
     * @throws ParseError when it is not a time of day
     */
    public static TimeOfDay parse(String text) {
        Matcher match = CLOCK.matcher(text);
        if (!match.matches()) {
            throw new ParseError(text, "a time of day");
        }
        int hour = Integer.parseInt(match.group("hour"));
        int minute = Integer.parseInt(match.group("minute"));
        String meridiem = match.group("meridiem");
        if (meridiem != null) {
            if (hour < 1 || hour > 12) {
                throw new ParseError(text, "a time of day");
            }
            hour = hour % 12 + (meridiem.toLowerCase(Locale.ROOT).equals("pm") ? 12 : 0);
        }
        if (hour > 23 || minute > 59) {
            throw new ParseError(text, "a time of day");
        }
        return new TimeOfDay(hour * 60 + minute);
    }

    /**
     * The hour.
     *
     * @return nought to twenty three
     */
    public int hour() {
        return minutes / 60;
    }

    /**
     * The minute past the hour.
     *
     * @return nought to fifty nine
     */
    public int minute() {
        return minutes % 60;
    }

    /**
     * The same time as a clock time.
     *
     * @return the local time, on the minute
     */
    public LocalTime toLocalTime() {
        return LocalTime.of(hour(), minute());
    }

    /**
     * Later the same day, by the whole minutes of the elapsed time.
     *
     * @param elapsed how much later
     * @return the later time
     * @throws RangeError when it would run past midnight
     */
    public TimeOfDay plus(Duration elapsed) {
        long total = minutes + elapsed.seconds() / 60;
        if (total >= MINUTES_PER_DAY) {
            throw new RangeError("time of day", total, 0, MINUTES_PER_DAY - 1);
        }
        return new TimeOfDay((int) total);
    }

    /**
     * Earlier the same day, by the whole minutes of the elapsed time.
     *
     * @param elapsed how much earlier
     * @return the earlier time
     * @throws RangeError when it would run back before midnight
     */
    public TimeOfDay minus(Duration elapsed) {
        long total = minutes - elapsed.seconds() / 60;
        if (total < 0) {
            throw new RangeError("time of day", total, 0, MINUTES_PER_DAY - 1);
        }
        return new TimeOfDay((int) total);
    }

    /**
     * How long from this time until a later one.
     *
     * @param later the later time
     * @return the elapsed time
     * @throws RangeError when the other time is earlier
     */
    public Duration until(TimeOfDay later) {
        if (later.compareTo(this) < 0) {
            throw new RangeError("interval", later, this, "later");
        }
        return Duration.ofMinutes(later.minutes - minutes);
    }

    /**
     * The twenty four hour face.
     *
     * @return such as {@code 8:30} or {@code 16:15}
     */
    public String formatClock() {
        return String.format(Locale.ROOT, "%d:%02d", hour(), minute());
    }

    /**
     * The twelve hour face the sign at the bottom prints.
     *
     * @return such as {@code 4:15 pm}
     */
    public String formatMeridiem() {
        int twelve = hour() % 12 == 0 ? 12 : hour() % 12;
        return String.format(Locale.ROOT, "%d:%02d %s", twelve, minute(), hour() < 12 ? "am" : "pm");
    }

    /**
     * The expression that builds this time.
     *
     * @return such as {@code new TimeOfDay(510)}
     */
    public String repr() {
        return "new TimeOfDay(" + minutes + ")";
    }

    @Override
    public int compareTo(TimeOfDay other) {
        return Integer.compare(minutes, other.minutes);
    }

    /** The twenty four hour face. */
    @Override
    public String toString() {
        return formatClock();
    }
}
