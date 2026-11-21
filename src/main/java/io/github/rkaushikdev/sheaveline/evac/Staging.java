package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where the teams park, which is what actually sets the evacuation clock.
 *
 * <p>The evacuation plan hands each carrier to whichever able team is nearest. That is the right
 * answer for one carrier and the wrong answer for a line, because nearest is measured from wherever
 * the team happens to be standing, and on a quad with eighty odd carriers it will cheerfully give one
 * team two thirds of the work while another sits at the bottom terminal.
 *
 * <p>The clock is not the total work. It is the busiest team's share of it, because the teams work at
 * the same time. So the job here is to cut the line into as many runs as there are teams and put the
 * cuts where the worst run is as small as it can be, then park each team in the middle of its own run.
 *
 * <p>The cut positions and the staging points decide each other. Move a cut and both neighbouring
 * teams stage somewhere else, which changes their walking, which changes where the next cut wants to
 * be. That is why this is a search over the finish time rather than a rule about distance: pick a
 * finish, ask whether the line can be cut to meet it, and squeeze.
 */
public final class Staging {

    private Staging() {}

    /**
     * Halfway between the ends of the run, which is where the walking is least.
     *
     * @param stops the run
     * @return the station midway between its lowest and highest carrier, rounded down to the mil
     * @throws EvacuationError when the run is empty
     */
    public static Length stagingPoint(List<CarrierStop> stops) {
        if (stops.isEmpty()) {
            throw new EvacuationError("an empty run has no middle");
        }
        long lowest = Long.MAX_VALUE;
        long highest = Long.MIN_VALUE;
        for (CarrierStop stop : stops) {
            lowest = Math.min(lowest, stop.station().mils());
            highest = Math.max(highest, stop.station().mils());
        }
        return new Length(Math.floorDiv(lowest + highest, 2));
    }

    /**
     * Cut the line so the busiest team finishes as early as it can, with three in every carrier, the
     * usual walking speed and the usual call out.
     *
     * @param profile the machine and the conditions
     * @param pool the teams on the hill
     * @param offset where the rope stopped
     * @param on the day
     * @return the staging plan
     * @throws EvacuationError when no team on the hill is current and equipped
     */
    public static StagingPlan stage(EvacuationProfile profile, TeamPool pool, Length offset, LocalDate on) {
        return stage(profile, pool, offset, on, EvacuationTiming.DEFAULT_SEATS_FILLED);
    }

    /**
     * Cut the line so the busiest team finishes as early as it can, at the usual walking speed and
     * call out.
     *
     * @param profile the machine and the conditions
     * @param pool the teams on the hill
     * @param offset where the rope stopped
     * @param on the day
     * @param seatsFilled people in each carrier
     * @return the staging plan
     * @throws EvacuationError when no team on the hill is current and equipped, or nobody is on the
     *     line
     */
    public static StagingPlan stage(
            EvacuationProfile profile, TeamPool pool, Length offset, LocalDate on, int seatsFilled) {
        return stage(profile, pool, offset, on, seatsFilled, EvacuationTiming.TRAVEL_SPEED, EvacuationTiming.CALL_OUT);
    }

