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
import java.util.stream.Stream;

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
 * which is the part that is actually logic. The four that hold a figure against a limit are {@link
 * Limit} rows, so the figure a hold reopens at sits beside the figure it holds at.
 *
 * @param lift the machine
 * @param observation the reading it is judged against
 */
public record WeatherHold(Lift lift, Observation observation) {

    /** The gust against the class's gust limit, reopening a margin under it. */
    public static final Limit<Speed> GUST = new Limit<>(
            HoldReason.GUST,
            decision -> decision.observation().wind().peak(),
            decision -> Optional.of(decision.policy().gust()),
            false,
            WeatherHold::lessTheMargin,
            (peak, limit) -> peak.formatMph() + " against a limit of " + limit.formatMph());

    /** The wind across the line against the class's crosswind limit, reopening a margin under it. */
    public static final Limit<Speed> CROSSWIND = new Limit<>(
            HoldReason.CROSSWIND,
            WeatherHold::crosswind,
            decision -> decision.policy().crosswind(),
            false,
            WeatherHold::lessTheMargin,
            (across, limit) -> across.formatMph() + " across the line");

    /** The wind chill against the class's floor. */
    public static final Limit<Temperature> CHILL = new Limit<>(
            HoldReason.CHILL,
            decision -> decision.observation().chill(),
            decision -> Optional.of(decision.policy().chillFloor()),
            true,
            floor -> floor,
            (chill, floor) -> chill.formatF(0) + " at the summit");

    /** What can be seen from the terminal against what the operator needs to see the line. */
    public static final Limit<Length> VISIBILITY = new Limit<>(
            HoldReason.VISIBILITY,
            decision -> decision.observation().visibility(),
            decision -> Optional.of(Policy.MINIMUM_VISIBILITY),
            true,
            wanted -> wanted,
            (seen, wanted) -> Decimals.fixed(seen.feet(), 0) + " ft at the terminal");

    private static final List<Limit<?>> LIMITS = List.of(GUST, CROSSWIND, CHILL, VISIBILITY);

    private static final List<Function<WeatherHold, Optional<Hold>>> RULES = List.of(
            GUST::check,
            CROSSWIND::check,
            CHILL::check,
            WeatherHold::icingHold,
            WeatherHold::lightningHold,
            VISIBILITY::check);

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
     * The wind readings close enough to the limit that the machine runs slow: inside the limit, so
     * nothing holds it, but not back to the figure a hold would reopen at.
     *
     * @return the gust and the crosswind that slow the machine, in that order, empty when neither
     *     does or when the machine is held anyway
     */
    public List<Hold> slowings() {
        if (isHeld()) {
            return List.of();
        }
        return Stream.of(GUST, CROSSWIND)
                .flatMap(one -> one.near(this).stream())
                .toList();
    }

    /**
     * Whether the machine runs, but slowed for the wind.
     *
     * @return true when it may run and something slows it
     */
    public boolean runsSlow() {
        return !slowings().isEmpty();
    }

    /**
     * The rope speed the machine is slowed to.
     *
     * @return {@link Policy#SLOW_RUNNING} of its design speed when it runs slow, otherwise empty
     */
    public Optional<Speed> slowSpeed() {
        if (!runsSlow()) {
            return Optional.empty();
        }
        return Optional.of(lift.designSpeed().scaled(Policy.SLOW_RUNNING));
    }

    /**
     * Whether a hold for one reason can come off on this reading. A wind hold waits for the wind to
     * come back {@link Policy#WIND_REOPEN_MARGIN} inside the limit rather than to the limit itself.
     *
     * @param reason the reason the machine is held
     * @return true when this reading no longer gives the reason and is back to the reopening figure
     */
    public boolean clears(HoldReason reason) {
        if (reasons().contains(reason)) {
            return false;
        }
        return LIMITS.stream().filter(one -> one.reason() == reason).allMatch(one -> one.clears(this));
    }

    /**
     * Whether a hold runs on a clock rather than on the next reading, as a lightning hold does.
     *
     * @return true when any reason does not clear on a reading
     */
    public boolean needsATimer() {
        return reasons().stream().anyMatch(reason -> !reason.clearsOnReading());
    }

    /** Such as {@code JP clear}, {@code JP slow: gust} or {@code JP held: gust, crosswind}. */
    @Override
    public String toString() {
        List<Hold> named = isHeld() ? holds() : slowings();
        String state = isHeld() ? " held: " : " slow: ";
        if (named.isEmpty()) {
            return lift.code() + " clear";
        }
        return lift.code()
                + state
                + named.stream().map(hold -> hold.reason().value()).collect(Collectors.joining(", "));
    }

    private Optional<Hold> icingHold() {
        if (!policy().holdsOnIce() || !observation.icing()) {
            return Optional.empty();
        }
        String cause = observation.rime() || observation.precipitation().buildsIce()
                ? "ice on the grips"
                : "freezing fog riming the grips";
        return Optional.of(new Hold(HoldReason.ICING, cause));
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

    private static Speed lessTheMargin(Speed limit) {
        return Speed.ofMilsPerMinute(Math.max(0, limit.milsPerMinute() - Policy.WIND_REOPEN_MARGIN.milsPerMinute()));
    }
}
