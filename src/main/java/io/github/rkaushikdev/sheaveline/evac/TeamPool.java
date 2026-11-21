package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every rescue team the department can put on the hill.
 *
 * @param teams the teams, at least one, no two with the same name
 */
public record TeamPool(List<RescueTeam> teams) {

    /**
     * A pool.
     *
     * @throws EvacuationError when there are no teams or two share a name
     */
    public TeamPool {
        teams = List.copyOf(teams);
        if (teams.isEmpty()) {
            throw new EvacuationError("a pool has teams in it");
        }
        Set<String> names = new HashSet<>();
        teams.forEach(team -> names.add(team.name()));
        if (names.size() != teams.size()) {
            throw new EvacuationError("two teams share a name");
        }
    }

    /**
     * A pool.
     *
     * @param teams the teams
     * @return the pool
     * @throws EvacuationError when there are no teams or two share a name
     */
    public static TeamPool of(List<RescueTeam> teams) {
        return new TeamPool(teams);
    }

    /**
     * The teams whose training is current.
     *
     * @param on the day
     * @return those teams, in pool order
     */
    public List<RescueTeam> current(LocalDate on) {
        return teams.stream().filter(team -> team.isCurrent(on)).toList();
    }

    /**
     * The teams whose training has lapsed.
     *
     * @param on the day
     * @return those teams, in pool order
     */
    public List<RescueTeam> lapsed(LocalDate on) {
        return teams.stream().filter(team -> !team.isCurrent(on)).toList();
    }

    /**
     * The people on the current teams.
     *
     * @param on the day
     * @return the count
     */
    public int people(LocalDate on) {
        return current(on).stream().mapToInt(RescueTeam::size).sum();
    }

    /**
     * The teams that may take a carrier.
     *
     * @param stop the carrier
     * @param on the day
     * @return those teams, in pool order
     */
    public List<RescueTeam> able(CarrierStop stop, LocalDate on) {
        return teams.stream().filter(team -> team.mayWork(stop, on)).toList();
    }

    /**
     * The able team with the least walking to a carrier.
     *
     * @param stop the carrier
     * @param on the day
     * @return that team, the first in pool order on a tie
     * @throws EvacuationError when no team can work the carrier, rather than a guess
     */
    public RescueTeam nearestAble(CarrierStop stop, LocalDate on) {
        List<RescueTeam> able = able(stop, on);
        if (able.isEmpty()) {
            throw new EvacuationError("no team can work carrier " + stop.number());
        }
        return Ranking.lowest(able, Comparator.comparingLong(team -> team.distanceTo(stop.station())
                .mils()));
    }

    /**
     * The furthest any team's kit reaches.
     *
     * @return the longest reach in the pool
     */
    public Length deepestReach() {
        return Ranking.highest(teams.stream().map(team -> team.kit().reach()).toList(), Comparator.naturalOrder());
    }

    /**
     * How many teams are in the pool.
     *
     * @return the count
     */
    public int size() {
        return teams.size();
    }
}