    /**
     * Cut the line so the busiest team finishes as early as it can.
     *
     * <p>Only the teams that are current and equipped are staged. Carriers none of them can reach are
     * stranded, and the rest are cut into contiguous runs along the line, one team to a run, each team
     * parked in the middle of its own.
     *
     * <p>The search is on the finish time rather than on distance. Distance is the thing that changes
     * when a cut moves, so asking where the cuts go is asking a question with no fixed answer. Asking
     * whether a given finish is reachable has one, and squeezing it is a binary search on somewhere
     * under three hundred steps. When even the longest reach cannot cut the line within one team's
     * whole share, nobody is staged and every carrier is reported stranded.
     *
     * @param profile the machine and the conditions
     * @param pool the teams on the hill
     * @param offset where the rope stopped
     * @param on the day
     * @param seatsFilled people in each carrier
     * @param travel how fast the teams walk
     * @param callOut how long from the drive being declared dead to the teams setting off
     * @return the staging plan
     * @throws EvacuationError when no team on the hill is current and equipped, or nobody is on the
     *     line
     */
    public static StagingPlan stage(
            EvacuationProfile profile,
            TeamPool pool,
            Length offset,
            LocalDate on,
            int seatsFilled,
            Speed travel,
            Duration callOut) {
        List<RescueTeam> teams =
                pool.current(on).stream().filter(RescueTeam::isEquipped).toList();
        if (teams.isEmpty()) {
            throw new EvacuationError("no team on the hill is fit to work on " + on);
        }
        List<CarrierStop> stops = new ArrayList<>(profile.stopsAtOffset(offset));
        stops.sort(Comparator.comparingLong(stop -> stop.station().mils()));
        List<CarrierStop> stranded = new ArrayList<>();
        List<CarrierStop> workable = new ArrayList<>();
        for (CarrierStop stop : stops) {
            if (teams.stream().anyMatch(one -> one.canReach(stop))) {
                workable.add(stop);
            } else {
                stranded.add(stop);
            }
        }
        if (workable.isEmpty()) {
            return new StagingPlan(profile, on, List.of(), stranded, callOut);
        }

        RescueTeam biggest = Ranking.highest(
                teams, Comparator.comparingLong(team -> team.kit().reach().mils()));
        Duration high = runWork(workable, biggest, seatsFilled, travel);
        Duration low = Duration.ZERO;
        Optional<List<Run>> first = cutWithin(workable, teams, high, seatsFilled, travel);
        if (first.isEmpty()) {
            return new StagingPlan(profile, on, List.of(), stops, callOut);
        }
        List<Run> found = first.get();
        while (low.seconds() < high.seconds()) {
            Duration middle = new Duration(Math.floorDiv(low.seconds() + high.seconds(), 2));
            Optional<List<Run>> trying = cutWithin(workable, teams, middle, seatsFilled, travel);
            if (trying.isEmpty()) {
                low = new Duration(middle.seconds() + 1);
            } else {
                found = trying.get();
                high = middle;
            }
        }
        List<Beat> beats = found.stream()
                .map(run -> new Beat(run.team(), run.stops(), stagingPoint(run.stops()), seatsFilled, travel))
                .toList();
        return new StagingPlan(profile, on, beats, stranded, callOut);
    }

    /**
     * The same cut, costed for a different load. A busy Saturday is not a Tuesday.
     *
     * @param plan the plan
     * @param seatsFilled people in each carrier
     * @return the same beats, teams and staging points with the new load
     * @throws EvacuationError when nobody is on the line
     */
    public static StagingPlan restage(StagingPlan plan, int seatsFilled) {
        List<Beat> beats = plan.beats().stream()
                .map(beat -> new Beat(beat.team(), beat.stops(), beat.stagedAt(), seatsFilled, beat.travel()))
                .toList();
        return new StagingPlan(plan.profile(), plan.on(), beats, plan.stranded(), plan.callOut());
    }

    private static Duration runWork(List<CarrierStop> stops, RescueTeam team, int seatsFilled, Speed travel) {
        return new Beat(team, stops, stagingPoint(stops), seatsFilled, travel).work();
    }

    /** The smallest kit that still covers the run, so the long ropes stay free. */
    private static Optional<RescueTeam> coveringTeam(List<CarrierStop> run, List<RescueTeam> free) {
        List<RescueTeam> able = free.stream()
                .filter(team -> run.stream().allMatch(team::canReach))
                .toList();
        if (able.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Ranking.lowest(
                able,
                Comparator.comparingLong((RescueTeam team) -> team.kit().reach().mils())
                        .thenComparing(RescueTeam::name)));
    }

    /**
     * Cut the line into runs nobody has to work for longer than the bound.
     *
     * <p>Greedy on a contiguous cut is exact for the length: a team that could take one more carrier
     * and does not only pushes that carrier onto somebody who is already at least as busy. The team
     * choice is not free though, so each run takes the smallest kit that covers it and the long ropes
     * stay available for the steep ground further up.
     */
    private static Optional<List<Run>> cutWithin(
            List<CarrierStop> ordered, List<RescueTeam> teams, Duration bound, int seatsFilled, Speed travel) {
        List<RescueTeam> free = new ArrayList<>(teams);
        List<Run> runs = new ArrayList<>();
        int index = 0;
        while (index < ordered.size()) {
            if (free.isEmpty()) {
                return Optional.empty();
            }
            Run best = null;
            int taken = 0;
            while (index + taken < ordered.size()) {
                List<CarrierStop> trying = List.copyOf(ordered.subList(index, index + taken + 1));
                Optional<RescueTeam> team = coveringTeam(trying, free);
                if (team.isEmpty()) {
                    break;
                }
                if (runWork(trying, team.get(), seatsFilled, travel).compareTo(bound) > 0) {
                    break;
                }
                best = new Run(team.get(), trying);
                taken += 1;
            }
            if (best == null) {
                return Optional.empty();
            }
            free.remove(best.team());
            runs.add(best);
            index += best.stops().size();
        }
        return Optional.of(List.copyOf(runs));
    }

    /** A team and the contiguous carriers it is given. */
    private record Run(RescueTeam team, List<CarrierStop> stops) {}
}
