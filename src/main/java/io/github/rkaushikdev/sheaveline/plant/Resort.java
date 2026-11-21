package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.schedule.Season;
import io.github.rkaushikdev.sheaveline.schedule.Site;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * One mountain, its lifts, where it is and when it runs: the registry the whole hill is asked
 * about.
 *
 * <p>The department is not asked about one lift very often. It is asked how many seats the mountain
 * turns in an hour, which machines are due for a five year inspection, and whether the rope on a
 * machine has been anywhere else. So the registry keeps the answers that only make sense across the
 * whole hill, and refuses the mistakes that only show up across it, such as the same rope serial
 * recorded on two machines. Nothing in it is edited in place.
 *
 * @param name the resort's name
 * @param site where it is
 * @param season when it runs
 * @param lifts its lifts
 */
public record Resort(String name, Site site, Season season, List<Lift> lifts) implements Iterable<Lift> {

    /**
     * A registry that holds together.
     *
     * @throws PlantError when it has no name or no lifts, two lifts share a code, or one rope serial
     *     is on two machines
     */
    public Resort {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(season, "season");
        lifts = List.copyOf(lifts);
        if (name.strip().isEmpty()) {
            throw new PlantError("a resort has a name");
        }
        if (lifts.isEmpty()) {
            throw new PlantError(name + " has no lifts");
        }
        if (lifts.stream().map(Lift::code).distinct().count() != lifts.size()) {
            throw new PlantError("two lifts share a code");
        }
        if (lifts.stream().map(lift -> lift.rope().serial()).distinct().count() != lifts.size()) {
            throw new PlantError("one rope serial is recorded on two machines");
        }
    }

    /**
     * A registry with its lifts in code order.
     *
     * @param name the resort's name
     * @param site where it is
     * @param season when it runs
     * @param lifts its lifts, in any order
     * @return the registry
     */
    public static Resort of(String name, Site site, Season season, List<Lift> lifts) {
        List<Lift> ordered = lifts.stream()
                .sorted(Comparator.comparing(lift -> lift.code().value()))
                .toList();
        return new Resort(name, site, season, ordered);
    }

    /**
     * How many lifts there are.
     *
     * @return the count
     */
    public int size() {
        return lifts.size();
    }

    @Override
    public Iterator<Lift> iterator() {
        return lifts.iterator();
    }

    /**
     * Whether a lift with that code is on the hill.
     *
     * @param code the code
     * @return true when there is one
     */
    public boolean contains(LiftCode code) {
        return lifts.stream().anyMatch(lift -> lift.code().equals(code));
    }

    /**
     * A lift by code.
     *
     * @param code the code
     * @return the lift
     * @throws PlantError when the hill has no such lift
     */
    public Lift lift(LiftCode code) {
        return lifts.stream()
                .filter(machine -> machine.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new PlantError(name + " has no lift " + code));
    }

    /**
     * A lift by code as somebody typed it.
     *
     * @param code the code, in any case
     * @return the lift
     * @throws PlantError when the hill has no such lift
     */
    public Lift lift(String code) {
        return lift(LiftCode.parse(code));
    }

    /**
     * Every lift's code.
     *
     * @return the codes in order
     */
    public List<LiftCode> codes() {
        return lifts.stream().map(Lift::code).toList();
    }

    /**
     * The lifts filed under a class.
     *
     * @param liftClass the class
     * @return those lifts in code order
     */
    public List<Lift> ofClass(LiftClass liftClass) {
        return where(lift -> lift.liftClass() == liftClass);
    }

    /**
     * The machines in the air.
     *
     * @return those lifts in code order
     */
    public List<Lift> aerial() {
        return where(lift -> lift.liftClass().isAerial());
    }

    /**
     * The surface lifts.
     *
     * @return those lifts in code order
     */
    public List<Lift> surface() {
        return where(lift -> !lift.liftClass().isAerial());
    }

    /**
     * The detachables.
     *
     * @return those lifts in code order
     */
    public List<Lift> detachables() {
        return where(lift -> lift.liftClass().isDetachable());
    }

    /**
     * Seats on the line across the hill.
     *
     * @return the total
     */
    public int seatsOnLine() {
        return lifts.stream().mapToInt(Lift::seatsOnLine).sum();
    }

    /**
     * Carriers on the line across the hill.
     *
     * @return the total
     */
    public int carriersOnLine() {
        return lifts.stream().mapToInt(Lift::carriersOnLine).sum();
    }

    /**
     * Towers across the hill.
     *
     * @return the total
     */
    public int towerCount() {
        return lifts.stream().mapToInt(lift -> lift.towers().size()).sum();
    }

    /**
     * Sheaves across the hill.
     *
     * @return the total
     */
    public int sheaveCount() {
        return lifts.stream().mapToInt(lift -> lift.towers().sheaveCount()).sum();
    }

    /**
     * Every rope serial on the hill.
     *
     * @return the serials
     */
    public Set<RopeSerial> ropeSerials() {
        return lifts.stream().map(lift -> lift.rope().serial()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The machine commissioned first, the first in code order on a tie.
     *
     * @return the oldest lift
     */
    public Lift oldest() {
        Lift found = lifts.get(0);
        for (Lift lift : lifts) {
            if (lift.commissioned().isBefore(found.commissioned())) {
                found = lift;
            }
        }
        return found;
    }

    /**
     * The machine commissioned last, the first in code order on a tie.
     *
     * @return the newest lift
     */
    public Lift newest() {
        Lift found = lifts.get(0);
        for (Lift lift : lifts) {
            if (lift.commissioned().isAfter(found.commissioned())) {
                found = lift;
            }
        }
        return found;
    }

    /**
     * The machines built before a date.
     *
     * @param cutoff the date
     * @return those lifts in code order
     */
    public List<Lift> commissionedBefore(LocalDate cutoff) {
        return where(lift -> lift.commissioned().isBefore(cutoff));
    }

    /**
     * The machines the board wants an evacuation plan for.
     *
     * @return those lifts in code order
     */
    public List<Lift> needingEvacuationPlans() {
        return where(lift -> lift.liftClass().needsEvacuationPlan());
    }

    /**
     * A new registry with one more machine on it.
     *
     * @param lift the machine
     * @return the registry
     * @throws PlantError when the machine clashes with one already on the hill
     */
    public Resort withLift(Lift lift) {
        List<Lift> all = new ArrayList<>(lifts);
        all.add(lift);
        return of(name, site, season, all);
    }

    /**
     * A new registry with one machine taken off.
     *
     * @param code the machine's code, as somebody typed it
     * @return the registry
     * @throws PlantError when there is no such machine
     */
    public Resort withoutLift(String code) {
        return withoutLift(LiftCode.parse(code));
    }

    /**
     * A new registry with one machine taken off.
     *
     * @param code the machine's code
     * @return the registry
     * @throws PlantError when there is no such machine
     */
    public Resort withoutLift(LiftCode code) {
        Lift going = lift(code);
        return of(name, site, season, where(lift -> !lift.code().equals(going.code())));
    }

    /** Such as {@code Ogden Peak, 3 lifts}. */
    @Override
    public String toString() {
        return name + ", " + lifts.size() + " lifts";
    }

    private List<Lift> where(Predicate<Lift> test) {
        return lifts.stream().filter(test).toList();
    }
}
