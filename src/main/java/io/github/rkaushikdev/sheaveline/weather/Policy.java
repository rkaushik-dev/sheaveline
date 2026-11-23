package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The weather limits, one row a class.
 *
 * <p>The limits are a table rather than a chain of tests inside the hold engine. Every one of them
 * is a number somebody at the board or the manufacturer decided, they change without any code
 * changing around them, and the crew asks to see them written down about once a month. So they are
 * a table, and the engine reads it.
 */
public final class Policy {

    /** A strike this close holds every machine on the hill: five miles. */
    public static final Length LIGHTNING_RADIUS = Length.ofFeet(26400);

    /** Below this visibility at the terminal the operator cannot see the line. */
    public static final Length MINIMUM_VISIBILITY = Length.ofFeet(200);

    /**
     * How far inside its limit the wind has to come back before a wind hold comes off: five miles an
     * hour, so a gusty afternoon does not open and close the machine every reading.
     */
    public static final Speed WIND_REOPEN_MARGIN = Speed.ofMilesPerHour(5);

    /**
     * The share of its design speed a machine is slowed to while the wind is inside the reopening
     * margin and not past the limit: seven tenths, which steadies the carriers through the towers.
     */
    public static final BigDecimal SLOW_RUNNING = new BigDecimal("0.7");

    /** Every class's row, in class order. */
    public static final Map<LiftClass, ClassPolicy> TABLE = table();

    private Policy() {}

    /**
     * The rope speed one machine is slowed to for wind.
     *
     * @param lift the machine
     * @return {@link #SLOW_RUNNING} of its design speed
     */
    public static Speed slowedSpeed(Lift lift) {
        return lift.designSpeed().scaled(SLOW_RUNNING);
    }

    /**
     * The row for one class. Every class has one.
     *
     * @param liftClass the class
     * @return its limits
     */
    public static ClassPolicy forClass(LiftClass liftClass) {
        return TABLE.get(liftClass);
    }

    private static Map<LiftClass, ClassPolicy> table() {
        Map<LiftClass, ClassPolicy> rows = new EnumMap<>(LiftClass.class);
        rows.put(LiftClass.FIXED_CHAIR, ClassPolicy.of(40, 30, -25, false));
        rows.put(LiftClass.DETACHABLE_CHAIR, ClassPolicy.of(45, 32, -25, true));
        rows.put(LiftClass.PULSE_GONDOLA, ClassPolicy.of(50, 35, -35, false));
        rows.put(LiftClass.DETACHABLE_GONDOLA, ClassPolicy.of(50, 35, -35, true));
        rows.put(LiftClass.AERIAL_TRAM, ClassPolicy.of(55, 40, -35, false));
        rows.put(LiftClass.SURFACE_TOW, ClassPolicy.withoutCrosswind(50, -25, false));
        return Collections.unmodifiableMap(rows);
    }
}
