package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Every dressing on one rope, in the order they went on, and what it costs to be late.
 *
 * <p>A haul rope is lubricated from the inside. The dressing the mill puts on works its way out of
 * the core as the rope bends over the sheaves, and when it is gone the wires start working against
 * each other dry. That does not show up as a diameter reading for a long time and then it shows up as
 * broken wires all at once, which is the wrong order to find things out in.
 *
 * <p>The interval is measured in rope miles rather than in weeks, because a rope that ran three
 * hundred hours in a thin winter has not aged the way one that ran a thousand has, and the calendar
 * cannot tell the difference.
 *
 * <p>A dry rope wears faster, so the wear rate a forecast is drawn from is not a property of the rope,
 * it is a property of the rope and how it has been looked after. Overdue dressings scale the rate, and
 * the scaled rate goes back into the forecast through {@link Trend#forecast(HaulRope, List,
 * java.time.LocalDate, BigDecimal)}.
 *
 * @param rope the rope
 * @param dressings the applications, in order of travel
 * @param interval the rope travel between dressings
 */
public record DressingLog(HaulRope rope, List<Dressing> dressings, Length interval) {

    /** Twenty five thousand rope miles between dressings. */
    public static final Length DRESSING_INTERVAL = Length.ofFeet(Dressing.FEET_IN_A_MILE * 25_000);

    /** What each interval missed adds to the wear rate. */
    public static final BigDecimal WEAR_PENALTY_A_LATE_INTERVAL = new BigDecimal("0.35");

    /** The most a late dressing is allowed to multiply the wear rate by. */
    public static final BigDecimal WORST_PENALTY = new BigDecimal("2.5");

    /**
     * A log of one rope.
     *
     * @throws RopeworkError when the interval is not a distance, a dressing is on another rope, or the
     *     dressings are not in order of travel
     */
    public DressingLog {
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(interval, "interval");
        dressings = List.copyOf(dressings);
        if (interval.mils() <= 0) {
            throw new RopeworkError("a dressing interval is a distance");
        }
        for (Dressing one : dressings) {
            if (!one.ropeSerial().equals(rope.serial().toString())) {
                throw new RopeworkError("that dressing is on " + one.ropeSerial());
            }
        }
        for (int index = 1; index < dressings.size(); index++) {
            if (dressings
                            .get(index)
                            .travelAt()
                            .compareTo(dressings.get(index - 1).travelAt())
                    < 0) {
                throw new RopeworkError("a rope cannot be dressed at a distance it has not run");
            }
        }
    }

    /**
     * A log on the standard interval.
     *
     * @param rope the rope
     * @param dressings the applications, in order of travel
     */
    public DressingLog(HaulRope rope, List<Dressing> dressings) {
        this(rope, dressings, DRESSING_INTERVAL);
    }

    /**
     * A log on the standard interval with its dressings put in order.
     *
     * @param rope the rope
     * @param dressings the applications, in any order
     * @return the log
     */
    public static DressingLog of(HaulRope rope, List<Dressing> dressings) {
        return of(rope, dressings, DRESSING_INTERVAL);
    }

    /**
     * A log with its dressings put in order of travel, then of date.
     *
     * @param rope the rope
     * @param dressings the applications, in any order
     * @param interval the rope travel between dressings
     * @return the log
     */
    public static DressingLog of(HaulRope rope, List<Dressing> dressings, Length interval) {
        List<Dressing> ordered = dressings.stream()
                .sorted(Comparator.comparingLong(
                                (Dressing one) -> one.travelAt().mils())
                        .thenComparing(Dressing::on))
                .toList();
        return new DressingLog(rope, ordered, interval);
    }

    /**
     * The most recent dressing.
     *
     * @return the last application, or empty when the rope has never been dressed
     */
    public Optional<Dressing> last() {
        return dressings.isEmpty() ? Optional.empty() : Optional.of(dressings.get(dressings.size() - 1));
    }

    /**
     * How far the rope has run since the last dressing, or since it went on the line.
     *
     * @param travelNow the meter reading now
     * @return the travel since
     * @throws RopeworkError when the meter reads less than at the last dressing
     */
    public Length travelSince(Length travelNow) {
        Length run = travelNow.minus(last().map(Dressing::travelAt).orElse(Length.ZERO));
        if (run.mils() < 0) {
            throw new RopeworkError("the meter has gone backwards since the last dressing");
        }
        return run;
    }

    /**
     * Whether the rope is due a dressing.
     *
     * @param travelNow the meter reading now
     * @return true once a whole interval has run
     */
    public boolean isDue(Length travelNow) {
        return travelSince(travelNow).compareTo(interval) >= 0;
    }

    /**
     * How many intervals late the rope is, which is what the wear penalty is scaled on.
     *
     * @param travelNow the meter reading now
     * @return the travel past the interval as a number of intervals, zero when not late
     */
    public BigDecimal intervalsOverdue(Length travelNow) {
        long over = travelSince(travelNow).mils() - interval.mils();
        if (over <= 0) {
            return BigDecimal.ZERO;
        }
        return Decimals.divide(over, interval.mils());
    }

    /**
     * How much further the rope can run before it is due.
     *
     * @param travelNow the meter reading now
     * @return the travel left, zero once it is due
     */
    public Length travelUntilDue(Length travelNow) {
        long left = interval.mils() - travelSince(travelNow).mils();
        return new Length(Math.max(0, left));
    }

    /**
     * What a dry rope costs, as a multiplier on the wear rate.
     *
     * <p>One at the interval, rising with every interval missed, and capped. The cap is not physics, it
     * is an admission that nobody here has measured a rope that far past its dressing and a number
     * invented past the data is worse than a number that says it has stopped.
     *
     * @param travelNow the meter reading now
     * @return the multiplier, from one to {@link #WORST_PENALTY}
     */
    public BigDecimal wearPenalty(Length travelNow) {
        BigDecimal late = intervalsOverdue(travelNow);
        if (late.signum() <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal raised = BigDecimal.ONE.add(Decimals.multiply(late, WEAR_PENALTY_A_LATE_INTERVAL), Decimals.CONTEXT);
        return raised.compareTo(WORST_PENALTY) < 0 ? raised : WORST_PENALTY;
    }

    /**
     * Rope miles run since the last dressing.
     *
     * @param travelNow the meter reading now
     * @return the miles
     */
    public BigDecimal milesRunSince(Length travelNow) {
        return Decimals.divide(travelSince(travelNow).feet(), Dressing.FEET_IN_A_MILE);
    }

    /**
     * How many dressings are in the log.
     *
     * @return the count
     */
    public int size() {
        return dressings.size();
    }

    /** The last dressing as the sheet prints it, or that the rope has never been dressed. */
    @Override
    public String toString() {
        return last().map(Dressing::toString).orElseGet(() -> rope.serial() + " has never been dressed");
    }
}
