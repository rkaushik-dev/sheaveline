package io.github.rkaushikdev.sheaveline.upkeep;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One job on one machine's ladder, and every clock it is measured against.
 *
 * <p>A job on two clocks is due when either of them says so, and the two are asked separately
 * because a machine that ran three hundred days and a machine that ran thirty will hit them in a
 * different order.
 *
 * @param job the job
 * @param dueOn the day it falls due, when it runs on the calendar
 * @param dueAtHours the hour meter reading it falls due at, when it runs on the hour meter and has
 *     been done with a reading
 * @param dueAtCycles the cycle counter reading it falls due at, when it runs on the cycle counter
 *     and has been done with a reading
 * @param everDone whether the machine's book holds any record of it
 */
public record DueItem(
        Requirement job,
        Optional<LocalDate> dueOn,
        Optional<BigDecimal> dueAtHours,
        Optional<Long> dueAtCycles,
        boolean everDone) {

    /** A due item. */
    public DueItem {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(dueOn, "dueOn");
        Objects.requireNonNull(dueAtHours, "dueAtHours");
        Objects.requireNonNull(dueAtCycles, "dueAtCycles");
    }

    /**
     * Whether the calendar has taken the job past due.
     *
     * @param on the day asked about
     * @return true once the day is after the due date
     */
    public boolean calendarOverdue(LocalDate on) {
        return dueOn.isPresent() && on.isAfter(dueOn.get());
    }

    /**
     * Whether the hour meter has taken the job past due.
     *
     * @param hours the hour meter now, if read
     * @return true once a reading is past the one it falls due at; never without both
     */
    public boolean hoursOverdue(Optional<BigDecimal> hours) {
        return dueAtHours.isPresent() && hours.isPresent() && hours.get().compareTo(dueAtHours.get()) > 0;
    }

    /**
     * Whether the cycle counter has taken the job past due.
     *
     * @param cycles the cycle counter now, if read
     * @return true once a reading is past the one it falls due at; never without both
     */
    public boolean cyclesOverdue(Optional<Long> cycles) {
        return dueAtCycles.isPresent() && cycles.isPresent() && cycles.get() > dueAtCycles.get();
    }

    /**
     * Whether the calendar alone has taken the job past due, with neither meter read.
     *
     * @param on the day asked about
     * @return true when overdue
     */
    public boolean isOverdue(LocalDate on) {
        return isOverdue(on, Meters.NOT_READ);
    }

    /**
     * Whether any clock has taken the job past due.
     *
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @return true when any clock has fired
     */
    public boolean isOverdue(LocalDate on, Meters meters) {
        return calendarOverdue(on) || hoursOverdue(meters.hours()) || cyclesOverdue(meters.cycles());
    }

    /**
     * Which clock took the job past due, with neither meter read.
     *
     * @param on the day asked about
     * @return the calendar, or nothing
     */
    public Set<Basis> fired(LocalDate on) {
        return fired(on, Meters.NOT_READ);
    }

    /**
     * Which clock or clocks took the job past due, which is what the note says.
     *
     * @param on the day asked about
     * @param meters what the machine's meters read now
     * @return an unmodifiable set in declaration order, empty when the job is not overdue
     */
    public Set<Basis> fired(LocalDate on, Meters meters) {
        Set<Basis> which = EnumSet.noneOf(Basis.class);
        if (calendarOverdue(on)) {
            which.add(Basis.CALENDAR);
        }
        if (hoursOverdue(meters.hours())) {
            which.add(Basis.HOURS);
        }
        if (cyclesOverdue(meters.cycles())) {
            which.add(Basis.CYCLES);
        }
        return Collections.unmodifiableSet(which);
    }

    /**
     * Days from a given day to the calendar due date.
     *
     * @param on the day asked about
     * @return the days, negative once past due, or empty when the job is not on the calendar
     */
    public Optional<Long> daysUntil(LocalDate on) {
        return dueOn.map(due -> ChronoUnit.DAYS.between(on, due));
    }

    /** Such as {@code M1 due 2025-01-31}, or {@code H1 due on the meter}. */
    @Override
    public String toString() {
        String when = dueOn.map(LocalDate::toString).orElse("on the meter");
        return job.code() + " due " + when;
    }
}
