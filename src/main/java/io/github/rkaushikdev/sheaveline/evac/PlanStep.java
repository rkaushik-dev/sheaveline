package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Objects;
import java.util.Optional;

/**
 * One carrier, how it is being cleared and who is clearing it.
 *
 * @param order where the row comes in the plan, counting from one
 * @param stop the carrier
 * @param method how its people come down
 * @param team the team doing it, empty when nobody is needed
 */
public record PlanStep(int order, CarrierStop stop, Method method, Optional<RescueTeam> team) {

    /**
     * A row of the plan.
     *
     * @throws EvacuationError when the order is under one, or there is a team exactly when the method
     *     needs none
     */
    public PlanStep {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(team, "team");
        if (order < 1) {
            throw new EvacuationError("plan steps are numbered from one");
        }
        if (method.needsATeam() && team.isEmpty()) {
            throw new EvacuationError("carrier " + stop.number() + " has no team against it");
        }
        if (!method.needsATeam() && team.isPresent()) {
            throw new EvacuationError("carrier " + stop.number() + " does not need a team");
        }
    }

    /**
     * A row worked by a team.
     *
     * @param order where the row comes in the plan
     * @param stop the carrier
     * @param method how its people come down
     * @param team the team doing it
     */
    public PlanStep(int order, CarrierStop stop, Method method, RescueTeam team) {
        this(order, stop, method, Optional.of(team));
    }

    /** Such as {@code 1. carrier 9 at 65 ft, rope, alpha}, or {@code crew} where no team is needed. */
    @Override
    public String toString() {
        String who = team.map(RescueTeam::name).orElse("crew");
        return order + ". carrier " + stop.number() + " at "
                + Decimals.fixed(stop.height().feet(), 0) + " ft, " + method.value() + ", " + who;
    }
}
