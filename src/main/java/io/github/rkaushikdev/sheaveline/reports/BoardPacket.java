package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The annual packet, which is the one document that leaves the department.
 *
 * <p>Everything else in this package is written for somebody who works here. This is written for
 * five people on a state board who see the hill once a year, and it has to answer their questions in
 * the order they ask them: did anything happen, was it reported on time, are the machines being
 * looked after, and can you get people off them.
 *
 * <p>Two things are deliberately not on the page. There is no commentary, because a number with a
 * sentence attached to it is a number somebody is arguing about before they have read it. And there
 * is no total availability figure across the hill, because a surface tow that ran three hundred hours
 * and a detachable quad that ran two thousand do not average into anything a person should act on.
 *
 * <p>Like the morning page, it renders what it was handed. Gathering is somebody else's job, so the
 * packet for the year before last still prints.
 *
 * @param season the season, as the board names it
 * @param resort the operator
 * @param prepared the day the packet was prepared
 * @param preparedBy who is signing it
 * @param machines every machine's year
 * @param incidents every reportable incident and what became of it
 */
public record BoardPacket(
        String season,
        String resort,
        LocalDate prepared,
        String preparedBy,
        List<MachineYear> machines,
        List<FiledIncident> incidents) {

    private static final DateTimeFormatter HAPPENED = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    /** A filing day recorded without a number prints this word where the number would go. */
    private static final String NO_NUMBER = "None";

    /** A packet. */
    public BoardPacket {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(resort, "resort");
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(preparedBy, "preparedBy");
        machines = List.copyOf(machines);
        incidents = List.copyOf(incidents);
    }

    /**
     * A packet with nothing in it, for a season with no machines on the register.
     *
     * @param season the season
     * @param resort the operator
     * @param prepared the day it was prepared
     * @param preparedBy who is signing it
     * @return the packet
     */
    public static BoardPacket of(String season, String resort, LocalDate prepared, String preparedBy) {
        return of(season, resort, prepared, preparedBy, List.of(), List.of());
    }

    /**
     * A packet with its machines in code order and its incidents oldest first.
     *
     * @param season the season
     * @param resort the operator
     * @param prepared the day it was prepared
     * @param preparedBy who is signing it
     * @param machines every machine's year, in any order
     * @param incidents every incident, in any order
     * @return the packet
     */
    public static BoardPacket of(
            String season,
            String resort,
            LocalDate prepared,
            String preparedBy,
            List<MachineYear> machines,
            List<FiledIncident> incidents) {
        return new BoardPacket(
                season,
                resort,
                prepared,
                preparedBy,
                machines.stream()
                        .sorted(Comparator.comparing(one -> one.lift().value()))
                        .toList(),
                incidents.stream()
                        .sorted(Comparator.comparing(one -> one.incident().at()))
                        .toList());
    }

    /**
     * The machines with nothing against them.
     *
     * @return them, in code order
     */
    public List<MachineYear> cleanMachines() {
        return machines.stream().filter(MachineYear::isClean).toList();
    }

    /**
     * Every machine's concerns, machine by machine.
     *
     * @return the sentences
     */
    public List<String> concerns() {
        return machines.stream().flatMap(one -> one.concerns().stream()).toList();
    }

    /**
     * The incidents nobody has filed.
     *
     * @return them, oldest first
     */
    public List<FiledIncident> unfiled() {
        return incidents.stream().filter(one -> !one.isFiled()).toList();
    }

    /**
     * The incidents whose filing went in after the deadline.
     *
     * @return them, oldest first
     */
    public List<FiledIncident> lateFilings() {
        return incidents.stream().filter(FiledIncident::wasLate).toList();
    }

    /**
     * The busiest machine's days, which is how long the hill was open.
     *
     * @return the most days any machine opened, zero with no machines
     */
    public int daysOpen() {
        return machines.stream().mapToInt(MachineYear::daysOpen).max().orElse(0);
    }

    /**
     * Whether the packet has nothing outstanding: no concern, no unfiled incident, no late filing.
     *
     * @return true when it is clean
     */
    public boolean isClean() {
        return concerns().isEmpty() && unfiled().isEmpty() && lateFilings().isEmpty();
    }

    /**
     * The page.
     *
     * @return the report
     */
    public Report render() {
        Report page = new Report();
        page.heading("annual packet " + season);
        page.field("operator", resort);
        page.field("prepared", prepared.toString());
        page.field("by", preparedBy);
        page.field("machines", machines.size());
        page.field("days open", daysOpen());
        page.field("incidents", incidents.size());

        page.section("machines");
        page.table(Table.of(
                List.of(
                        new Column("lift", 5),
                        new Column("name", 16),
                        new Column("days", 5, Align.RIGHT),
                        new Column("avail", 7, Align.RIGHT),
                        new Column("status", 15),
                        new Column("due", 4, Align.RIGHT),
                        new Column("defects", 8, Align.RIGHT)),
                machines.stream()
                        .map(one -> List.of(
                                one.lift().toString(),
                                one.name(),
                                one.daysOpen(),
                                Figures.percent(one.availability()),
                                one.status().value(),
                                one.ladderOverdue(),
                                one.openDefects()))
                        .toList()));

        page.section("ropes");
        page.table(Table.of(
                List.of(new Column("lift", 5), new Column("serial", 18), new Column("verdict", 16)),
                machines.stream()
                        .map(one -> List.of(one.lift().toString(), one.ropeSerial(), one.ropeVerdict()))
                        .toList()));

        page.section("evacuation");
        page.table(Table.of(
                List.of(new Column("lift", 5), new Column("name", 20), new Column("clears in", 12, Align.RIGHT)),
                machines.stream()
                        .map(one -> List.of(
                                one.lift().toString(),
                                one.name(),
                                one.evacuationTime().map(Duration::formatClock).orElse("not drilled")))
                        .toList()));
        incidentPages(page);
        return page;
    }

    /**
     * The page as text.
     *
     * @return every line, each ended by a newline
     */
    public String text() {
        return render().text();
    }

    private void incidentPages(Report page) {
        page.section("incidents");
        if (incidents.isEmpty()) {
            page.line("nothing was reportable this season");
        } else {
            page.table(Table.of(
                    List.of(
                            new Column("lift", 5),
                            new Column("happened", 17),
                            new Column("what", 18),
                            new Column("injury", 12),
                            new Column("filed as", 15)),
                    incidents.stream()
                            .map(one -> List.of(
                                    one.incident().lift().toString(),
                                    HAPPENED.format(one.incident().at()),
                                    one.incident().kind().value(),
                                    one.incident().injury().value(),
                                    one.filing().map(FilingNumber::toString).orElse("NOT FILED")))
                            .toList()));
        }
        page.section("outstanding");
        List<String> outstanding = new ArrayList<>(concerns());
        for (FiledIncident one : unfiled()) {
            outstanding.add("incident on " + one.incident().at().toLocalDate() + " has not been filed");
        }
        for (FiledIncident one : lateFilings()) {
            String number = one.filing().map(FilingNumber::toString).orElse(NO_NUMBER);
            outstanding.add("filing " + number + " went in after the deadline");
        }
        if (outstanding.isEmpty()) {
            page.line("nothing outstanding");
            return;
        }
        for (String line : outstanding) {
            page.line("  " + line);
        }
    }
}
