package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * When ropes run out, worked out from the walks rather than from the calendar.
 *
 * <p>The projection is a straight line through the worst station's reading on every walk in the
 * file, run forward to the condemning diameter. Two walks is the least that can define a line, so a
 * forecast refuses outright under two and says in one word how much of a line it was drawn from.
 */
public final class Trend {

    private Trend() {}

    /**
     * The forecast for a rope that has been looked after, with the usual lead time.
     *
     * @param rope the rope
     * @param surveys its walks
     * @param on the day the forecast is read
     * @return the forecast
     * @throws RopeworkError when the walks cannot carry a trend
     */
    public static Retirement forecast(HaulRope rope, List<WearSurvey> surveys, LocalDate on) {
        return forecast(rope, surveys, on, Retirement.LEAD_TIME_DAYS, BigDecimal.ONE);
    }

    /**
     * The forecast for a rope that has been looked after.
     *
     * @param rope the rope
     * @param surveys its walks
     * @param on the day the forecast is read
     * @param leadTime days from purchase order to a rope on site
     * @return the forecast
     * @throws RopeworkError when the walks cannot carry a trend or the lead time is negative
     */
    public static Retirement forecast(HaulRope rope, List<WearSurvey> surveys, LocalDate on, int leadTime) {
        return forecast(rope, surveys, on, leadTime, BigDecimal.ONE);
    }

    /**
     * The forecast for a rope wearing faster than its file, with the usual lead time.
     *
     * @param rope the rope
     * @param surveys its walks
     * @param on the day the forecast is read
     * @param penalty the multiplier on the wear rate, such as a dressing log's wear penalty
     * @return the forecast
     * @throws RopeworkError when the walks cannot carry a trend or the penalty is under one
     */
    public static Retirement forecast(HaulRope rope, List<WearSurvey> surveys, LocalDate on, BigDecimal penalty) {
        return forecast(rope, surveys, on, Retirement.LEAD_TIME_DAYS, penalty);
    }

    /**
     * The forecast drawn through a rope's walks.
     *
     * @param rope the rope
     * @param surveys its walks
     * @param on the day the forecast is read
     * @param leadTime days from purchase order to a rope on site
     * @param penalty the multiplier on the wear rate
     * @return the forecast
     * @throws RopeworkError when the walks cannot carry a trend, the lead time is negative or the
     *     penalty is under one
     */
    public static Retirement forecast(
            HaulRope rope, List<WearSurvey> surveys, LocalDate on, int leadTime, BigDecimal penalty) {
        return new Retirement(WearTrend.of(rope, surveys), on, leadTime, penalty);
    }

    /**
     * Every rope whose replacement should already be on order, with the usual lead time.
     *
     * @param ropes each rope with its walks
     * @param on the day the question is asked
     * @return the forecasts, soonest off first
     */
    public static List<Retirement> orderable(List<RopeWalks> ropes, LocalDate on) {
        return orderable(ropes, on, Retirement.LEAD_TIME_DAYS);
    }

    /**
     * Every rope whose replacement should already be on order, soonest first. A rope with fewer than
     * two walks is left out rather than guessed at.
     *
     * @param ropes each rope with its walks
     * @param on the day the question is asked
     * @param leadTime days from purchase order to a rope on site
     * @return the forecasts, soonest off first
     */
    public static List<Retirement> orderable(List<RopeWalks> ropes, LocalDate on, int leadTime) {
        List<Retirement> found = new ArrayList<>();
        for (RopeWalks one : ropes) {
            if (one.walks().size() < WearTrend.LEAST_WALKS) {
                continue;
            }
            Retirement made = forecast(one.rope(), one.walks(), on, leadTime);
            if (made.isOverdueToOrder()) {
                found.add(made);
            }
        }
        return found.stream()
                .sorted(Comparator.comparing(one -> one.comesOff().orElse(LocalDate.MAX)))
                .toList();
    }

    /**
     * A rope and the walks on file for it.
     *
     * @param rope the rope
     * @param walks its wear surveys
     */
    public record RopeWalks(HaulRope rope, List<WearSurvey> walks) {

        /** A rope and its walks. */
        public RopeWalks {
            Objects.requireNonNull(rope, "rope");
            walks = List.copyOf(walks);
        }
    }
}
