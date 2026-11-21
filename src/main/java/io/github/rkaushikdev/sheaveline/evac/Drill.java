package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One evacuation drill on one machine.
 *
 * <p>A drill proves two things and they are not the same thing. It proves the machine has a plan that
 * works, which is a property of the lift and lasts a year. And it proves a particular team can do the
 * work, which is a property of the people and lapses at the end of the season whoever else was there.
 *
 * @param lift the machine
 * @param on the day it was run
 * @param kind how much of the plan it exercised
 * @param teams the teams that took part, at least one, each once
 * @param carriersWorked how many carriers were actually worked
 * @param elapsed how long it took
 * @param observer who watched it and signed it off
 */
public record Drill(
        LiftCode lift,
        LocalDate on,
        DrillKind kind,
        List<String> teams,
        int carriersWorked,
        Duration elapsed,
        String observer) {

    /** How long a full line drill keeps the machine current. */
    public static final int FULL_DRILL_VALID_DAYS = 365;

    /** The fewest carriers a full line drill must work to count for the annual. */
    public static final int MINIMUM_FULL_CARRIERS = 4;

    /**
     * A drill.
     *
     * @throws EvacuationError when there are no teams or one is listed twice, nobody signed it off,
     *     the carrier count is negative, a drill on the line worked no carrier, or a tabletop drill
     *     worked one
     */
    public Drill {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(observer, "observer");
        teams = List.copyOf(teams);
        if (teams.isEmpty()) {
            throw new EvacuationError("a drill has teams in it");
        }
        if (new HashSet<>(teams).size() != teams.size()) {
            throw new EvacuationError("a team is counted once in a drill");
        }
        if (observer.strip().isEmpty()) {
            throw new EvacuationError("a drill is signed off by whoever watched it");
        }
        if (carriersWorked < 0) {
            throw new EvacuationError("a drill cannot work a negative number of carriers");
        }
        if (kind.putsPeopleOnTheLine() && carriersWorked == 0) {
            throw new EvacuationError("a " + kind.value() + " drill works at least one carrier");
        }
        if (!kind.putsPeopleOnTheLine() && carriersWorked > 0) {
            throw new EvacuationError("a tabletop drill does not touch a carrier");
        }
    }

    /**
     * A drill.
     *
     * @param lift the machine
     * @param on the day it was run
     * @param kind how much of the plan it exercised
     * @param teams the teams that took part
     * @param carriersWorked how many carriers were worked
     * @param elapsed how long it took
     * @param observer who signed it off
     * @return the drill
     * @throws EvacuationError when the record does not describe a drill that could have happened
     */
    public static Drill of(
            LiftCode lift,
            LocalDate on,
            DrillKind kind,
            List<String> teams,
            int carriersWorked,
            Duration elapsed,
            String observer) {
        return new Drill(lift, on, kind, teams, carriersWorked, elapsed, observer);
    }

    /**
     * A full line drill that worked enough carriers to mean anything.
     *
     * @return true when it counts for the machine's annual drill
     */
    public boolean countsForTheAnnual() {
        return kind.satisfiesTheAnnual() && carriersWorked >= MINIMUM_FULL_CARRIERS;
    }

    /**
     * The rate the drill achieved.
     *
     * @return the elapsed time over the carriers worked, rounded down to the second
     * @throws EvacuationError when nothing was worked
     */
    public Duration minutesPerCarrier() {
        if (carriersWorked == 0) {
            throw new EvacuationError("nothing was worked, so there is no rate");
        }
        return new Duration(Math.floorDiv(elapsed.seconds(), carriersWorked));
    }

    /**
     * Whether a team took part.
     *
     * @param team the team's name
     * @return true when it is listed
     */
    public boolean included(String team) {
        return teams.contains(team);
    }

    /**
     * The last day the drill keeps the machine current.
     *
     * @return a year after it was run
     */
    public LocalDate expires() {
        return on.plusDays(FULL_DRILL_VALID_DAYS);
    }

    /** Such as {@code JP full line on 2024-12-05, 8 carriers}. */
    @Override
    public String toString() {
        return lift + " " + kind.value() + " on " + on + ", " + carriersWorked + " carriers";
    }
}
