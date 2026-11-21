package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One walk reduced to the number the trend is drawn through.
 *
 * @param taken the day of the walk
 * @param smallest the mean diameter at the worst station
 */
public record WearPoint(LocalDate taken, Length smallest) {

    /** A point. */
    public WearPoint {
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(smallest, "smallest");
    }

    /** Such as {@code 2025-01-15 1.330"}. */
    @Override
    public String toString() {
        return taken + " " + smallest.formatInches(3);
    }
}
