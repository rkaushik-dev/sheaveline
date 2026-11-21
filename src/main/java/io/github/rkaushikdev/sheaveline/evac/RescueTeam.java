package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * One rescue team, its people, its kit and where it is staged.
 *
 * <p>A team is three things at once and all three have to be true on the day. It is people, and the
 * people have to be current on their training or they are spectators. It is kit, and the kit reaches a
 * certain height and no further. And it is a place on the mountain, because a team staged at the base
 * is forty minutes from the top of the line whatever else is true about it.
 *
 * <p>The kit question is the one that catches people out. A twenty foot ladder is not a twenty foot
 * reach, because somebody has to stand on it and work, and a rope system that reaches a hundred feet
 * down needs an anchor and a tower to hang off, which the middle of a span does not have.
 *
 * @param name what the team is called, unique on the hill
 * @param members the people on it, at least two and nobody twice
 * @param kit what it carries
 * @param stagedAt the station it waits at
 * @param trainedUntil the last day its training is current
 */
public record RescueTeam(String name, List<String> members, RescueKit kit, Length stagedAt, LocalDate trainedUntil) {

    /**
     * A team.
     *
     * @throws EvacuationError when the name is blank, there are fewer than two people, or somebody is
     *     listed twice
     */
    public RescueTeam {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(stagedAt, "stagedAt");
        Objects.requireNonNull(trainedUntil, "trainedUntil");
        members = List.copyOf(members);
        if (name.strip().isEmpty()) {
            throw new EvacuationError("a team has a name");
        }
        if (members.size() < 2) {
            throw new EvacuationError(name + " works aloft and needs at least two people");
        }
        if (new HashSet<>(members).size() != members.size()) {
            throw new EvacuationError(name + " has somebody counted twice");
        }
    }

    /**
     * A team.
     *
     * @param name what the team is called
     * @param members the people on it
     * @param kit what it carries
     * @param stagedAt the station it waits at
     * @param trainedUntil the last day its training is current
     * @return the team
     * @throws EvacuationError when the name is blank, there are fewer than two people, or somebody is
     *     listed twice
     */
    public static RescueTeam of(
            String name, List<String> members, RescueKit kit, Length stagedAt, LocalDate trainedUntil) {
        return new RescueTeam(name, members, kit, stagedAt, trainedUntil);
    }

    /**
     * How many people are on the team.
     *
     * @return the count
     */
    public int size() {
        return members.size();
    }

    /**
     * Whether the team's training is current on a day.
     *
     * @param on the day
     * @return true up to and including the last trained day
     */
    public boolean isCurrent(LocalDate on) {
        return !on.isAfter(trainedUntil);
    }

    /**
     * Whether the team carries enough kit for everybody on it.
     *
     * @return true when the kit is complete for the team's size
     */
    public boolean isEquipped() {
        return kit.isComplete(size());
    }

    /**
     * Whether the team's kit reaches a carrier.
     *
     * @param stop the carrier
     * @return true when its height is within the kit's reach
     */
    public boolean canReach(CarrierStop stop) {
        return kit.reaches(stop.height());
    }

    /**
     * Whether the team may take a carrier on a day.
     *
     * @param stop the carrier
     * @param on the day
     * @return true when the team is current, equipped and can reach it
     */
    public boolean mayWork(CarrierStop stop, LocalDate on) {
        return isCurrent(on) && isEquipped() && canReach(stop);
    }

    /**
     * Why a team cannot take a carrier, which is what the plan has to say.
     *
     * @param stop the carrier
     * @param on the day
     * @return every reason, in the order training, kit, reach; empty when the team can do the job
     */
    public List<String> refusals(CarrierStop stop, LocalDate on) {
        List<String> reasons = new ArrayList<>();
        if (!isCurrent(on)) {
            reasons.add(name + " training lapsed " + trainedUntil);
        }
        if (!isEquipped()) {
            reasons.add(name + " kit is short");
        }
        if (!canReach(stop)) {
            reasons.add(name + " reaches " + Decimals.fixed(kit.reach().feet(), 0) + " ft, carrier " + stop.number()
                    + " is at " + Decimals.fixed(stop.height().feet(), 0) + " ft");
        }
        return List.copyOf(reasons);
    }

    /**
     * How far the team has to walk to a station.
     *
     * @param station the station
     * @return the distance either way
     */
    public Length distanceTo(Length station) {
        return station.minus(stagedAt).abs();
    }

    /** Such as {@code alpha, 3 up, reach 120 ft, 2 seats}. */
    @Override
    public String toString() {
        return name + ", " + size() + " up, " + kit;
    }
}
