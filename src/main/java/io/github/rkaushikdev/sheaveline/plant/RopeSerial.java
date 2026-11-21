package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mill mark on a haul rope, which follows it for its whole life: the mill, the year the rope
 * was drawn, and the mill's serial.
 *
 * @param mill two to four letters
 * @param year the year drawn, 1900 to 2100
 * @param serial four to six digits
 */
public record RopeSerial(String mill, int year, String serial) implements Comparable<RopeSerial> {

    private static final Pattern SHAPE =
            Pattern.compile("(?<mill>[A-Z]{2,4})-(?<year>\\d{4})-(?<serial>\\d{4,6})", Pattern.CASE_INSENSITIVE);
    private static final Comparator<RopeSerial> ORDER = Comparator.comparing(RopeSerial::mill)
            .thenComparingInt(RopeSerial::year)
            .thenComparing(RopeSerial::serial);

    /**
     * A rope serial that checks its own parts.
     *
     * @throws ParseError when the mill or the serial is the wrong shape
     * @throws RangeError when the year is outside 1900 to 2100
     */
    public RopeSerial {
        Objects.requireNonNull(mill, "mill");
        Objects.requireNonNull(serial, "serial");
        if (mill.length() < 2 || mill.length() > 4 || !mill.chars().allMatch(Character::isLetter)) {
            throw new ParseError(mill, "a mill code");
        }
        if (year < 1900 || year > 2100) {
            throw new RangeError("rope year", year, 1900, 2100);
        }
        if (serial.length() < 4 || serial.length() > 6 || !serial.chars().allMatch(Character::isDigit)) {
            throw new ParseError(serial, "a rope serial");
        }
    }

    /**
     * Read {@code FAT-2019-04412}. The mill is upper cased.
     *
     * @param text what was written
     * @return the serial
     * @throws ParseError when it is not a rope serial
     */
    public static RopeSerial parse(String text) {
        Matcher match = SHAPE.matcher(text.strip());
        if (!match.matches()) {
            throw new ParseError(text, "a rope serial");
        }
        return new RopeSerial(
                match.group("mill").toUpperCase(Locale.ROOT),
                Integer.parseInt(match.group("year")),
                match.group("serial"));
    }

    /**
     * How old the rope is in a given year.
     *
     * @param inYear the year asked about
     * @return whole years since it was drawn
     * @throws RangeError when the year is before the rope was drawn
     */
    public int ageIn(int inYear) {
        if (inYear < year) {
            throw new RangeError("year", inYear, year, "later");
        }
        return inYear - year;
    }

    @Override
    public int compareTo(RopeSerial other) {
        return ORDER.compare(this, other);
    }

    /** Such as {@code FAT-2019-04412}. */
    @Override
    public String toString() {
        return mill + "-" + year + "-" + serial;
    }
}
