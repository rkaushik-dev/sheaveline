package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A number the board issues. Sequences run per state and per year with no gaps.
 *
 * @param state the two letter state code
 * @param year the filing year, {@value #FIRST_FILING_YEAR} to 2100
 * @param sequence one to 9999
 */
public record FilingNumber(String state, int year, int sequence) implements Comparable<FilingNumber> {

    /** The first year the board numbered filings. */
    public static final int FIRST_FILING_YEAR = 1970;

    private static final Pattern SHAPE =
            Pattern.compile("(?<state>[A-Z]{2})-(?<year>\\d{4})-(?<sequence>\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Comparator<FilingNumber> ORDER = Comparator.comparing(FilingNumber::state)
            .thenComparingInt(FilingNumber::year)
            .thenComparingInt(FilingNumber::sequence);

    /**
     * A filing number.
     *
     * @throws ParseError when the state is not two letters
     * @throws RangeError when the year or the sequence is out of range
     */
    public FilingNumber {
        Objects.requireNonNull(state, "state");
        if (state.length() != 2 || !state.chars().allMatch(Character::isLetter)) {
            throw new ParseError(state, "a state code");
        }
        if (year < FIRST_FILING_YEAR || year > 2100) {
            throw new RangeError("filing year", year, FIRST_FILING_YEAR, 2100);
        }
        if (sequence < 1 || sequence > 9999) {
            throw new RangeError("filing sequence", sequence, 1, 9999);
        }
    }

    /**
     * Read {@code UT-2025-0147}. The state is upper cased.
     *
     * @param text what was written
     * @return the number
     * @throws ParseError when it is not a filing number
     */
    public static FilingNumber parse(String text) {
        Matcher match = SHAPE.matcher(text.strip());
        if (!match.matches()) {
            throw new ParseError(text, "a filing number");
        }
        return new FilingNumber(
                match.group("state").toUpperCase(Locale.ROOT),
                Integer.parseInt(match.group("year")),
                Integer.parseInt(match.group("sequence")));
    }

    /**
     * The first filing of a year.
     *
     * @param state the state, in any case
     * @param year the year
     * @return number one
     */
    public static FilingNumber firstOf(String state, int year) {
        return new FilingNumber(state.toUpperCase(Locale.ROOT), year, 1);
    }

    /**
     * The number the board issues next.
     *
     * @return the same state and year, one further on
     * @throws RangeError when the year's sequence is used up
     */
    public FilingNumber nextInSequence() {
        if (sequence == 9999) {
            throw new RangeError("filing sequence", sequence + 1, 1, 9999);
        }
        return new FilingNumber(state, year, sequence + 1);
    }

    /**
     * Whether this number is the very next one after the other, which is what gapless means.
     *
     * @param earlier the number before
     * @return true when the state and year agree and the sequence is one higher
     */
    public boolean follows(FilingNumber earlier) {
        return state.equals(earlier.state) && year == earlier.year && sequence == earlier.sequence + 1;
    }

    @Override
    public int compareTo(FilingNumber other) {
        return ORDER.compare(this, other);
    }

    /** Such as {@code UT-2025-0147}, the sequence padded to four digits. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s-%d-%04d", state, year, sequence);
    }
}
