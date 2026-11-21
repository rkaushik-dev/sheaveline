package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Every filing under one state and one year, in issue order.
 *
 * <p>The board's numbers run in sequence per state and per year with no gaps, and a gap is not a
 * filing mistake, it is a question at the audit about what was withdrawn and why. So the series is
 * the thing that hands out numbers, and it refuses to skip one.
 *
 * @param state the state the numbers are issued in
 * @param year the year they are issued in
 * @param filings the filings, in order of their sequence
 */
public record FilingSeries(String state, int year, List<Filing> filings) {

    /**
     * A series.
     *
     * @throws BoardError when a filing is numbered in another state or year, or two share a number
     */
    public FilingSeries {
        Objects.requireNonNull(state, "state");
        filings = List.copyOf(filings);
        String upper = state.toUpperCase(Locale.ROOT);
        for (Filing filing : filings) {
            if (!filing.number().state().equals(upper) || filing.number().year() != year) {
                throw new BoardError(filing.number() + " does not belong in " + state + " " + year);
            }
        }
        Set<Integer> numbers = new HashSet<>();
        for (Filing filing : filings) {
            if (!numbers.add(filing.number().sequence())) {
                throw new BoardError("two filings share a number");
            }
        }
    }

    /**
     * An empty series.
     *
     * @param state the state, in any case
     * @param year the year
     * @return the series, with nothing filed in it
     */
    public static FilingSeries of(String state, int year) {
        return of(state, year, List.of());
    }

    /**
     * A series with its filings put in order of their sequence.
     *
     * @param state the state, in any case
     * @param year the year
     * @param filings the filings, in any order
     * @return the series
     * @throws BoardError when a filing is numbered in another state or year, or two share a number
     */
    public static FilingSeries of(String state, int year, List<Filing> filings) {
        List<Filing> ordered = filings.stream()
                .sorted(Comparator.comparingInt(filing -> filing.number().sequence()))
                .toList();
        return new FilingSeries(state.toUpperCase(Locale.ROOT), year, ordered);
    }

    /**
     * The number the board issues next.
     *
     * @return number one in an empty series, otherwise one after the last filing
     */
    public FilingNumber nextNumber() {
        if (filings.isEmpty()) {
            return FilingNumber.firstOf(state, year);
        }
        return filings.getLast().number().nextInSequence();
    }

    /**
     * Whether the sequence runs from one with nothing skipped.
     *
     * @return true for an empty series and for one numbered one, two, three and on
     */
    public boolean isGapless() {
        int expected = 1;
        for (Filing filing : filings) {
            if (filing.number().sequence() != expected) {
                return false;
            }
            expected++;
        }
        return true;
    }

    /**
     * The sequence numbers skipped below the highest one issued.
     *
     * @return the missing numbers, lowest first
     */
    public List<Integer> missingNumbers() {
        if (filings.isEmpty()) {
            return List.of();
        }
        Set<Integer> held = new HashSet<>();
        filings.forEach(filing -> held.add(filing.number().sequence()));
        int highest = Collections.max(held);
        return IntStream.rangeClosed(1, highest)
                .filter(number -> !held.contains(number))
                .boxed()
                .toList();
    }

    /**
     * Take the next number and record a filing under it, which is the only way to get one.
     *
     * @param kind what it is about
     * @param lift the machine
     * @param filed when it went out
     * @param about what it says
     * @param filedBy who sent it
     * @return the longer series and the new filing
     * @throws BoardError when it went out in another year, says nothing or is unsigned
     */
    public Filed file(FilingKind kind, LiftCode lift, LocalDateTime filed, String about, String filedBy) {
        if (filed.getYear() != year) {
            throw new BoardError("a " + filed.getYear() + " filing does not go in the " + year + " series");
        }
        Filing entry = new Filing(nextNumber(), kind, lift, filed, about, filedBy);
        List<Filing> longer = new ArrayList<>(filings);
        longer.add(entry);
        return new Filed(of(state, year, longer), entry);
    }

    /**
     * The filings against one machine.
     *
     * @param lift the machine
     * @return the filings, in order
     */
    public List<Filing> forLift(LiftCode lift) {
        return filings.stream().filter(filing -> filing.lift().equals(lift)).toList();
    }

    /**
     * The filings of one kind.
     *
     * @param kind the kind
     * @return the filings, in order
     */
    public List<Filing> ofKind(FilingKind kind) {
        return filings.stream().filter(filing -> filing.kind() == kind).toList();
    }

    /**
     * The filings still waiting on the board to write back.
     *
     * @return the filings, in order
     */
    public List<Filing> outstanding() {
        return filings.stream().filter(Filing::isOutstanding).toList();
    }

    /**
     * Record the board writing back on one filing.
     *
     * @param number the filing's number
     * @param on the day the board wrote back
     * @return a new series, in the same order; this one is unchanged
     * @throws BoardError when the number is not in this series, or that filing is already
     *     acknowledged
     */
    public FilingSeries acknowledge(FilingNumber number, LocalDate on) {
        Filing found = filings.stream()
                .filter(filing -> filing.number().equals(number))
                .findFirst()
                .orElseThrow(() -> new BoardError(number + " is not in this series"));
        List<Filing> changed = new ArrayList<>(filings.stream()
                .filter(filing -> !filing.number().equals(number))
                .toList());
        changed.add(found.acknowledge(on));
        return of(state, year, changed);
    }

    /**
     * How many filings are in the series.
     *
     * @return the count
     */
    public int size() {
        return filings.size();
    }

    /**
     * What filing through a series gives back.
     *
     * @param series the series with the new filing in it
     * @param filing the new filing, under the number it took
     */
    public record Filed(FilingSeries series, Filing filing) {}
}
