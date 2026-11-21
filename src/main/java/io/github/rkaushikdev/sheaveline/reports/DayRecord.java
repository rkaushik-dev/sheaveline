package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One machine on one day, as the day log left it.
 *
 * @param lift the machine
 * @param day the day
 * @param opened whether it opened to the public
 * @param scheduled how long it was meant to run
 * @param running how long it ran
 * @param downtime how long it was stopped, whatever stopped it
 * @param chargeable the part of the downtime that was the machine's own fault
 * @param stops why it stopped, a kind for every stop
 */
public record DayRecord(
        LiftCode lift,
        LocalDate day,
        boolean opened,
        Duration scheduled,
        Duration running,
        Duration downtime,
        Duration chargeable,
        List<StopKind> stops) {

    /** A day record. */
    public DayRecord {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(scheduled, "scheduled");
        Objects.requireNonNull(running, "running");
        Objects.requireNonNull(downtime, "downtime");
        Objects.requireNonNull(chargeable, "chargeable");
        stops = List.copyOf(stops);
    }

    /**
     * A day with no stops on it.
     *
     * @param lift the machine
     * @param day the day
     * @param opened whether it opened to the public
     * @param scheduled how long it was meant to run
     * @param running how long it ran
     * @param downtime how long it was stopped
     * @param chargeable the part of the downtime that was the machine's own fault
     */
    public DayRecord(
            LiftCode lift,
            LocalDate day,
            boolean opened,
            Duration scheduled,
            Duration running,
            Duration downtime,
            Duration chargeable) {
        this(lift, day, opened, scheduled, running, downtime, chargeable, List.of());
    }

    /**
     * The share of the scheduled time the machine was not charged for.
     *
     * @return a fraction, one when nothing was charged, zero for a day nothing was scheduled on
     */
    public BigDecimal availability() {
        return availability(scheduled.seconds(), chargeable.seconds());
    }

    /**
     * The availability rule every page shares: the scheduled time less what was charged, over the
     * scheduled time.
     *
     * @param scheduled the seconds the machine was meant to run
     * @param chargeable the seconds it was charged for
     * @return the fraction, zero when nothing was scheduled
     */
    static BigDecimal availability(long scheduled, long chargeable) {
        if (scheduled == 0) {
            return BigDecimal.ZERO;
        }
        return Decimals.divide(scheduled - chargeable, scheduled);
    }
}
