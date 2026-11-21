package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The whole line cut up, with every team parked where its run wants it.
 *
 * <p>The clock is not the total work. It is the busiest team's share of it, because the teams work at
 * the same time, so a plan is as good as its busiest beat.
 *
 * @param profile the machine and the conditions
 * @param on the day
 * @param beats one beat per team staged, in order up the line
 * @param stranded carriers no team on the hill can reach
 * @param callOut how long from the drive being declared dead to the teams setting off
 */
public record StagingPlan(
        EvacuationProfile profile, LocalDate on, List<Beat> beats, List<CarrierStop> stranded, Duration callOut) {

    /** A staging plan. */
    public StagingPlan {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(callOut, "callOut");
        beats = List.copyOf(beats);
        stranded = List.copyOf(stranded);
    }

    /**
     * The teams staged.
     *
     * @return one per beat, in beat order
     */
    public List<RescueTeam> teams() {
        return beats.stream().map(Beat::team).toList();
    }

    /**
     * Every carrier somebody is working.
     *
     * @return the carriers, beat by beat
     */
    public List<CarrierStop> stops() {
        return beats.stream().flatMap(beat -> beat.stops().stream()).toList();
    }

    /**
     * The beat with the most work.
     *
     * @return that beat, the first on a tie
     * @throws EvacuationError when nobody is staged
     */
    public Beat busiest() {
        requireBeats();
        return Ranking.highest(
                beats, Comparator.comparingLong(beat -> beat.work().seconds()));
    }

    /**
     * The beat with the least work.
     *
     * @return that beat, the first on a tie
     * @throws EvacuationError when nobody is staged
     */
    public Beat quietest() {
        requireBeats();
        return Ranking.lowest(
                beats, Comparator.comparingLong(beat -> beat.work().seconds()));
    }

    /**
     * Call out, then the busiest team, because the rest finish sooner.
     *
     * @return the time to clear the line
     * @throws EvacuationError when nobody is staged
     */
    public Duration clearsIn() {
        requireBeats();
        return callOut.plus(busiest().work());
    }

    /**
     * What it would take with the beats worked one after another.
     *
     * @return call out, then every beat's work added up
     */
    public Duration serialTime() {
        Duration total = callOut;
        for (Beat beat : beats) {
            total = total.plus(beat.work());
        }
        return total;
    }

    /**
     * Busiest minus quietest, which is how badly the cutting went.
     *
     * @return the gap between the two
     * @throws EvacuationError when nobody is staged
     */
    public Duration spread() {
        return busiest().work().minus(quietest().work());
    }

    /**
     * Whether every carrier on the line can be cleared.
     *
     * @return true when nothing is stranded and every team reaches its whole beat
     */
    public boolean isWorkable() {
        return stranded.isEmpty() && beats.stream().allMatch(Beat::reachesEverything);
    }

    /**
     * What stops the plan clearing the line.
     *
     * @return the stranded carriers first, then each beat's carriers out of its team's reach
     */
    public List<String> refusals() {
        List<String> found = new ArrayList<>();
        for (CarrierStop stop : stranded) {
            found.add("carrier " + stop.number() + " at "
                    + Decimals.fixed(stop.height().feet(), 0) + " ft, nobody reaches it");
        }
        for (Beat beat : beats) {
            for (CarrierStop stop : beat.outOfReach()) {
                found.add(beat.team().name() + " cannot reach carrier " + stop.number());
            }
        }
        return List.copyOf(found);
    }

    /**
     * The plan as rows.
     *
     * @return every beat's rows in beat order, numbered from one without gaps
     */
    public List<PlanStep> steps() {
        List<PlanStep> planned = new ArrayList<>();
        for (Beat beat : beats) {
            planned.addAll(beat.steps(planned.size() + 1));
        }
        return List.copyOf(planned);
    }

    /**
     * How far the teams walk between them.
     *
     * @return every beat's walking, added up
     */
    public Length walking() {
        Length total = Length.ZERO;
        for (Beat beat : beats) {
            total = total.plus(beat.walking());
        }
        return total;
    }

    /**
     * How many teams are staged.
     *
     * @return the number of beats
     */
    public int size() {
        return beats.size();
    }

    private void requireBeats() {
        if (beats.isEmpty()) {
            throw new EvacuationError("nobody is on the hill");
        }
    }

    /**
     * Such as {@code 2 teams, 84 carriers, clear in 95:40}, with the stranded count after it when there
     * is one.
     */
    @Override
    public String toString() {
        if (beats.isEmpty()) {
            return "nobody can work this line, " + stranded.size() + " carriers stranded";
        }
        String note = stranded.isEmpty() ? "" : ", " + stranded.size() + " stranded";
        return beats.size() + " teams, " + stops().size() + " carriers, clear in "
                + clearsIn().formatClock() + note;
    }
}
