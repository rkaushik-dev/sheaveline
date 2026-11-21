package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One contiguous stretch of the line, and the team that walks it.
 *
 * <p>The team works every carrier on its beat from where it is parked, walking out to each one and
 * back, so a beat costs its walking as well as its rigging and lowering.
 *
 * @param team the team
 * @param stops the carriers on the beat, at least one, in the order they are worked
 * @param stagedAt where the team is parked
 * @param seatsFilled people in each carrier, at least one
 * @param travel how fast the team walks
 */
public record Beat(RescueTeam team, List<CarrierStop> stops, Length stagedAt, int seatsFilled, Speed travel) {

    /**
     * A beat.
     *
     * @throws EvacuationError when the beat has no carriers or nobody is on the line
     */
    public Beat {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(stagedAt, "stagedAt");
        Objects.requireNonNull(travel, "travel");
        stops = List.copyOf(stops);
        if (stops.isEmpty()) {
            throw new EvacuationError(team.name() + " has been given nothing to do");
        }
        if (seatsFilled < 1) {
            throw new EvacuationError("an evacuation with nobody on the line is not one");
        }
    }

    /**
     * A beat walked at the usual speed.
     *
     * @param team the team
     * @param stops the carriers on the beat
     * @param stagedAt where the team is parked
     * @param seatsFilled people in each carrier
     */
    public Beat(RescueTeam team, List<CarrierStop> stops, Length stagedAt, int seatsFilled) {
        this(team, stops, stagedAt, seatsFilled, EvacuationTiming.TRAVEL_SPEED);
    }

    /**
     * How this team's kit clears a carrier. Not the pool's best kit.
     *
     * @param stop the carrier
     * @return stepping off when it is low enough, a ladder when this team's ladder reaches, and the rope
     *     otherwise
     */
    public Method methodFor(CarrierStop stop) {
        if (!stop.isAbove(EvacuationPlan.STEP_OFF_HEIGHT)) {
            return Method.STEP_OFF;
        }
        return team.kit().needsARopeFor(stop.height()) ? Method.ROPE : Method.LADDER;
    }

    /**
     * How long one carrier keeps the team busy.
     *
     * @param stop the carrier
     * @return the walk from the staging point, the rigging and the lowering of everybody in it
     */
    public Duration workOn(CarrierStop stop) {
        Method method = methodFor(stop);
        Duration walk = travel.timeToCover(stop.station().minus(stagedAt).abs());
        return walk.plus(EvacuationTiming.SETUP.get(method))
                .plus(EvacuationTiming.PER_PERSON.get(method).scaled(seatsFilled));
    }

    /**
     * How long the whole beat keeps the team busy.
     *
     * @return the work on every carrier, added up
     */
    public Duration work() {
        Duration total = Duration.ZERO;
        for (CarrierStop stop : stops) {
            total = total.plus(workOn(stop));
        }
        return total;
    }

    /**
     * Whether the team's kit reaches every carrier on the beat.
     *
     * @return true when nothing is out of reach
     */
    public boolean reachesEverything() {
        return stops.stream().allMatch(team::canReach);
    }

    /**
     * The carriers on the beat the team cannot reach.
     *
     * @return those carriers, in beat order
     */
    public List<CarrierStop> outOfReach() {
        return stops.stream().filter(stop -> !team.canReach(stop)).toList();
    }

    /**
     * How far the team walks over the beat.
     *
     * @return the distance from the staging point to every carrier, added up
     */
    public Length walking() {
        Length total = Length.ZERO;
        for (CarrierStop stop : stops) {
            total = total.plus(stop.station().minus(stagedAt).abs());
        }
        return total;
    }

    /**
     * The beat as plan rows, numbered from one.
     *
     * @return one row per carrier, in beat order
     */
    public List<PlanStep> steps() {
        return steps(1);
    }

    /**
     * The beat as plan rows.
     *
     * @param first the number of the first row
     * @return one row per carrier, in beat order, the team against every carrier that needs one
     */
    public List<PlanStep> steps(int first) {
        List<PlanStep> planned = new ArrayList<>();
        for (int offset = 0; offset < stops.size(); offset++) {
            CarrierStop stop = stops.get(offset);
            Method method = methodFor(stop);
            Optional<RescueTeam> who = method.needsATeam() ? Optional.of(team) : Optional.empty();
            planned.add(new PlanStep(first + offset, stop, method, who));
        }
        return List.copyOf(planned);
    }

    /** Such as {@code alpha at 1200 ft, 21 carriers, 48:20}. */
    @Override
    public String toString() {
        return team.name() + " at " + Decimals.fixed(stagedAt.feet(), 0) + " ft, " + stops.size() + " carriers, "
                + work().formatClock();
    }
}
