package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * When the rope comes off, and whether that leaves time to buy the next one.
 *
 * <p>A verdict answers whether the rope may run today. It is the wrong question in September, because
 * a haul rope is eighteen weeks from the mill and the purchase order has to go in before anybody can
 * see the wear that will justify it. What the department needs in September is when the rope is
 * going to reach the condemning diameter, and the only honest source for that is the walks already in
 * the file.
 *
 * <p>The penalty is how a dry rope gets into the arithmetic. A rope past its dressing wears faster
 * than the file says it has been wearing, and projecting the past rate forward on a rope nobody has
 * lubricated since March is how a department gets surprised in January. One means it has been looked
 * after.
 *
 * @param trend the line through the walks
 * @param on the day the forecast is read
 * @param leadTime days from purchase order to a rope on site
 * @param penalty the multiplier on the wear rate, one for a rope that has been looked after
 */
public record Retirement(WearTrend trend, LocalDate on, int leadTime, BigDecimal penalty) {

    /** Days from purchase order to a new haul rope on site. */
    public static final int LEAD_TIME_DAYS = 126;

    /**
     * A forecast.
     *
     * @throws RopeworkError when the lead time is negative or the penalty is under one
     */
    public Retirement {
        Objects.requireNonNull(trend, "trend");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(penalty, "penalty");
        if (leadTime < 0) {
            throw new RopeworkError("a lead time is not negative");
        }
        if (penalty.compareTo(BigDecimal.ONE) < 0) {
            throw new RopeworkError("looking after a rope does not make it wear more slowly");
        }
    }

    /**
     * A forecast for a rope that has been looked after, with the usual lead time.
     *
     * @param trend the line through the walks
     * @param on the day the forecast is read
     */
    public Retirement(WearTrend trend, LocalDate on) {
        this(trend, on, LEAD_TIME_DAYS, BigDecimal.ONE);
    }

    /**
     * Whether the file carries a line at all.
     *
     * @return true when the rope is wearing and the walks span more than one day
     */
    public boolean isProjectable() {
        return trend.isWearing() && trend.daysCovered() > 0;
    }

    /**
     * Days from the last walk until the rope reaches the condemning diameter.
     *
     * @return whole days, zero when it is already there, or empty when the file will not carry a line
     */
    public Optional<Long> daysLeft() {
        if (!isProjectable()) {
            return Optional.empty();
        }
        Length left = trend.left();
        if (left.mils() <= 0) {
            return Optional.of(0L);
        }
        BigDecimal rate = Decimals.multiply(trend.milsADay(), penalty);
        return Optional.of(Decimals.divide(Decimals.of(left.mils()), rate).longValue());
    }

    /**
     * The day the rope reaches the condemning diameter.
     *
     * @return the day, or empty when the file will not carry a line
     */
    public Optional<LocalDate> comesOff() {
        return daysLeft().map(remaining -> trend.last().taken().plusDays(remaining));
    }

    /**
     * The last day a purchase order can go in and still beat the wear.
     *
     * @return the day, or empty when the file will not carry a line
     */
    public Optional<LocalDate> orderBy() {
        return comesOff().map(wanted -> wanted.minusDays(leadTime));
    }

    /**
     * Whether the order should already have gone in.
     *
     * @return true on or after the order by day
     */
    public boolean isOverdueToOrder() {
        return orderBy().map(wanted -> !on.isBefore(wanted)).orElse(false);
    }

    /**
     * Whether the rope reaches a day, which is what a season plan asks.
     *
     * @param until the day
     * @return whether it comes off after that day, or empty when the file will not carry a line
     */
    public Optional<Boolean> survives(LocalDate until) {
        return comesOff().map(wanted -> wanted.isAfter(until));
    }

    /**
     * Whether a late dressing is speeding the forecast up.
     *
     * @return true when the penalty is over one
     */
    public boolean isPenalised() {
        return penalty.compareTo(BigDecimal.ONE) > 0;
    }

    /**
     * How much of a line the file actually carries, said in one word.
     *
     * @return {@code settled} for five walks over a year or more, {@code provisional} for three or
     *     more, otherwise {@code two points}
     */
    public String confidence() {
        int walks = trend.walks();
        if (walks >= 5 && trend.daysCovered() >= 365) {
            return "settled";
        }
        if (walks >= 3) {
            return "provisional";
        }
        return "two points";
    }

    /** What the purchase order needs: the serial, the day it comes off and the day to order by. */
    @Override
    public String toString() {
        Optional<LocalDate> wanted = comesOff();
        if (wanted.isEmpty()) {
            return trend.rope().serial() + " is not wearing measurably yet";
        }
        return trend.rope().serial() + " reaches condemning diameter " + wanted.get() + ", order by "
                + orderBy().orElseThrow();
    }
}
