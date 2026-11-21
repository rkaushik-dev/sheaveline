package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The standing list of jobs, which is data rather than code on purpose.
 *
 * <p>This is the department's maintenance manual turned into rows. It gets edited when the
 * manufacturer issues a bulletin or the board changes a rule, and that should be an edit to one line
 * here and nothing else. Anything that reads the list reads it through {@link #libraryFor} and
 * {@link #requirement}, so nothing downstream holds its own copy of what a machine is due.
 *
 * <p>The codes follow the interval they run on, which is how the crew refers to them on the radio.
 * D is daily, W weekly, M monthly, A annual, F five yearly, H hour based and C cycle based.
 */
public final class Library {

    /** Every class of machine. */
    public static final Set<LiftClass> ALL_CLASSES = Collections.unmodifiableSet(EnumSet.allOf(LiftClass.class));

    /** Every class with a tension carriage and a counterweight on it, which is every aerial one. */
    public static final Set<LiftClass> COUNTERWEIGHTED = Requirement.AERIAL;

    /** Every job in the manual, in the order the manual lists them. */
    public static final List<Requirement> STANDARD_LIBRARY = List.of(
            new Requirement("D1", "ride the line and look at every sheave train", Interval.daily(), ALL_CLASSES),
            new Requirement("D2", "set and release the service brake", Interval.daily(), ALL_CLASSES),
            new Requirement("D3", "test both terminal stop switches", Interval.daily(), ALL_CLASSES),
            new Requirement("W1", "walk the towers and check sheave liners and bolts", Interval.weekly(), ALL_CLASSES),
            new Requirement("W2", "inspect the grips through the terminal", Interval.weekly(), Requirement.DETACHABLES),
            new Requirement(
                    "W3", "check the tension carriage travel and the gauge", Interval.weekly(), COUNTERWEIGHTED),
            new Requirement("M1", "gearbox oil level and condition", Interval.monthly(), ALL_CLASSES),
            new Requirement(
                    "M2", "test the telephone and the radio to both terminals", Interval.monthly(), ALL_CLASSES),
            new Requirement(
                    "M3", "check the evacuation kit seals and the rope bags", Interval.monthly(), Requirement.AERIAL),
            new Requirement("M4", "run the auxiliary drive under load", Interval.monthly(), Requirement.AERIAL),
            new Requirement("A1", "annual inspection by a licensed inspector", Interval.annual(), ALL_CLASSES),
            new Requirement("A2", "load test both brakes", Interval.annual(), Requirement.AERIAL),
            new Requirement("A3", "inspect every splice in the haul rope", Interval.annual(), ALL_CLASSES),
            new Requirement("A4", "run a full evacuation drill", Interval.annual(), Requirement.AERIAL),
            new Requirement("A5", "repaint the tower numbers and the signage", Interval.annual(), false, ALL_CLASSES),
            new Requirement("F1", "non destructive test of the drive shaft", Interval.fiveYearly(), ALL_CLASSES),
            new Requirement("F2", "non destructive test of both bullwheel shafts", Interval.fiveYearly(), ALL_CLASSES),
            new Requirement(
                    "H1",
                    "change the gearbox oil",
                    Interval.ofHours(1500).withDays(730).withGraceDays(30),
                    ALL_CLASSES),
            new Requirement(
                    "H2",
                    "change the hydraulic filter",
                    Interval.ofHours(1000).withDays(365).withGraceDays(14),
                    ALL_CLASSES),
            new Requirement(
                    "C1",
                    "change the grip conveyor tyres",
                    Interval.ofCycles(500_000).withDays(1095).withGraceDays(30),
                    Requirement.DETACHABLES));

    private Library() {}

    /**
     * The jobs a machine of that class actually carries.
     *
     * @param liftClass the class
     * @return those jobs, in the manual's order
     */
    public static List<Requirement> libraryFor(LiftClass liftClass) {
        return STANDARD_LIBRARY.stream().filter(job -> job.appliesTo(liftClass)).toList();
    }

    /**
     * One job by its code, which is how the radio refers to it.
     *
     * @param code the code, in either case
     * @return the job
     * @throws UpkeepError when the manual has no such job
     */
    public static Requirement requirement(String code) {
        String wanted = code.toUpperCase(Locale.ROOT);
        for (Requirement job : STANDARD_LIBRARY) {
            if (job.code().equals(wanted)) {
                return job;
            }
        }
        throw new UpkeepError("there is no job " + code);
    }

    /**
     * The codes of the jobs a machine of that class carries.
     *
     * @param liftClass the class
     * @return the codes, in the manual's order
     */
    public static List<String> codesFor(LiftClass liftClass) {
        return libraryFor(liftClass).stream().map(Requirement::code).toList();
    }

    /**
     * The codes of the critical jobs a machine of that class carries.
     *
     * @param liftClass the class
     * @return the codes, in the manual's order
     */
    public static List<String> criticalCodesFor(LiftClass liftClass) {
        return libraryFor(liftClass).stream()
                .filter(Requirement::critical)
                .map(Requirement::code)
                .toList();
    }
}
