package io.github.rkaushikdev.sheaveline.evac;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every drill run on the hill, and the two different kinds of currency they buy.
 *
 * <p>A drill proves the machine has a plan that works, which lasts a year, and it proves the teams
 * that took part can do the work, which lapses at the end of the season. So a resort that ran one
 * splendid full line drill in November and then hired four new patrollers in January is not current,
 * and the register has to be able to say that without anybody having to notice it.
 *
 * @param drills the drills, in the order they were run
 */
public record DrillRegister(List<Drill> drills) {

    /** A register. */
    public DrillRegister {
        drills = List.copyOf(drills);
    }

    /** A register with nothing in it. */
    public DrillRegister() {
        this(List.of());
    }

    /**
     * A register with nothing in it.
     *
     * @return the empty register
     */
    public static DrillRegister of() {
        return new DrillRegister();
    }

    /**
     * A register with its drills put in the order they were run.
     *
     * @param drills the drills, in any order
     * @return the register, drills on the same day kept in the order given
     */
    public static DrillRegister of(List<Drill> drills) {
        return new DrillRegister(
                drills.stream().sorted(Comparator.comparing(Drill::on)).toList());
    }

    /**
     * The drills on one machine.
     *
     * @param lift the machine
     * @return its drills, in the order they were run
     */
    public List<Drill> onLift(LiftCode lift) {
        return drills.stream().filter(drill -> drill.lift().equals(lift)).toList();
    }

    /**
     * The latest drill on a machine that counts for the annual.
     *
     * @param lift the machine
     * @return that drill, or empty when none counts
     */
    public Optional<Drill> lastFull(LiftCode lift) {
        List<Drill> counting =
                onLift(lift).stream().filter(Drill::countsForTheAnnual).toList();
        return counting.isEmpty() ? Optional.empty() : Optional.of(counting.get(counting.size() - 1));
    }

    /**
     * Whether the machine's annual drill is current.
     *
     * @param lift the machine
     * @param on the day
     * @return true up to and including the day its last full drill expires
     */
    public boolean liftIsCurrent(LiftCode lift, LocalDate on) {
        return lastFull(lift).filter(last -> !on.isAfter(last.expires())).isPresent();
    }

    /**
     * When the machine's annual drill runs out.
     *
     * @param lift the machine
     * @return the expiry of its last full drill, or empty when it has none
     */
    public Optional<LocalDate> liftExpires(LiftCode lift) {
        return lastFull(lift).map(Drill::expires);
    }

    /**
     * Whether a team was in a drill on the machine this season.
     *
     * @param team the team's name
     * @param lift the machine
     * @param season the season
     * @return true when some drill on the machine inside the season included it
     */
    public boolean teamTookPart(String team, LiftCode lift, Season season) {
        return onLift(lift).stream().anyMatch(drill -> drill.included(team) && season.covers(drill.on()));
    }

    /**
     * Teams on the machine that have not been in a drill this season.
     *
     * @param lift the machine
     * @param season the season
     * @param expected the teams that work the machine
     * @return the names of those not yet drilled, in the order expected
     */
    public List<String> teamsShort(LiftCode lift, Season season, List<String> expected) {
        return expected.stream()
                .filter(name -> !teamTookPart(name, lift, season))
                .toList();
    }

    /**
     * Both kinds of currency at once, which is the question actually asked.
     *
     * @param lift the machine
     * @param on the day
     * @param season the season
     * @param expected the teams that work the machine
     * @return true when the machine's annual is current and every expected team has drilled this season
     */
    public boolean isCurrent(LiftCode lift, LocalDate on, Season season, List<String> expected) {
        return liftIsCurrent(lift, on) && teamsShort(lift, season, expected).isEmpty();
    }

    /**
     * The best rate the hill has actually achieved, which is what a plan is checked against.
     *
     * @param lift the machine
     * @return the shortest time per carrier of any drill that worked carriers
     * @throws EvacuationError when nothing has been worked on the machine
     */
    public Duration rateFor(LiftCode lift) {
        List<Duration> rates = onLift(lift).stream()
                .filter(drill -> drill.carriersWorked() > 0)
                .map(Drill::minutesPerCarrier)
                .toList();
        if (rates.isEmpty()) {
            throw new EvacuationError("nothing has been worked on " + lift);
        }
        return Ranking.lowest(rates, Comparator.naturalOrder());
    }

    /**
     * The register with one more drill.
     *
     * @param drill the drill
     * @return a new register, in order; this one is unchanged
     */
    public DrillRegister withDrill(Drill drill) {
        List<Drill> all = new ArrayList<>(drills);
        all.add(drill);
        return of(all);
    }

    /**
     * How many drills are on the register.
     *
     * @return the count
     */
    public int size() {
        return drills.size();
    }
}
