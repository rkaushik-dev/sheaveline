package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.models.FilingCounterKey;
import io.github.rkaushikdev.sheaveline.persistence.models.FilingCounterRow;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Locale;

/**
 * Board filing numbers, handed out one at a time with no gaps.
 *
 * <p>A gap in the sequence is a question at the audit, so the number cannot come from a count of
 * the rows already there: two clerks filing at the same moment both count seven and both write
 * eight. It comes off a counter row that is read for update and written in the same transaction,
 * which is the only arrangement where the second clerk waits. The unique filing number is the belt
 * to that pair of braces.
 */
public final class Numbering {

    /** The sequence a year's first number takes. */
    public static final int FIRST_SEQUENCE = 1;

    private Numbering() {}

    /**
     * Hand out the next number and move the counter on, inside the session's transaction.
     *
     * @param session the session
     * @param state the two letter state code, in either case
     * @param year the filing year
     * @return the number
     * @throws StorageError when the state is not two letters
     */
    public static FilingNumber takeNumber(Session session, String state, int year) {
        if (state.length() != 2 || !state.chars().allMatch(Character::isLetter)) {
            throw new StorageError(state + " is not a state code");
        }
        FilingCounterRow row = counter(session, state, year, true);
        FilingNumber number = new FilingNumber(row.getState(), row.getYear(), row.getNextSequence());
        row.setNextSequence(row.getNextSequence() + 1);
        session.flush();
        return number;
    }

    /**
     * What the next number would be, without taking it.
     *
     * @param session the session
     * @param state the two letter state code, in either case
     * @param year the filing year
     * @return the number the next filing would take
     */
    public static FilingNumber peekNumber(Session session, String state, int year) {
        FilingCounterRow row = counter(session, state, year, false);
        return new FilingNumber(row.getState(), row.getYear(), row.getNextSequence());
    }

    /**
     * How many numbers a state's year has handed out.
     *
     * @param session the session
     * @param state the two letter state code, in either case
     * @param year the filing year
     * @return the count, zero for a year nothing was filed in
     */
    public static int issuedCount(Session session, String state, int year) {
        return session.find(FilingCounterRow.class, key(state, year))
                .map(row -> row.getNextSequence() - FIRST_SEQUENCE)
                .orElse(0);
    }

    /**
     * The years a state has a series in, oldest first.
     *
     * @param session the session
     * @param state the two letter state code, in either case
     * @return the years
     */
    public static List<Integer> seriesYears(Session session, String state) {
        return session.entityManager()
                .createQuery(
                        "select c.year from FilingCounterRow c where c.state = :state order by c.year", Integer.class)
                .setParameter("state", state.toUpperCase(Locale.ROOT))
                .getResultList();
    }

    /**
     * Move the counter past a number that came from somewhere else, as when a series is loaded from
     * paper. It never moves the counter backwards, because that would hand out a number twice.
     *
     * @param session the session
     * @param number the number already issued
     */
    public static void adopt(Session session, FilingNumber number) {
        FilingCounterRow row = counter(session, number.state(), number.year(), true);
        if (number.sequence() >= row.getNextSequence()) {
            row.setNextSequence(number.sequence() + 1);
            session.flush();
        }
    }

    private static FilingCounterRow counter(Session session, String state, int year, boolean lock) {
        FilingCounterKey key = key(state, year);
        FilingCounterRow row = session.entityManager()
                .find(FilingCounterRow.class, key, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (row == null) {
            row = new FilingCounterRow(key.state(), key.year());
            session.persist(row);
            session.flush();
        }
        return row;
    }

    private static FilingCounterKey key(String state, int year) {
        return new FilingCounterKey(state.toUpperCase(Locale.ROOT), year);
    }
}
