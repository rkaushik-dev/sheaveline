package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
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
