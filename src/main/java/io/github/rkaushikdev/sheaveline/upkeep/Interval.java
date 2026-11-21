package io.github.rkaushikdev.sheaveline.upkeep;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * How often a job comes round, on whichever clocks apply to it, and the awkward fact that a job can
 * be due on two clocks at once.
 *
 * <p>Some jobs come round on the calendar. The annual inspection is annual whether the lift ran
 * three hundred days or thirty. Some come round on the hour meter, because a gearbox does not care
 * what month it is. And a few carry both, in which case whichever arrives first is the one that
 * matters, and the other one resets with it.
 *
 * <p>Grace is real and it is not slack. A monthly job with seven days of grace is not a job due
 * every thirty seven days: the next one is still counted from when the last one was due, not from
 * when it was finally done, or a machine drifts a week later every month until the annual lands in
 * July.
 *
 * @param days the calendar interval, when the job runs on the calendar
 * @param hours the hour meter interval, when the job runs on the hour meter
 * @param cycles the cycle counter interval, when the job runs on the cycle counter
 * @param graceDays how many days past the calendar due date the job may still be done on time
 */
public record Interval(Optional<Integer> days, Optional<Integer> hours, Optional<Long> cycles, int graceDays) {

    /**
     * An interval on at least one clock.
     *
     * @throws UpkeepError when it runs on no clock, a clock is under one, or the grace is negative
     */
    public Interval {
        Objects.requireNonNull(days, "days");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(cycles, "cycles");
        if (days.isEmpty() && hours.isEmpty() && cycles.isEmpty()) {
            throw new UpkeepError("an interval runs on at least one clock");
        }
        refuseUnderOne(days.map(Integer::longValue), "days");
        refuseUnderOne(hours.map(Integer::longValue), "hours");
        refuseUnderOne(cycles, "cycles");
        if (graceDays < 0) {
            throw new UpkeepError("grace is not negative");
        }
    }

    /**
     * A job on the calendar alone, with no grace.
     *
     * @param days how many days apart
     * @return the interval
     * @throws UpkeepError when the days are under one
     */
    public static Interval ofDays(int days) {
        return new Interval(Optional.of(days), Optional.empty(), Optional.empty(), 0);
    }

    /**
     * A job on the hour meter alone.
     *
     * @param hours how many running hours apart
     * @return the interval
     * @throws UpkeepError when the hours are under one
     */
    public static Interval ofHours(int hours) {
        return new Interval(Optional.empty(), Optional.of(hours), Optional.empty(), 0);
    }

    /**
     * A job on the cycle counter alone.
     *
     * @param cycles how many cycles apart
     * @return the interval
     * @throws UpkeepError when the cycles are under one
     */
    public static Interval ofCycles(long cycles) {
        return new Interval(Optional.empty(), Optional.empty(), Optional.of(cycles), 0);
    }

    /**
     * Every day, with no grace.
     *
     * @return the interval
     */
    public static Interval daily() {
        return ofDays(1);
    }

    /**
     * Every seven days, with two days of grace.
     *
     * @return the interval
     */
    public static Interval weekly() {
        return weekly(2);
    }

    /**
     * Every seven days.
     *
     * @param graceDays the grace
     * @return the interval
     */
    public static Interval weekly(int graceDays) {
        return ofDays(7).withGraceDays(graceDays);
    }

    /**
     * Every thirty days, with seven days of grace.
     *
     * @return the interval
     */
    public static Interval monthly() {
        return monthly(7);
    }

    /**
     * Every thirty days.
     *
     * @param graceDays the grace
     * @return the interval
     */
    public static Interval monthly(int graceDays) {
        return ofDays(30).withGraceDays(graceDays);
    }

    /**
     * Every three hundred and sixty five days, with thirty days of grace.
     *
     * @return the interval
     */
    public static Interval annual() {
        return annual(30);
    }

    /**
     * Every three hundred and sixty five days.
     *
     * @param graceDays the grace
     * @return the interval
     */
    public static Interval annual(int graceDays) {
        return ofDays(365).withGraceDays(graceDays);
    }

