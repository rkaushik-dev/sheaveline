package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.schedule.Site;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One plan, costed: how long the evacuation takes, which is the number the board actually asks for.
 *
 * <p>The rule is simple to state and awkward to satisfy. Everybody has to be off the line before
 * dark, and the clock starts when the drive is declared dead rather than when the first team reaches
 * the first tower. So the answer has three parts: the time to get the teams out to where the work is,
 * the time to work each carrier, and the fact that teams work at the same time as each other rather
 * than one after the other.
 *
 * <p>Teams working in parallel is the part that makes this arithmetic rather than addition. Two teams
 * do not halve the time unless the work splits evenly, and it never does, because the awkward
 * carriers are all in the same two spans.
 *
 * @param plan the plan
 * @param seatsFilled people in each carrier, at least one
 * @param callOut how long from the drive being declared dead to the teams setting off
 * @param travel how fast the teams walk
 */
public record EvacuationTiming(EvacuationPlan plan, int seatsFilled, Duration callOut, Speed travel) {

    /** How long from the drive being declared dead to the teams setting off. */
    public static final Duration CALL_OUT = Duration.ofMinutes(15);

    /** How fast a team walks the line with its kit. */
    public static final Speed TRAVEL_SPEED = Speed.ofFeetPerMinute(180);

    /** Rigging time for each method, once per carrier. */
    public static final Map<Method, Duration> SETUP =
            table(Duration.ofMinutes(1), Duration.ofMinutes(3), Duration.ofMinutes(6));

    /** Lowering time for each method, for every person in the carrier. */
    public static final Map<Method, Duration> PER_PERSON =
            table(Duration.ofSeconds(20), Duration.ofMinutes(1), Duration.ofSeconds(150));

    /** The seats filled in each carrier unless told otherwise. */
    public static final int DEFAULT_SEATS_FILLED = 3;

    /**
     * A costed plan.
     *
     * @throws EvacuationError when nobody is on the line or the teams cannot walk
     */
    public EvacuationTiming {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(callOut, "callOut");
        Objects.requireNonNull(travel, "travel");
        if (seatsFilled < 1) {
            throw new EvacuationError("an evacuation with nobody on the line is not one");
        }
        if (travel.isStopped()) {
            throw new EvacuationError("teams have to be able to walk");
        }
    }

    /**
     * A costed plan with the usual call out and walking speed.
     *
     * @param plan the plan
     * @param seatsFilled people in each carrier
     */
    public EvacuationTiming(EvacuationPlan plan, int seatsFilled) {
        this(plan, seatsFilled, CALL_OUT, TRAVEL_SPEED);
    }

    /**
     * A costed plan with three in every carrier, the usual call out and walking speed.
     *
     * @param plan the plan
     */
    public EvacuationTiming(EvacuationPlan plan) {
        this(plan, DEFAULT_SEATS_FILLED);
    }

    private static Map<Method, Duration> table(Duration stepOff, Duration ladder, Duration rope) {
        Map<Method, Duration> table = new EnumMap<>(Method.class);
        table.put(Method.STEP_OFF, stepOff);
        table.put(Method.LADDER, ladder);
        table.put(Method.ROPE, rope);
        return Collections.unmodifiableMap(table);
    }

    /**
     * The total walking the teams do, which is what argues for a second staging.
     *
     * @param plan the plan
     * @return every team's walk to every carrier it works, added up
     */
    public static Length approachDistance(EvacuationPlan plan) {
        Length total = Length.ZERO;
        for (PlanStep step : plan.steps()) {
            if (step.team().isPresent()) {
                total = total.plus(step.team().get().distanceTo(step.stop().station()));
            }
        }
        return total;
    }

    /**
     * The walk out to a row's carrier.
     *
     * @param step the row
     * @return the team's walk from where it is staged, nothing when no team is needed
     */
    public Duration approachFor(PlanStep step) {
        return step.team()
                .map(team -> travel.timeToCover(team.distanceTo(step.stop().station())))
                .orElse(Duration.ZERO);
    }

