package io.github.rkaushikdev.sheaveline.capacity;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Objects;

/**
 * Both terminal constraints on one machine, read together.
 *
 * <p>Two things have to hold at once and they bind independently. The carriers have to arrive far
 * enough apart for the class, and each one has to sit at the gate long enough for the crew to fill
 * it.
 *
 * @param spacing the carriers on the line
 * @param gate the load point
 * @param liftClass the class whose interval floor applies
 */
public record TerminalCheck(LineSpacing spacing, LoadingGate gate, LiftClass liftClass) {

    /** A check, nothing left null. */
    public TerminalCheck {
        Objects.requireNonNull(spacing, "spacing");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(liftClass, "liftClass");
    }

    /**
     * Whether the carriers arrive far enough apart for the class.
     *
     * @return true when the interval meets the floor
     */
    public boolean intervalHolds() {
        return spacing.meets(liftClass);
    }

    /**
     * Whether the crew keeps up at the gate.
     *
     * @return true when the window is long enough
     */
    public boolean gateHolds() {
        return gate.keepsUp();
    }

    /**
     * Whether both constraints hold.
     *
     * @return true when the interval and the gate both hold
     */
    public boolean holds() {
        return intervalHolds() && gateHolds();
    }

    /**
     * Which constraint fails.
     *
     * @return {@code both}, {@code interval}, {@code gate} or {@code neither}
     */
    public String bindingConstraint() {
        if (!intervalHolds() && !gateHolds()) {
            return "both";
        }
        if (!intervalHolds()) {
            return "interval";
        }
        if (!gateHolds()) {
            return "gate";
        }
        return "neither";
    }

    /**
     * The fullest line that still satisfies whichever constraint binds first.
     *
     * @return no carriers at all when the gate cannot keep up, otherwise the most the class floor
     *     allows
     */
    public int mostCarriers() {
        if (!gateHolds()) {
            return 0;
        }
        return spacing.mostCarriersFor(liftClass);
    }

    /**
     * The interval floor in use.
     *
     * @return the class's minimum interval
     */
    public Duration floor() {
        return LineSpacing.MINIMUM_INTERVAL.get(liftClass);
    }

    /** Both numbers and what binds, such as {@code interval 0:06, window 0:30, neither binding}. */
    @Override
    public String toString() {
        return "interval " + spacing.interval().formatClock() + ", window "
                + gate.window().formatClock() + ", " + bindingConstraint() + " binding";
    }
}
