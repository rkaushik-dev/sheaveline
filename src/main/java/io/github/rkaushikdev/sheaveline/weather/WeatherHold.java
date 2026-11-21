package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Whether the weather stops a lift, and which weather it was: one machine read against one
 * observation.
 *
 * <p>The answer is never just yes. The operator on the phone wants to know what is holding the
 * machine, because wind and lightning behave completely differently: one of them is over when the
 * anemometer says so and the other is over thirty minutes after the last strike, whatever the
 * anemometer says.
 *
 * <p>The limits are in the {@link Policy} table, and what is here is the six questions themselves,
 * which is the part that is actually logic. Adding a seventh question is one entry in the list of
 * rules.
 *
 * @param lift the machine
 * @param observation the reading it is judged against
 */
public record WeatherHold(Lift lift, Observation observation) {

    private static final List<Function<WeatherHold, Optional<Hold>>> RULES = List.of(
            WeatherHold::gustHold,
            WeatherHold::crosswindHold,
            WeatherHold::chillHold,
            WeatherHold::icingHold,
            WeatherHold::lightningHold,
            WeatherHold::visibilityHold);

    /** A machine and a reading. */
    public WeatherHold {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(observation, "observation");
    }

    /**
     * The limits for the machine's class.
     *
     * @return its row of the policy table
     */
    public ClassPolicy policy() {
        return Policy.forClass(lift.liftClass());
    }

    /**
     * The strongest gust the machine runs in.
     *
     * @return the class's gust limit
     */
    public Speed gustLimit() {
        return policy().gust();
    }

    /**
     * The strongest wind across the line the machine runs in.
     *
     * @return the class's crosswind limit, or empty when the class is not asked about one
     */
    public Optional<Speed> crosswindLimit() {
        return policy().crosswind();
    }

    /**
     * Whether ice on the grips holds the machine.
     *
     * @return the class's rule
     */
    public boolean holdsOnIce() {
        return policy().holdsOnIce();
    }

    /**
     * How much of the peak wind is across the line, which is what the crosswind limit reads.
     *
     * @return the cross component of the gust, or of the sustained wind when there is no gust
     */
    public Speed crosswind() {
        return observation.wind().peakAcross(lift.bearing());
    }

    /**
     * Every reason the machine is held, in the order the rules ask.
     *
     * @return the holds, empty when the machine may run
     */
    public List<Hold> holds() {
        return RULES.stream().flatMap(rule -> rule.apply(this).stream()).toList();
    }

    /**
     * Whether anything holds the machine.
     *
     * @return true when there is at least one hold
     */
    public boolean isHeld() {
        return !holds().isEmpty();
    }

    /**
     * Whether nothing holds the machine.
     *
     * @return true when there are no holds
     */
    public boolean mayRun() {
        return !isHeld();
    }

    /**
     * What is holding the machine.
     *
     * @return the reasons, in declaration order
     */
    public Set<HoldReason> reasons() {
        Set<HoldReason> found = EnumSet.noneOf(HoldReason.class);
        holds().forEach(hold -> found.add(hold.reason()));
        return Collections.unmodifiableSet(found);
    }

    /**
     * Whether a hold runs on a clock rather than on the next reading, as a lightning hold does.
     *
     * @return true when any reason does not clear on a reading
     */
    public boolean needsATimer() {
        return reasons().stream().anyMatch(reason -> !reason.clearsOnReading());
    }

    /** Such as {@code JP clear} or {@code JP held: gust, crosswind}. */
    @Override
    public String toString() {
        if (mayRun()) {
            return lift.code() + " clear";
        }
        return lift.code() + " held: "
                + holds().stream().map(hold -> hold.reason().value()).collect(Collectors.joining(", "));
    }

    private Optional<Hold> gustHold() {
        Speed peak = observation.wind().peak();
        Speed limit = policy().gust();
        if (peak.compareTo(limit) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Hold(HoldReason.GUST, peak.formatMph() + " against a limit of " + limit.formatMph()));
    }

    private Optional<Hold> crosswindHold() {
        Optional<Speed> limit = policy().crosswind();
        if (limit.isEmpty()) {
            return Optional.empty();
        }
        Speed across = crosswind();
        if (across.compareTo(limit.get()) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Hold(HoldReason.CROSSWIND, across.formatMph() + " across the line"));
    }

    private Optional<Hold> chillHold() {
        Temperature chill = observation.chill();
        if (chill.compareTo(policy().chillFloor()) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new Hold(HoldReason.CHILL, chill.formatF(0) + " at the summit"));
    }

    private Optional<Hold> icingHold() {
        if (!policy().holdsOnIce() || !observation.icing()) {
            return Optional.empty();
        }
        return Optional.of(new Hold(HoldReason.ICING, "ice on the grips"));
    }

    private Optional<Hold> lightningHold() {
        Optional<Length> struck = observation.lightningWithin();
        if (struck.isEmpty() || struck.get().compareTo(Policy.LIGHTNING_RADIUS) > 0) {
            return Optional.empty();
        }
        return Optional.of(new Hold(
                HoldReason.LIGHTNING,
                "strike within " + Decimals.fixed(struck.get().feet(), 0) + " ft"));
    }

    private Optional<Hold> visibilityHold() {
        Length seen = observation.visibility();
        if (seen.compareTo(Policy.MINIMUM_VISIBILITY) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new Hold(HoldReason.VISIBILITY, Decimals.fixed(seen.feet(), 0) + " ft at the terminal"));
    }
}
