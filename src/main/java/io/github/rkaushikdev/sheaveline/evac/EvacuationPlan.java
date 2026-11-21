package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole line turned into a list of jobs with a team against each one, worked out for one stop
 * position on one day.
 *
 * <p>The plan is deliberately dull. Every carrier gets a row, the row says how the people are coming
 * down and who is doing it, and the rows are in the order they get worked. Nothing clever, because it
 * is read at four in the afternoon in a snowstorm by somebody who has been on their feet since seven.
 *
 * <p>Order is by exposure rather than by convenience. The chair ninety feet over a gully is worked
 * before the one eight feet over the cat track, even though the second one is quicker, because the
 * people in the first one are the ones who are going to be in trouble.
 *
 * @param profile the machine and the conditions
 * @param pool the teams on the hill
 * @param offset where the rope stopped
 * @param on the day
 */
public record EvacuationPlan(EvacuationProfile profile, TeamPool pool, Length offset, LocalDate on) {

    /** The highest a seat can be off the ground for its people to step off without a team. */
    public static final Length STEP_OFF_HEIGHT = Length.ofFeet(6);

    /** A plan. */
    public EvacuationPlan {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(on, "on");
    }

    /**
     * Every carrier on the line.
     *
     * @return the stops, in carrier order
     */
    public List<CarrierStop> stops() {
        return profile.stopsAtOffset(offset);
    }

    /**
     * How the people in a carrier come down: stepping off when it is low enough, a ladder when some
     * current team's ladder reaches it, and the rope otherwise.
     *
     * @param stop the carrier
     * @return the method
     */
    public Method methodFor(CarrierStop stop) {
        if (stop.height().compareTo(STEP_OFF_HEIGHT) <= 0) {
            return Method.STEP_OFF;
        }
        boolean ladders = pool.current(on).stream().anyMatch(team -> !team.kit().needsARopeFor(stop.height()));
        return ladders ? Method.LADDER : Method.ROPE;
    }

    /**
     * The carriers in the order they are worked.
     *
     * @return the highest first, the lower station first between equals
     */
    public List<CarrierStop> orderedStops() {
        List<CarrierStop> ordered = new ArrayList<>(stops());
        ordered.sort(
                Comparator.comparingLong((CarrierStop stop) -> -stop.height().mils())
                        .thenComparingLong(stop -> stop.station().mils()));
        return List.copyOf(ordered);
    }

    /**
     * The plan's rows: every carrier somebody can clear, each worked by the nearest able team.
     *
     * @return the rows in working order, numbered from one without gaps; a carrier nobody can work
     *     gets no row
     */
    public List<PlanStep> steps() {
        List<PlanStep> planned = new ArrayList<>();
        int order = 1;
        for (CarrierStop stop : orderedStops()) {
            Method method = methodFor(stop);
            if (!method.needsATeam()) {
                planned.add(new PlanStep(order, stop, method, Optional.empty()));
                order += 1;
                continue;
            }
            if (pool.able(stop, on).isEmpty()) {
                continue;
            }
            planned.add(new PlanStep(order, stop, method, pool.nearestAble(stop, on)));
            order += 1;
        }
        return List.copyOf(planned);
    }

    /**
     * Carriers nobody in the pool can work, which is what a plan is for finding.
     *
     * @return those carriers, in working order
     */
    public List<CarrierStop> unassigned() {
        return orderedStops().stream()
                .filter(stop ->
                        methodFor(stop).needsATeam() && pool.able(stop, on).isEmpty())
                .toList();
    }

    /**
     * Whether every carrier can be cleared.
     *
     * @return true when nothing is unassigned
     */
    public boolean isWorkable() {
        return unassigned().isEmpty();
    }

    /**
     * The rows each team works.
     *
     * @return the rows by team name, the teams in the order they first appear in the plan
     */
    public Map<String, List<PlanStep>> byTeam() {
        Map<String, List<PlanStep>> grouped = new LinkedHashMap<>();
        for (PlanStep step : steps()) {
            step.team().ifPresent(team -> grouped.computeIfAbsent(team.name(), name -> new ArrayList<>())
                    .add(step));
        }
        Map<String, List<PlanStep>> frozen = new LinkedHashMap<>();
        grouped.forEach((name, steps) -> frozen.put(name, List.copyOf(steps)));
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * The rows on the rope.
     *
     * @return those rows, in working order
     */
    public List<PlanStep> technicalSteps() {
        return steps().stream().filter(step -> step.method().isTechnical()).toList();
    }

    /**
     * The rows where people step off.
     *
     * @return those rows, in working order
     */
    public List<PlanStep> stepOffs() {
        return steps().stream().filter(step -> step.method() == Method.STEP_OFF).toList();
    }

    /**
     * How many people are on the line.
     *
     * @param seatsFilled people in each carrier
     * @return the carriers times the people in each
     * @throws EvacuationError when the count is negative
     */
    public int peopleOnTheLine(int seatsFilled) {
        if (seatsFilled < 0) {
            throw new EvacuationError("a carrier cannot hold fewer than nobody");
        }
        return stops().size() * seatsFilled;
    }

    /** Such as {@code 84 carriers, workable} or {@code 84 carriers, 12 unreachable}. */
    @Override
    public String toString() {
        String state = isWorkable() ? "workable" : unassigned().size() + " unreachable";
        return stops().size() + " carriers, " + state;
    }
}
