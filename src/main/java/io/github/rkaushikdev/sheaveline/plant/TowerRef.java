package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tower, which is a lift and a number counted up the line from the drive.
 *
 * <p>Towers sort by lift and then by number rather than by text, so tower ten does not land between
 * one and two.
 *
 * @param lift the lift the tower is on
 * @param number one up to {@value #MAX_TOWER}
 */
public record TowerRef(LiftCode lift, int number) implements Comparable<TowerRef> {

    /** The highest tower number a line can have. */
    public static final int MAX_TOWER = 99;

    private static final Pattern SHAPE =
            Pattern.compile("(?<lift>[A-Z][A-Z0-9]{1,7})[ /-]?T(?<number>\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Comparator<TowerRef> ORDER =
            Comparator.comparing((TowerRef one) -> one.lift().value()).thenComparingInt(TowerRef::number);

    /**
     * A tower.
     *
     * @throws RangeError when the number is outside one to {@value #MAX_TOWER}
     */
    public TowerRef {
        Objects.requireNonNull(lift, "lift");
        if (number < 1 || number > MAX_TOWER) {
            throw new RangeError("tower number", number, 1, MAX_TOWER);
        }
    }

    /**
     * Read the shapes a crew writes: {@code JP-T7}, {@code JPT7}, {@code jp t7}, {@code JP/T7}.
     *
     * @param text what was written
     * @return the tower
     * @throws ParseError when it is not a tower reference
     * @throws RangeError when the number is outside the line
     */
    public static TowerRef parse(String text) {
        Matcher match = SHAPE.matcher(text.strip());
        if (!match.matches()) {
            throw new ParseError(text, "a tower reference");
        }
        return new TowerRef(LiftCode.parse(match.group("lift")), Integer.parseInt(match.group("number")));
    }

    /**
     * The next tower up the line.
     *
     * @return the tower numbered one higher
     */
    public TowerRef nextUp() {
        return new TowerRef(lift, number + 1);
    }

    @Override
    public int compareTo(TowerRef other) {
        return ORDER.compare(this, other);
    }

    /** Such as {@code JP-T7}. */
    @Override
    public String toString() {
        return lift + "-T" + number;
    }
}
