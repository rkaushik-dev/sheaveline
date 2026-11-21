package io.github.rkaushikdev.sheaveline.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One winter, from the first preview day to the last day the lifts turn, and what kind of day any
 * date in it is.
 *
 * <p>The department does not get to decide when it runs. Marketing publishes a season with an
 * opening day, a closing day, a handful of holiday periods and a spring schedule, plus the odd
 * scheduled closure for a race or a wind day the mountain manager called in advance. Every other rule
 * reads the day type, so the precedence between them is decided in {@link #dayType} and nowhere else.
 *
 * <p>Precedence, highest first. A day outside the season or on the closure list is closed and nothing
 * else matters. Then a preview day, which the machines run for but the public does not ride. Then a
 * holiday period, which beats a weekend because the crew size is set by the holiday and not by the
 * calendar. Then spring, which is a different staffing model again. Then the weekend. Whatever is left
 * is midweek.
 *
 * @param label how the season is named, such as {@code 2024/25}
 * @param opens the first public day
 * @param closes the last day the lifts turn
 * @param previewFrom the first preview day, when there are any
 * @param springFrom the first day of the spring schedule, when there is one
 * @param holidays the holiday periods
 * @param closures the scheduled closures
 */
public record Season(
        String label,
        LocalDate opens,
        LocalDate closes,
        Optional<LocalDate> previewFrom,
        Optional<LocalDate> springFrom,
        List<HolidayPeriod> holidays,
        Set<LocalDate> closures) {

    /**
     * A season that holds together.
     *
     * @throws SeasonError when it closes before it opens, previews after it opens, starts spring
     *     outside itself, or has a holiday outside itself
     */
    public Season {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(opens, "opens");
        Objects.requireNonNull(closes, "closes");
        Objects.requireNonNull(previewFrom, "previewFrom");
        Objects.requireNonNull(springFrom, "springFrom");
        holidays = List.copyOf(holidays);
        closures = Set.copyOf(closures);
        if (closes.isBefore(opens)) {
            throw new SeasonError("season " + label + " closes before it opens");
        }
        if (previewFrom.isPresent() && previewFrom.get().isAfter(opens)) {
            throw new SeasonError("season " + label + " previews after it opens");
        }
        if (springFrom.isPresent()
                && (springFrom.get().isBefore(opens) || springFrom.get().isAfter(closes))) {
            throw new SeasonError("season " + label + " starts spring outside the season");
        }
        LocalDate first = previewFrom.orElse(opens);
        for (HolidayPeriod holiday : holidays) {
            if (holiday.last().isBefore(first) || holiday.first().isAfter(closes)) {
                throw new SeasonError("holiday " + holiday.name() + " falls outside " + label);
            }
        }
    }

    /**
     * A plain season: no preview, no spring schedule, no holidays and no closures.
     *
     * @param label how the season is named
     * @param opens the first day
     * @param closes the last day
     */
    public Season(String label, LocalDate opens, LocalDate closes) {
        this(label, opens, closes, Optional.empty(), Optional.empty(), List.of(), Set.of());
    }

    /**
     * The first day anything runs: the first preview day, or opening day when there is no preview.
     *
     * @return the first day
     */
    public LocalDate firstDay() {
        return previewFrom.orElse(opens);
    }

    /**
     * Whether the day falls between the first day and closing day.
     *
     * @param day the day
     * @return true inside the season, both ends included
     */
    public boolean covers(LocalDate day) {
        return !day.isBefore(firstDay()) && !day.isAfter(closes);
    }

    /**
     * The holiday period a day falls in.
     *
     * @param day the day
     * @return the first period that covers it, or empty
     */
    public Optional<HolidayPeriod> holidayOn(LocalDate day) {
        return holidays.stream().filter(holiday -> holiday.covers(day)).findFirst();
    }

    /**
     * What kind of day a date is. The one place the precedence between day kinds is decided.
     *
     * @param day the day
     * @return its type
     */
    public DayType dayType(LocalDate day) {
        if (!covers(day) || closures.contains(day)) {
            return DayType.CLOSED;
        }
        if (day.isBefore(opens)) {
            return DayType.PREVIEW;
        }
        if (holidayOn(day).isPresent()) {
            return DayType.HOLIDAY;
        }
        if (springFrom.isPresent() && !day.isBefore(springFrom.get())) {
            return DayType.SPRING;
        }
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return DayType.WEEKEND;
        }
        return DayType.MIDWEEK;
    }

    /**
     * Whether the machines run on the day.
     *
     * @param day the day
     * @return true on any day that is not closed
     */
    public boolean isOperating(LocalDate day) {
        return dayType(day).isOperating();
    }

    /**
     * Whether the public rides on the day.
     *
     * @param day the day
     * @return true on an operating day that is not a preview
     */
    public boolean isPublic(LocalDate day) {
        return dayType(day).isPublic();
    }

    /**
     * Every date from the first day to closing day.
     *
     * @return the dates in order
     */
    public List<LocalDate> days() {
        return firstDay().datesUntil(closes.plusDays(1)).toList();
    }

    /**
     * The dates the machines run.
     *
     * @return the operating days in order
     */
    public List<LocalDate> operatingDays() {
        return days().stream().filter(this::isOperating).toList();
    }

    /**
     * The dates the public rides.
     *
     * @return the public days in order
     */
    public List<LocalDate> publicDays() {
        return days().stream().filter(this::isPublic).toList();
    }

    /**
     * Which operating day of the season this is, counting from one.
     *
     * @param day an operating day
     * @return its number
     * @throws SeasonError when the day is not an operating day
     */
    public int dayNumber(LocalDate day) {
        if (!isOperating(day)) {
            throw new SeasonError(day + " is not an operating day of " + label);
        }
        return (int) operatingDays().stream().filter(each -> !each.isAfter(day)).count();
    }

    /**
     * Operating days left after this one, which the crew plan reads.
     *
     * @param day the day
     * @return the count of later operating days
     */
    public int daysRemaining(LocalDate day) {
        return (int) operatingDays().stream().filter(each -> each.isAfter(day)).count();
    }

    /**
     * How many days the machines run.
     *
     * @return the operating day count
     */
    public int length() {
        return operatingDays().size();
    }

    /** Such as {@code 2024/25 (2024-11-29 to 2025-04-20)}. */
    @Override
    public String toString() {
        return label + " (" + opens + " to " + closes + ")";
    }
}
