package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.util.Objects;

/**
 * How long one row of the plan takes, split so it can be argued with.
 *
 * @param step the row
 * @param approach the team's walk out to the carrier
 * @param setup rigging the method
 * @param lowering bringing the people down
 */
public record StepTiming(PlanStep step, Duration approach, Duration setup, Duration lowering) {

    /** A costed row. */
    public StepTiming {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(approach, "approach");
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(lowering, "lowering");
    }

    /**
     * The whole row.
     *
     * @return the approach, the setup and the lowering added together
     */
    public Duration total() {
        return approach.plus(setup).plus(lowering);
    }

    /** Such as {@code carrier 9: 14:30}. */
    @Override
    public String toString() {
        return "carrier " + step.stop().number() + ": " + total().formatClock();
    }
}
