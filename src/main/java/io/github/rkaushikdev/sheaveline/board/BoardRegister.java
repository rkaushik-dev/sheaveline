package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every incident on the hill, and what has been sent about each one: the answer to what the
 * department still owes the board.
 *
 * <p>An incident and a filing are separate records on purpose. The incident happened whether or
 * not anybody wrote to the board about it, and the gap between the two is exactly the thing an
 * inspector looks for. So the register holds incidents with a filing hanging off each one, and the
 * filing can be missing.
 *
 * <p>The question it exists to answer is asked every Monday morning: what is due, what is late, and
 * what goes in this month's return.
 *
 * @param series the board's numbers for the year
 * @param reported the incidents, in the order they happened
 */
public record BoardRegister(FilingSeries series, List<ReportedIncident> reported) {

    /** A register. */
    public BoardRegister {
        Objects.requireNonNull(series, "series");
        reported = List.copyOf(reported);
    }

    /**
     * A register with nothing reported in it.
     *
     * @param series the board's numbers for the year
     * @return the register
     */
    public static BoardRegister of(FilingSeries series) {
        return of(series, List.of());
    }

    /**
     * A register with its incidents put in the order they happened.
     *
     * @param series the board's numbers for the year
     * @param reported the incidents, in any order
     * @return the register
     */
    public static BoardRegister of(FilingSeries series, List<ReportedIncident> reported) {
        List<ReportedIncident> ordered = reported.stream()
                .sorted(Comparator.comparing(entry -> entry.incident().at()))
                .toList();
        return new BoardRegister(series, ordered);
    }

    /**
     * Enter an incident, unfiled.
     *
     * @param incident the incident
     * @return a new register; this one is unchanged
     */
    public BoardRegister record(Incident incident) {
        List<ReportedIncident> longer = new ArrayList<>(reported);
        longer.add(new ReportedIncident(incident));
        return of(series, longer);
    }

    /**
     * Take a number and hang it on the incident, which is the only way to file.
     *
     * @param incident the incident, as it is in this register
     * @param filed when the filing went out
     * @param filedBy who sent it
     * @return a new register with the filing and the longer series; this one is unchanged
     * @throws BoardError when the incident is not in the register or has already been filed
     */
    public BoardRegister file(Incident incident, LocalDateTime filed, String filedBy) {
        ReportedIncident held = reported.stream()
                .filter(entry -> entry.incident().equals(incident))
                .findFirst()
                .orElseThrow(() -> new BoardError("that incident is not in the register"));
        if (held.isFiled()) {
            throw new BoardError("that incident has already been filed");
        }
        FilingSeries.Filed sent =
                series.file(FilingKind.INCIDENT, incident.lift(), filed, incident.narrative(), filedBy);
        List<ReportedIncident> changed = new ArrayList<>(reported.stream()
                .filter(entry -> !entry.incident().equals(incident))
                .toList());
        changed.add(new ReportedIncident(incident, sent.filing()));
        return of(sent.series(), changed);
    }

    /**
     * The incidents nothing has been filed about.
     *
     * @return the entries, in the order they happened
     */
    public List<ReportedIncident> unfiled() {
        return reported.stream().filter(entry -> !entry.isFiled()).toList();
    }

    /**
     * The incidents the board heard about too late, or has not heard about past the due time.
     *
     * @param now the moment asked about
     * @return the entries, in the order they happened
     */
    public List<ReportedIncident> overdue(LocalDateTime now) {
        return reported.stream().filter(entry -> entry.isOverdue(now)).toList();
    }

    /**
     * The incidents that were filed, but late.
     *
     * @return the entries, in the order they happened
     */
    public List<ReportedIncident> lateFilings() {
        return reported.stream()
                .filter(entry -> entry.isFiled() && !entry.lateness().isZero())
                .toList();
    }

    /**
     * The incidents on one machine.
     *
     * @param lift the machine
     * @return the entries, in the order they happened
     */
    public List<ReportedIncident> forLift(LiftCode lift) {
        return reported.stream()
                .filter(entry -> entry.incident().lift().equals(lift))
                .toList();
    }

    /**
     * The unfiled incident with the least time left on it. The earliest wins a tie.
     *
     * @param now the moment asked about
     * @return the entry, or empty when nothing unfiled is still inside its deadline
     */
    public Optional<ReportedIncident> dueNext(LocalDateTime now) {
        return unfiled().stream()
                .filter(entry -> !entry.incident().dueBy().isBefore(now))
                .min(Comparator.comparing(entry -> entry.incident().dueBy()));
    }

    /**
     * Everything that only ever needed a line in the month's return.
     *
     * @param year the year
     * @param month the month, one for January
     * @return the entries that happened that month, in the order they happened
     */
    public List<ReportedIncident> monthlyReturn(int year, int month) {
        return reported.stream()
                .filter(entry -> entry.incident().goesInTheMonthlyReturn()
                        && entry.incident().at().getYear() == year
                        && entry.incident().at().getMonthValue() == month)
                .toList();
    }

    /**
     * Machines with an unfiled incident against them that stops the lift running.
     *
     * @return the machines, in code order
     */
    public Set<LiftCode> holdingMachines() {
        Set<LiftCode> holding = new TreeSet<>();
        for (ReportedIncident entry : reported) {
            if (entry.incident().holdsTheMachine() && !entry.isFiled()) {
                holding.add(entry.incident().lift());
            }
        }
        return Collections.unmodifiableSet(holding);
    }

    /**
     * How many incidents are in the register.
     *
     * @return the count
     */
    public int size() {
        return reported.size();
    }
}
