package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One machine's season, added up once.
 *
 * @param lift the machine
 * @param days its day records, in the order they were tallied
 * @param daysOpen the days it opened
 * @param daysShut the days it did not
 * @param scheduled the time it was meant to run
 * @param running the time it ran
 * @param downtime the time it was stopped
 * @param chargeable the stopped time charged to the machine
 * @param stops how many stops of each kind, kinds in the order they were first seen
 */
public record LiftTally(
        LiftCode lift,
        List<DayRecord> days,
        int daysOpen,
        int daysShut,
        Duration scheduled,
        Duration running,
        Duration downtime,
        Duration chargeable,
        Map<StopKind, Integer> stops) {

    /** A tally. */
    public LiftTally {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(scheduled, "scheduled");
        Objects.requireNonNull(running, "running");
        Objects.requireNonNull(downtime, "downtime");
        Objects.requireNonNull(chargeable, "chargeable");
        days = List.copyOf(days);
        stops = Collections.unmodifiableMap(new LinkedHashMap<>(stops));
    }

    /**
     * Add up a machine's days, walking them once.
     *
     * @param lift the machine
     * @param days its day records
     * @return the tally, all zeros for no days
     */
    public static LiftTally of(LiftCode lift, List<DayRecord> days) {
        Duration scheduled = Duration.ZERO;
        Duration running = Duration.ZERO;
        Duration downtime = Duration.ZERO;
        Duration chargeable = Duration.ZERO;
        int open = 0;
        int shut = 0;
        Map<StopKind, Integer> stops = new LinkedHashMap<>();
        for (DayRecord record : days) {
            scheduled = scheduled.plus(record.scheduled());
            running = running.plus(record.running());
            downtime = downtime.plus(record.downtime());
            chargeable = chargeable.plus(record.chargeable());
            if (record.opened()) {
                open += 1;
            } else {
                shut += 1;
            }
            for (StopKind kind : record.stops()) {
                stops.merge(kind, 1, Integer::sum);
            }
        }
        return new LiftTally(lift, days, open, shut, scheduled, running, downtime, chargeable, stops);
    }

    /**
     * The share of the season's scheduled time the machine was not charged for.
     *
     * @return a fraction, zero when nothing was scheduled
     */
    public BigDecimal availability() {
        return DayRecord.availability(scheduled.seconds(), chargeable.seconds());
    }

    /**
     * The day that cost the most charged time; of days that cost the same, the earliest tallied.
     *
     * @return the day, or empty for a machine with no days
     */
    public Optional<DayRecord> worstDay() {
        DayRecord worst = null;
        for (DayRecord one : days) {
            if (worst == null || one.chargeable().compareTo(worst.chargeable()) > 0) {
                worst = one;
            }
        }
        return Optional.ofNullable(worst);
    }
}
