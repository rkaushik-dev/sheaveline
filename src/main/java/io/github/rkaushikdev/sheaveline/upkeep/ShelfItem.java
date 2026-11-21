package io.github.rkaushikdev.sheaveline.upkeep;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One part number and how many of it are in the shed.
 *
 * @param kind the part
 * @param onHand how many are on the shelf
 * @param leadDays how many days one takes to arrive once ordered
 * @param least how many have to stay on the shelf in reserve
 */
public record ShelfItem(PartKind kind, int onHand, int leadDays, int least) {

    /**
     * A shelf item.
     *
     * @throws UpkeepError when any of the counts is negative
     */
    public ShelfItem {
        Objects.requireNonNull(kind, "kind");
        if (onHand < 0) {
            throw new UpkeepError(kind.code() + " cannot have " + onHand + " on the shelf");
        }
        if (leadDays < 0) {
            throw new UpkeepError(kind.code() + " cannot arrive before it is ordered");
        }
        if (least < 0) {
            throw new UpkeepError(kind.code() + " cannot want fewer than none in reserve");
        }
    }

    /**
     * A shelf item with no reserve.
     *
     * @param kind the part
     * @param onHand how many are on the shelf
     * @param leadDays how many days one takes to arrive once ordered
     */
    public ShelfItem(PartKind kind, int onHand, int leadDays) {
        this(kind, onHand, leadDays, 0);
    }

    /**
     * What is on the shelf over and above what has to stay there.
     *
     * @return the count, never negative
     */
    public int spare() {
        return Math.max(0, onHand - least);
    }

    /**
     * The day one would arrive if it were ordered on a given day.
     *
     * @param on the day ordered
     * @return that day plus the lead time
     */
    public LocalDate arrivesIfOrdered(LocalDate on) {
        return on.plusDays(leadDays);
    }

    /** Such as {@code LIN 4 on hand, 42 days out}. */
    @Override
    public String toString() {
        return kind.code() + " " + onHand + " on hand, " + leadDays + " days out";
    }
}
