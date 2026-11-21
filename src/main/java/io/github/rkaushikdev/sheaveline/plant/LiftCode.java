package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.ParseError;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What the crew paints on the terminal: upper case, a letter first, two to eight characters.
 *
 * <p>The identifiers that appear on paperwork are types and not strings, because they get written on
 * a form by hand and typed in again later, and the rules they keep should be checked once.
 *
 * @param value the code
 */
public record LiftCode(String value) implements Comparable<LiftCode> {

    private static final Pattern SHAPE = Pattern.compile("[A-Z][A-Z0-9]{1,7}");

    /**
     * A lift code.
     *
     * @throws ParseError when the code is not upper case letters and digits of the right length
     */
    public LiftCode {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new ParseError(value, "a lift code");
        }
    }

    /**
     * Read a code as somebody typed it, trimmed and upper cased.
     *
     * @param text what was typed
     * @return the code
     * @throws ParseError when it is not a code
     */
    public static LiftCode parse(String text) {
        return new LiftCode(text.strip().toUpperCase(Locale.ROOT));
    }

    /**
     * A tower on this lift.
     *
     * @param number counted up the line from the drive
     * @return the tower
     */
    public TowerRef tower(int number) {
        return new TowerRef(this, number);
    }

    @Override
    public int compareTo(LiftCode other) {
        return value.compareTo(other.value);
    }

    /** The code itself. */
    @Override
    public String toString() {
        return value;
    }
}