    /**
     * One row, costed.
     *
     * @param step the row
     * @return the walk, the rigging and the lowering of everybody in the carrier
     */
    public StepTiming timeFor(PlanStep step) {
        return new StepTiming(
                step,
                approachFor(step),
                SETUP.get(step.method()),
                PER_PERSON.get(step.method()).scaled(seatsFilled));
    }

    /**
     * Every row, costed.
     *
     * @return the timings in working order
     */
    public List<StepTiming> timings() {
        return plan.steps().stream().map(this::timeFor).toList();
    }

    /**
     * How long each team is busy, which is what sets the finish.
     *
     * @return the work by team name, the rows nobody needs to work under {@code crew}, in the order
     *     each first appears in the plan
     */
    public Map<String, Duration> workByTeam() {
        Map<String, Duration> totals = new LinkedHashMap<>();
        for (StepTiming timing : timings()) {
            String name = timing.step().team().map(RescueTeam::name).orElse("crew");
            totals.merge(name, timing.total(), Duration::plus);
        }
        return Collections.unmodifiableMap(totals);
    }

    /**
     * The team with the most work.
     *
     * @return its name, the first in the plan on a tie
     * @throws EvacuationError when nothing in the plan takes any time
     */
    public String busiestTeam() {
        Map<String, Duration> work = workByTeam();
        if (work.isEmpty()) {
            throw new EvacuationError("nothing in this plan takes any time");
        }
        return Ranking.highest(new ArrayList<>(work.keySet()), Comparator.comparingLong(name -> work.get(name)
                .seconds()));
    }

    /**
     * Call out, then the longest single team's share of the work.
     *
     * @return the time to clear the line
     */
    public Duration totalTime() {
        Map<String, Duration> work = workByTeam();
        if (work.isEmpty()) {
            return callOut;
        }
        return callOut.plus(Ranking.highest(new ArrayList<>(work.values()), Comparator.naturalOrder()));
    }

    /**
     * What it would take with one team, which is what the board compares against.
     *
     * @return call out, then every row one after another
     */
    public Duration serialTime() {
        Duration total = callOut;
        for (StepTiming timing : timings()) {
            total = total.plus(timing.total());
        }
        return total;
    }

    /**
     * How much the extra teams actually buy, which is never the team count.
     *
     * @return the serial time over the parallel time
     * @throws EvacuationError when the evacuation takes no time at all
     */
    public BigDecimal speedUp() {
        long parallel = totalTime().seconds();
        if (parallel == 0) {
            throw new EvacuationError("an evacuation that takes no time is not one");
        }
        return Decimals.divide(serialTime().seconds(), parallel);
    }

    /**
     * When the line is clear.
     *
     * @param started when the drive was declared dead
     * @return the finish
     */
    public TimeOfDay finishesBy(TimeOfDay started) {
        return started.plus(totalTime());
    }

    /**
     * Whether everybody is off before sunset.
     *
     * @param site where the lift is
     * @param on the day
     * @param started when the drive was declared dead
     * @return true when the whole evacuation fits in the daylight
     */
    public boolean fitsInDaylight(Site site, LocalDate on, TimeOfDay started) {
        return site.fitsInDaylight(on, started, totalTime());
    }

    /**
     * The last moment the drive can be declared dead and everybody still be off before sunset.
     *
     * @param site where the lift is
     * @param on the day
     * @return the latest start
     */
    public TimeOfDay latestStart(Site site, LocalDate on) {
        return site.latestStart(on, totalTime());
    }

    /**
     * How many people are on the line.
     *
     * @return the carriers times the seats filled
     */
    public int people() {
        return plan.stops().size() * seatsFilled;
    }

    /**
     * The same plan with a different number in each carrier.
     *
     * @param changed people in each carrier
     * @return the new timing
     * @throws EvacuationError when nobody is on the line
     */
    public EvacuationTiming withLoad(int changed) {
        return new EvacuationTiming(plan, changed, callOut, travel);
    }

    /** Such as {@code 252 people, 1:42:10}. */
    @Override
    public String toString() {
        return people() + " people, " + totalTime().formatHms();
    }
}
