package io.github.rkaushikdev.sheaveline.upkeep;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One part number the line is going to want and the shed cannot cover.
 *
 * @param kind the part
 * @param wanted how many the line will want inside the window
 * @param onHand how many are on the shelf
 * @param least how many have to stay on the shelf in reserve
 * @param orderBy the last day an order arrives in time, when there is one
 * @param estimated whether the date was worked out from an assumed rate of running
 */
public record Shortage(
        PartKind kind, int wanted, int onHand, int least, Optional<LocalDate> orderBy, boolean estimated) {

    /** A shortage. */
    public Shortage {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(orderBy, "orderBy");
    }

    /**
     * How many more the line wants than the shelf can spare.
     *
     * @return the count, never negative
     */
    public int shortBy() {
        return Math.max(0, wanted - Math.max(0, onHand - least));
    }

    /**
     * Whether the order should already have gone in.
     *
     * @param on the day asked about
     * @return true once the order by date is that day or earlier
     */
    public boolean isUrgent(LocalDate on) {
        return orderBy.isPresent() && !orderBy.get().isAfter(on);
    }

    /** Such as {@code LIN short 3, order by 2025-11-27, estimated}. */
    @Override
    public String toString() {
        String when = orderBy.map(LocalDate::toString).orElse("no date");
        String note = estimated ? ", estimated" : "";
        return kind.code() + " short " + shortBy() + ", order by " + when + note;
    }
}
