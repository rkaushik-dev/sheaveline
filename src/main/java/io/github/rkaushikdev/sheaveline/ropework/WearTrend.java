package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A straight line through the worst station on every walk in the file.
 *
 * <p>Two readings is the least that can define a line and it is not enough to trust one, so the
 * trend refuses outright under two, and the forecast drawn from it says how many walks it was drawn
 * from. A department reading a projection off two points should know that is what it is doing.
 *
 * @param rope the rope
 * @param points one point a walk, oldest first
 */
public record WearTrend(HaulRope rope, List<WearPoint> points) {

    /** The fewest walks a trend is drawn through. */
    public static final int LEAST_WALKS = 2;

    /**
     * A trend through enough walks.
     *
     * @throws RopeworkError when there are fewer than two points, or two of them share a day
     */
    public WearTrend {
        Objects.requireNonNull(rope, "rope");
        points = List.copyOf(points);
        if (points.size() < LEAST_WALKS) {
            throw new RopeworkError("a trend needs " + LEAST_WALKS + " walks and this rope has " + points.size());
        }
        if (points.stream().map(WearPoint::taken).distinct().count() != points.size()) {
            throw new RopeworkError("two walks on the same day cannot both be the trend");
        }
    }

    /**
     * The trend through a rope's walks, put in date order.
     *
     * @param rope the rope
     * @param surveys its walks, in any order
     * @return the trend
     * @throws RopeworkError when a walk is of another rope, or the walks cannot carry a trend
     */
    public static WearTrend of(HaulRope rope, List<WearSurvey> surveys) {
        for (WearSurvey survey : surveys) {
            if (!survey.rope().serial().equals(rope.serial())) {
                throw new RopeworkError("that walk is on " + survey.rope().serial());
            }
        }
        List<WearPoint> points = surveys.stream()
                .sorted(Comparator.comparing(WearSurvey::taken))
                .map(one -> new WearPoint(one.taken(), one.smallest().mean()))
                .toList();
        return new WearTrend(rope, points);
    }

    /**
     * The oldest walk.
     *
     * @return the first point
     */
    public WearPoint first() {
        return points.get(0);
    }

    /**
     * The most recent walk.
     *
     * @return the last point
     */
    public WearPoint last() {
        return points.get(points.size() - 1);
    }

    /**
     * How many walks the trend is drawn through.
     *
     * @return the count
     */
    public int walks() {
        return points.size();
    }

    /**
     * Days from the first walk to the last.
     *
     * @return whole days
     */
    public long daysCovered() {
        return ChronoUnit.DAYS.between(first().taken(), last().taken());
    }

    /**
     * How much diameter has gone since the first walk in the file.
     *
     * @return the loss, negative if the rope reads fatter now
     */
    public Length worn() {
        return first().smallest().minus(last().smallest());
    }

    /**
     * How much there is between where the rope is now and where it comes off.
     *
     * @return the last reading less the condemning diameter
     */
    public Length left() {
        return last().smallest().minus(rope.spec().condemningDiameter());
    }

    /**
     * The rate of wear.
     *
     * @return mils lost a day
     * @throws RopeworkError when every walk is on one day
     */
    public BigDecimal milsADay() {
        long covered = daysCovered();
        if (covered <= 0) {
            throw new RopeworkError("the walks in the file are all on one day");
        }
        return Decimals.divide(worn().mils(), covered);
    }

    /**
     * Whether the rope has measurably lost anything. A rope that has not cannot be projected.
     *
     * @return true when the worn length is positive
     */
    public boolean isWearing() {
        return worn().mils() > 0;
    }
}
