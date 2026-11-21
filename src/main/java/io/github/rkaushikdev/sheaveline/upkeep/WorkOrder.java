package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One job somebody owns, with a number and a date, which is what a defect turns into once somebody
 * owns it.
 *
 * <p>A defect is an observation. A work order is a promise, with a number, a name against it and a
 * date it should be done by. The two are separate because a defect can sit in the book for a season
 * with nobody arguing about it, and the moment it becomes a work order the clock the mountain
 * manager reads starts.
 *
 * @param number the order's number, counted from one
 * @param lift the machine
 * @param raised the day the order was raised
 * @param raisedBy who raised it
 * @param detail what is to be done
 * @param priority how long the department has
 * @param where where on the machine, when the order came off a defect
 * @param closed the day the work was finished, once it has been
 * @param closedBy who finished it, once somebody has
 */
public record WorkOrder(
        int number,
        LiftCode lift,
        LocalDate raised,
        String raisedBy,
        String detail,
        Priority priority,
        Optional<String> where,
        Optional<LocalDate> closed,
        Optional<String> closedBy) {

    /**
     * A work order.
     *
     * @throws UpkeepError when the number is under one, nobody signed it, it says nothing, it closed
     *     before it was raised, or it has only one of the day it closed and who closed it
     */
    public WorkOrder {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(raised, "raised");
        Objects.requireNonNull(raisedBy, "raisedBy");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(closed, "closed");
        Objects.requireNonNull(closedBy, "closedBy");
        if (number < 1) {
            throw new UpkeepError("work orders are numbered from one");
        }
        if (raisedBy.strip().isEmpty()) {
            throw new UpkeepError("work order " + number + " is signed by whoever raised it");
        }
        if (detail.strip().isEmpty()) {
            throw new UpkeepError("work order " + number + " says nothing");
        }
        if (closed.isPresent() && closed.get().isBefore(raised)) {
            throw new UpkeepError("work order " + number + " closed before it was raised");
        }
        if (closed.isPresent() != closedBy.isPresent()) {
            throw new UpkeepError("a work order is closed by somebody on some day, or neither");
        }
    }

    /**
     * An open order not tied to one place on the machine.
     *
     * @param number the order's number
     * @param lift the machine
     * @param raised the day it was raised
     * @param raisedBy who raised it
     * @param detail what is to be done
     * @param priority how long the department has
     */
    public WorkOrder(int number, LiftCode lift, LocalDate raised, String raisedBy, String detail, Priority priority) {
        this(number, lift, raised, raisedBy, detail, priority, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * An open order raised off a defect, dated from the day the defect was found. The priority comes
     * off the defect rather than off whoever is typing.
     *
     * @param number the order's number
     * @param lift the machine
     * @param defect the defect
     * @param raisedBy who raised it
     * @return the order
     */
    public static WorkOrder fromDefect(int number, LiftCode lift, Defect defect, String raisedBy) {
        return new WorkOrder(
                number,
                lift,
                defect.raised(),
                raisedBy,
                defect.detail(),
                Priority.forLevel(defect.level()),
                Optional.of(defect.where()),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Whether the work is still to do.
     *
     * @return true until the order is closed
     */
    public boolean isOpen() {
        return closed.isEmpty();
    }

    /**
     * The day the work should be done by.
     *
     * @return the day raised plus the priority's target
     */
    public LocalDate dueBy() {
        return raised.plusDays(priority.targetDays());
    }

    /**
     * Whether the order is late: an open order once its date has passed, a closed one if it was
     * closed after its date.
     *
     * @param on the day asked about, which matters only while the order is open
     * @return true when late
     */
    public boolean isOverdue(LocalDate on) {
        return closed.map(day -> day.isAfter(dueBy())).orElseGet(() -> on.isAfter(dueBy()));
    }

    /**
     * How long the order was open, or has been so far.
     *
     * @param on the day an open order is measured to; a closed one is measured to its closing
     * @return the whole days
     * @throws UpkeepError when that day is before the order was raised
     */
    public long ageInDays(LocalDate on) {
        LocalDate end = closed.orElse(on);
        if (end.isBefore(raised)) {
            throw new UpkeepError("that day is before the order was raised");
        }
        return ChronoUnit.DAYS.between(raised, end);
    }

    /**
     * Close the order.
     *
     * @param on the day the work was finished
     * @param by who finished it
     * @return the closed order; this one is unchanged
     * @throws UpkeepError when it is already closed, nobody is named, or the day is before it was
     *     raised
     */
    public WorkOrder close(LocalDate on, String by) {
        if (!isOpen()) {
            throw new UpkeepError("work order " + number + " is already closed");
        }
        if (by.strip().isEmpty()) {
            throw new UpkeepError("a work order is closed by somebody");
        }
        return new WorkOrder(number, lift, raised, raisedBy, detail, priority, where, Optional.of(on), Optional.of(by));
    }

    /** Such as {@code 1 scheduled on JP, sheave liner cracked (open)}. */
    @Override
    public String toString() {
        String state = isOpen() ? "open" : "closed";
        return number + " " + priority.value() + " on " + lift + ", " + detail + " (" + state + ")";
    }
}
