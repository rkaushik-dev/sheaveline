package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One walk of the rope counting broken wires, the way the standard counts them.
 *
 * <p>A broken wire on its own means nothing. What matters is how many there are inside one lay
 * length, how many inside six, and whether any of them are down in the valley between strands rather
 * than on the crown. A valley break usually means the wire has been working against the wire beside
 * it for a long time, so one of those weighs about as much as a handful of crown breaks.
 *
 * @param rope the rope walked
 * @param taken the day it was walked
 * @param inspector who signed it
 * @param observations the stations where breaks were counted, in the order recorded
 */
public record BreakSurvey(HaulRope rope, LocalDate taken, String inspector, List<BreakObservation> observations) {

    /**
     * A survey that could have been taken.
     *
     * @throws RopeworkError when it is unsigned, predates the rope, or counts a station past the end
     *     of it
     */
    public BreakSurvey {
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(inspector, "inspector");
        observations = List.copyOf(observations);
        if (inspector.strip().isEmpty()) {
            throw new RopeworkError("a survey is signed by whoever took it");
        }
        if (taken.isBefore(rope.installed())) {
            throw new RopeworkError("a survey cannot predate the rope it is of");
        }
        for (BreakObservation seen : observations) {
            if (seen.station().compareTo(rope.length()) > 0) {
                throw new RopeworkError(
                        "station " + Decimals.fixed(seen.station().feet(), 0) + " is past the end");
            }
        }
    }

    /**
     * A walk that found nothing to count.
     *
     * @param rope the rope walked
     * @param taken the day it was walked
     * @param inspector who signed it
     * @return the survey
     */
    public static BreakSurvey of(HaulRope rope, LocalDate taken, String inspector) {
        return of(rope, taken, inspector, List.of());
    }

    /**
     * A walk with its counts.
     *
     * @param rope the rope walked
     * @param taken the day it was walked
     * @param inspector who signed it
     * @param observations the counts
     * @return the survey
     */
    public static BreakSurvey of(
            HaulRope rope, LocalDate taken, String inspector, List<BreakObservation> observations) {
        return new BreakSurvey(rope, taken, inspector, observations);
    }

    /**
     * The allowance for this rope's construction.
     *
     * @return the limits
     */
    public BreakLimits limits() {
        return BreakLimits.forSpec(rope.spec());
    }

    /**
     * Every break counted in one lay, across the walk.
     *
     * @return the total
     */
    public int totalBreaks() {
        return observations.stream().mapToInt(BreakObservation::inOneLay).sum();
    }

    /**
     * The worst station: the most valley breaks, then the most breaks in one lay.
     *
     * @return the station, the first recorded on a tie, or empty when nothing was counted
     */
    public Optional<BreakObservation> worst() {
        return observations.stream()
                .max(Comparator.comparingInt(BreakObservation::valleyBreaks)
                        .thenComparingInt(BreakObservation::inOneLay));
    }

    /**
     * The stations over the allowance.
     *
     * @return those stations, in the order recorded
     */
    public List<BreakObservation> exceedances() {
        BreakLimits limits = limits();
        return observations.stream().filter(seen -> seen.exceeds(limits)).toList();
    }

    /**
     * The stations over the watch level and still inside the allowance.
     *
     * @return those stations, in the order recorded
     */
    public List<BreakObservation> nearLimit() {
        BreakLimits watch = limits().watchLevel();
        Set<BreakObservation> exceeded = new HashSet<>(exceedances());
        return observations.stream()
                .filter(seen -> seen.exceeds(watch) && !exceeded.contains(seen))
                .toList();
    }

    /**
     * How much of the rope carried no counted breaks at all, taking a lay out for every station
     * marked.
     *
     * @return the clean length
     */
    public Length cleanLength() {
        long marked = observations.stream()
                .map(seen -> seen.station().mils())
                .distinct()
                .count();
        if (marked == 0) {
            return rope.length();
        }
        Length lay = rope.spec().layLength();
        return rope.length().minus(lay.scaled(marked));
    }

    /** Such as {@code FAT-2019-04412 breaks on 2025-01-15, 9 counted}. */
    @Override
    public String toString() {
        return rope.serial() + " breaks on " + taken + ", " + totalBreaks() + " counted";
    }
}
