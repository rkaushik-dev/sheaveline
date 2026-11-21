package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Carriers spread evenly round a loop, running at a speed, and the interval that falls out of it.
 *
 * <p>There is one number the loading crew feels and it is the interval, the seconds between one
 * carrier and the next arriving at the gate. The interval is the spacing divided by the rope speed,
 * the spacing is the loop divided by the carriers, and the loop is fixed by the machine. That leaves
 * two dials. Take carriers off and the interval opens up. Slow the rope down and it opens up as well,
 * but slowing the rope costs capacity twice over because the ride gets longer too.
 *
 * <p>A detachable's carriers slow to a walk in the terminal, so the interval the crew loads at is not
 * the interval the carriers run at on the line, and the minimum the class will tolerate is set by the
 * terminal and not by the rope.
 *
 * @param loop the length of the rope loop
 * @param carriers how many carriers are on it
 * @param speed the rope speed
 */
public record LineSpacing(Length loop, int carriers, Speed speed) {

    /** The shortest interval each class of lift will tolerate between carriers. */
    public static final Map<LiftClass, Duration> MINIMUM_INTERVAL = minimumIntervals();

    /**
     * A loop with carriers on it and the rope running.
     *
     * @throws CapacityError when the loop has no length, there is no carrier, or the rope is stopped
     */
    public LineSpacing {
        Objects.requireNonNull(loop, "loop");
        Objects.requireNonNull(speed, "speed");
        if (loop.mils() <= 0) {
            throw new CapacityError("a loop has a length");
        }
        if (carriers < 1) {
            throw new CapacityError("a line carries at least one carrier");
        }
        if (speed.isStopped()) {
            throw new CapacityError("a stopped rope has no interval");
        }
    }

    /**
     * A lift's own loop and carriers at its design speed.
     *
     * @param lift the machine
     * @return the spacing
     */
    public static LineSpacing of(Lift lift) {
        return of(lift, lift.designSpeed());
    }

    /**
     * A lift's own loop and carriers at another speed.
     *
     * @param lift the machine
     * @param speed the rope speed to ask about
     * @return the spacing
     */
    public static LineSpacing of(Lift lift, Speed speed) {
        return new LineSpacing(lift.expectedRopeLength(), lift.carriersOnLine(), speed);
    }

    /**
     * The distance between one carrier and the next.
     *
     * @return the loop over the carriers, rounded down to the mil
     */
    public Length spacing() {
        return new Length(loop.mils() / carriers);
    }

    /**
     * The time between one carrier and the next.
     *
     * @return the spacing at the rope speed
     */
    public Duration interval() {
        return speed.timeToCover(spacing());
    }

    /**
     * How many carriers that spacing would put on the loop.
     *
     * @param spacing the spacing
     * @return the whole carriers that fit
     * @throws CapacityError when the spacing is not positive
     */
    public int carriersFor(Length spacing) {
        if (spacing.mils() <= 0) {
            throw new CapacityError("a spacing is a positive length");
        }
        return loop.ratio(spacing).toBigInteger().intValueExact();
    }

    /**
     * The spacing that gives that interval at this rope speed.
     *
     * @param interval the interval
     * @return the distance the rope covers in it
     * @throws CapacityError when the interval is zero
     */
    public Length spacingFor(Duration interval) {
        if (interval.isZero()) {
            throw new CapacityError("an interval of nothing has no spacing");
        }
        return speed.travel(interval);
    }

    /**
     * The same line at another rope speed.
     *
     * @param changed the speed
     * @return the new spacing
     */
    public LineSpacing atSpeed(Speed changed) {
        return new LineSpacing(loop, carriers, changed);
    }

    /**
     * The same line with another number of carriers.
     *
     * @param changed the carriers
     * @return the new spacing
     */
    public LineSpacing withCarriers(int changed) {
        return new LineSpacing(loop, changed, speed);
    }

    /**
     * Whether the interval is at least the class floor.
     *
     * @param liftClass the class
     * @return true at or above the floor
     */
    public boolean meets(LiftClass liftClass) {
        return interval().compareTo(MINIMUM_INTERVAL.get(liftClass)) >= 0;
    }

    /**
     * How far under the class floor the interval is.
     *
     * @param liftClass the class
     * @return the shortfall, zero when the floor is met
     */
    public Duration shortfall(LiftClass liftClass) {
        Duration floor = MINIMUM_INTERVAL.get(liftClass);
        if (interval().compareTo(floor) >= 0) {
            return Duration.ZERO;
        }
        return floor.minus(interval());
    }

    /**
     * The fullest the line may be run without dropping under the class floor.
     *
     * @param liftClass the class
     * @return the most carriers
     */
    public int mostCarriersFor(LiftClass liftClass) {
        Length room = spacingFor(MINIMUM_INTERVAL.get(liftClass));
        return carriersFor(room);
    }

    /** The three numbers the crew reads, such as {@code 100 carriers at 100 ft, 0:06 apart}. */
    @Override
    public String toString() {
        return carriers + " carriers at " + Decimals.fixed(spacing().feet(), 0) + " ft, "
                + interval().formatClock() + " apart";
    }

    private static Map<LiftClass, Duration> minimumIntervals() {
        Map<LiftClass, Duration> floors = new EnumMap<>(LiftClass.class);
        floors.put(LiftClass.FIXED_CHAIR, Duration.ofSeconds(9));
        floors.put(LiftClass.DETACHABLE_CHAIR, Duration.ofSeconds(6));
        floors.put(LiftClass.PULSE_GONDOLA, Duration.ofSeconds(30));
        floors.put(LiftClass.DETACHABLE_GONDOLA, Duration.ofSeconds(6));
        floors.put(LiftClass.AERIAL_TRAM, Duration.ofSeconds(300));
        floors.put(LiftClass.SURFACE_TOW, Duration.ofSeconds(5));
        return Collections.unmodifiableMap(floors);
    }
}
