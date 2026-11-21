package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Torque;
import java.util.Objects;

/**
 * One brake on the drive, with the torque it is set to.
 *
 * @param kind where it acts
 * @param torque the torque it is set to
 * @param hydraulic whether it is released hydraulically; a backstop never is
 */
public record Brake(BrakeKind kind, Torque torque, boolean hydraulic) {

    /**
     * A brake that can stop something.
     *
     * @throws PlantError when it has no torque, or a backstop is hydraulic
     */
    public Brake {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(torque, "torque");
        if (torque.isSlack()) {
            throw new PlantError("a " + kind.value() + " brake with no torque is not a brake");
        }
        if (kind == BrakeKind.BACKSTOP && hydraulic) {
            throw new PlantError("a backstop is mechanical, not hydraulic");
        }
    }

    /**
     * A hydraulic brake.
     *
     * @param kind where it acts
     * @param torque the torque it is set to
     */
    public Brake(BrakeKind kind, Torque torque) {
        this(kind, torque, true);
    }

    /**
     * Whether the brake holds against a demand.
     *
     * @param demand the torque it has to hold
     * @return true when its setting is at least the demand
     */
    public boolean holds(Torque demand) {
        return torque.compareTo(demand) >= 0;
    }

    /** Such as {@code service brake at 900 lb ft}. */
    @Override
    public String toString() {
        return kind.value() + " brake at " + torque.formatPoundFeet();
    }
}
