package io.github.rkaushikdev.sheaveline.schedule;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Operating windows by day type, with one fallback for anything unlisted.
 *
 * <p>Hours are set per day type, not per date, so the holiday schedule is one row rather than
 * fourteen.
 *
 * @param fallback the window for any operating day type not listed
 * @param byDayType the windows for the day types that differ
 */
public record HoursTable(OperatingWindow fallback, Map<DayType, OperatingWindow> byDayType) {

    /** A table, its map copied and kept in day type order. */
    public HoursTable {
        Objects.requireNonNull(fallback, "fallback");
        EnumMap<DayType, OperatingWindow> copied = new EnumMap<>(DayType.class);
        copied.putAll(byDayType);
        byDayType = Collections.unmodifiableMap(copied);
    }

    /**
     * The window for a kind of day.
     *
     * @param dayType the kind of day
     * @return its window, the fallback when unlisted, or empty on a closed day
     */
    public Optional<OperatingWindow> windowForType(DayType dayType) {
        if (!dayType.isOperating()) {
            return Optional.empty();
        }
        return Optional.of(byDayType.getOrDefault(dayType, fallback));
    }

    /**
     * The window that applies to a date.
     *
     * @param season the season the date falls in
     * @param day the date
     * @return its window, or empty on a closed day
     */
    public Optional<OperatingWindow> windowOn(Season season, LocalDate day) {
        return windowForType(season.dayType(day));
    }
}