    /**
     * Every eighteen hundred and twenty six days, with sixty days of grace.
     *
     * @return the interval
     */
    public static Interval fiveYearly() {
        return fiveYearly(60);
    }

    /**
     * Every eighteen hundred and twenty six days, five years with a leap day in them.
     *
     * @param graceDays the grace
     * @return the interval
     */
    public static Interval fiveYearly(int graceDays) {
        return ofDays(1826).withGraceDays(graceDays);
    }

    /**
     * The same interval with a calendar clock as well, or a different one.
     *
     * @param days how many days apart
     * @return a new interval; this one is unchanged
     */
    public Interval withDays(int days) {
        return new Interval(Optional.of(days), hours, cycles, graceDays);
    }

    /**
     * The same interval with an hour meter clock as well, or a different one.
     *
     * @param hours how many running hours apart
     * @return a new interval; this one is unchanged
     */
    public Interval withHours(int hours) {
        return new Interval(days, Optional.of(hours), cycles, graceDays);
    }

    /**
     * The same interval with a cycle counter clock as well, or a different one.
     *
     * @param cycles how many cycles apart
     * @return a new interval; this one is unchanged
     */
    public Interval withCycles(long cycles) {
        return new Interval(days, hours, Optional.of(cycles), graceDays);
    }

    /**
     * The same interval with different grace.
     *
     * @param graceDays the grace
     * @return a new interval; this one is unchanged
     */
    public Interval withGraceDays(int graceDays) {
        return new Interval(days, hours, cycles, graceDays);
    }

    /**
     * The clocks this interval runs on.
     *
     * @return an unmodifiable set in declaration order, never empty
     */
    public Set<Basis> bases() {
        Set<Basis> found = EnumSet.noneOf(Basis.class);
        days.ifPresent(any -> found.add(Basis.CALENDAR));
        hours.ifPresent(any -> found.add(Basis.HOURS));
        cycles.ifPresent(any -> found.add(Basis.CYCLES));
        return Collections.unmodifiableSet(found);
    }

    /**
     * Whether the interval runs on one clock.
     *
     * @param basis the clock
     * @return true when it is one of this interval's clocks
     */
    public boolean runsOn(Basis basis) {
        return bases().contains(basis);
    }

    /**
     * When the next one falls due on the calendar, counted from when the last one was due and not
     * from when it was done.
     *
     * @param lastDue when the last one was due
     * @return the due date, or empty when the job does not run on the calendar
     */
    public Optional<LocalDate> dueDate(LocalDate lastDue) {
        return days.map(lastDue::plusDays);
    }

    /**
     * The last day the next one can be done before it is late.
     *
     * @param lastDue when the last one was due
     * @return the due date plus the grace, or empty when the job does not run on the calendar
     */
    public Optional<LocalDate> lastDayOfGrace(LocalDate lastDue) {
        return dueDate(lastDue).map(due -> due.plusDays(graceDays));
    }

    /**
     * The hour meter reading the next one falls due at.
     *
     * @param hoursAtLast the hour meter when the last one was done
     * @return the reading, or empty when the job does not run on the hour meter
     */
    public Optional<BigDecimal> dueHours(BigDecimal hoursAtLast) {
        return hours.map(every -> hoursAtLast.add(BigDecimal.valueOf(every)));
    }

    /**
     * The cycle counter reading the next one falls due at.
     *
     * @param cyclesAtLast the cycle counter when the last one was done
     * @return the reading, or empty when the job does not run on the cycle counter
     */
    public Optional<Long> dueCycles(long cyclesAtLast) {
        return cycles.map(every -> cyclesAtLast + every);
    }

    /** Such as {@code every 365 days or every 1500 hours}, cycles grouped in thousands. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        days.ifPresent(every -> parts.add("every " + every + " days"));
        hours.ifPresent(every -> parts.add("every " + every + " hours"));
        cycles.ifPresent(every -> parts.add("every " + String.format(Locale.ROOT, "%,d", every) + " cycles"));
        return String.join(" or ", parts);
    }

    private static void refuseUnderOne(Optional<Long> value, String name) {
        if (value.isPresent() && value.get() < 1) {
            throw new UpkeepError("an interval of " + value.get() + " " + name + " is not an interval");
        }
    }
}
