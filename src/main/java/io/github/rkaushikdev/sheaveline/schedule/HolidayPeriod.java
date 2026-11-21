package io.github.rkaushikdev.sheaveline.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * A named block of days the resort staffs as a holiday.
 *
 * @param name what marketing calls it
 * @param first the first day, inclusive
 * @param last the last day, inclusive
 */
public record HolidayPeriod(String name, LocalDate first, LocalDate last) {

    /**
     * A holiday period.
     *
     * @throws SeasonError when it ends before it starts
     */
    public HolidayPeriod {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(last, "last");
        if (last.isBefore(first)) {
            throw new SeasonError("holiday " + name + " ends before it starts");
        }
    }

    /**
     * Whether the day falls in this period.
     *
     * @param day the day
     * @return true from the first day to the last, both included
     */
    public boolean covers(LocalDate day) {
        return !day.isBefore(first) && !day.isAfter(last);
    }

    /**
     * How many days the period runs.
     *
     * @return the count, both ends included
     */
    public long days() {
        return ChronoUnit.DAYS.between(first, last) + 1;
    }
}
