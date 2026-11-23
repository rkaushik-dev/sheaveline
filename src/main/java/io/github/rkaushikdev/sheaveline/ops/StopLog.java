package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Every stop on one machine over some window.
 *
 * @param lift the machine
 * @param stops the stops, in the order they happened
 */
public record StopLog(LiftCode lift, List<StopEvent> stops) {

    /** How far back a stop looks for the ones before it. */
    public static final Duration REPEAT_WINDOW = Duration.ofMinutes(60);

    /** This many trips inside the window call the mechanic out. */
    public static final int REPEAT_LIMIT = 3;

    /**
     * A log of one machine's stops.
     *
     * @throws OperationsError when a stop is on another machine
     */
    public StopLog {
        Objects.requireNonNull(lift, "lift");
        stops = List.copyOf(stops);
        for (StopEvent stop : stops) {
            if (!stop.lift().equals(lift)) {
                throw new OperationsError(stop.lift() + " does not belong in a " + lift + " log");
            }
        }
    }

    /**
     * A log with nothing in it.
     *
     * @param lift the machine
     * @return the empty log
     */
    public static StopLog of(LiftCode lift) {
        return new StopLog(lift, List.of());
    }

    /**
     * A log with its stops put in the order they happened.
     *
     * @param lift the machine
     * @param stops the stops, in any order
     * @return the log
     */
    public static StopLog of(LiftCode lift, List<StopEvent> stops) {
        return new StopLog(
                lift,
                stops.stream().sorted(Comparator.comparing(StopEvent::stopped)).toList());
    }

    /**
     * The stops of one kind.
     *
     * @param kind the kind
     * @return those stops, in order
     */
    public List<StopEvent> ofKind(StopKind kind) {
        return where(stop -> stop.kind() == kind);
    }

    /**
     * The stops the board hears about.
     *
     * @return the reportable stops, in order
     */
    public List<StopEvent> reportable() {
        return where(stop -> stop.kind().isReportable());
    }

    /**
     * The stops nobody has run the machine again after.
     *
     * @return the open stops, in order
     */
    public List<StopEvent> openStops() {
        return where(StopEvent::isOpen);
    }

    /**
     * How long the machine stood, counting the closed stops.
     *
     * @return the summed length of every closed stop
     */
    public Duration downtime() {
        return closedLength(stop -> true);
    }

    /**
     * Only the stops that count against the machine.
     *
     * @return the summed length of every closed stop that was the machine's fault
     */
    public Duration chargeableDowntime() {
        return closedLength(stop -> stop.kind().countsAgainstTheMachine());
    }

    /**
     * The longest closed stop.
     *
     * @return the first of the longest, when two tie
     * @throws OperationsError when nothing in the log has been closed
     */
    public StopEvent longest() {
        return stops.stream()
                .filter(stop -> !stop.isOpen())
                .max(Comparator.comparing(StopEvent::length))
                .orElseThrow(() -> new OperationsError("nothing in this log has been closed yet"));
    }

    /**
     * Running time over scheduled time, with the machine's own stops taken off.
     *
     * @param scheduled how long the machine was meant to run
     * @return the fraction of it the machine was available
     * @throws OperationsError when nothing was scheduled
     */
    public BigDecimal availability(Duration scheduled) {
        if (scheduled.isZero()) {
            throw new OperationsError("availability needs a scheduled run time");
        }
        long lost = chargeableDowntime().seconds();
        return Decimals.divide(scheduled.seconds() - lost, scheduled.seconds());
    }

    /**
     * The trips in the hour up to and including one stop: the stops the machine made by itself.
     *
     * <p>One safety circuit trip is a tower switch that got a gust. Three inside an hour is a switch,
     * a sheave or a rope position fault that is going to keep doing it, and the operator resetting
     * it a fourth time is how a small fault becomes a deropement.
     *
     * @param stop one of the stops in the log
     * @return the trips in the {@link #REPEAT_WINDOW} ending at that stop, oldest first, the stop
     *     itself last when it is a trip
     */
    public List<StopEvent> repeatsUpTo(StopEvent stop) {
        LocalDateTime from = stop.stopped().minusSeconds(REPEAT_WINDOW.seconds());
        return where(one -> one.kind().isATrip()
                && !one.stopped().isBefore(from)
                && !one.stopped().isAfter(stop.stopped()));
    }

    /**
     * Whether a stop is one too many, so a mechanic is called and the line walked before the
     * machine runs again whatever kind of stop it was.
     *
     * @param stop one of the stops in the log
     * @return true when it is the {@link #REPEAT_LIMIT}th or later trip inside the window
     */
    public boolean callsTheMechanic(StopEvent stop) {
        return stop.kind().isATrip() && repeatsUpTo(stop).size() >= REPEAT_LIMIT;
    }

    /**
     * The same log with one more stop in it.
     *
     * @param stop the stop
     * @return a new log in order; this one is unchanged
     */
    public StopLog withStop(StopEvent stop) {
        List<StopEvent> all = new ArrayList<>(stops);
        all.add(stop);
        return of(lift, all);
    }

    /**
     * How many stops are in the log.
     *
     * @return the count
     */
    public int size() {
        return stops.size();
    }

    private List<StopEvent> where(Predicate<StopEvent> test) {
        return stops.stream().filter(test).toList();
    }

    private Duration closedLength(Predicate<StopEvent> counted) {
        Duration total = Duration.ZERO;
        for (StopEvent stop : stops) {
            if (!stop.isOpen() && counted.test(stop)) {
                total = total.plus(stop.length());
            }
        }
        return total;
    }
}
