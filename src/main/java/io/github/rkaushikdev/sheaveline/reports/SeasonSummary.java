package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The season summary, which is the page the board reads in May.
 *
 * <p>It is the same handful of numbers asked of every operating day: how many days did each machine
 * run, how much of that was it stopped, and whose fault was the stopping. The board compares this
 * year against last, so the arithmetic has to be the same arithmetic every year.
 *
 * <p>The records are tallied once, on the way in, and every question reads the tally. A season of
 * a hundred and fifty days across six machines is nine hundred rows, and the page asks eight
 * questions a machine plus one a stop kind; walking the records for each of them showed.
 *
 * @param season the season
 * @param records every day record, by day and then by machine code
 * @param tallies each machine's tally, by machine code
 */
public record SeasonSummary(Season season, List<DayRecord> records, Map<LiftCode, LiftTally> tallies) {

    /** A summary. */
    public SeasonSummary {
        Objects.requireNonNull(season, "season");
        records = List.copyOf(records);
        tallies = Collections.unmodifiableMap(new LinkedHashMap<>(tallies));
    }

    /**
     * Sort a season's records and tally every machine in them.
     *
     * @param season the season
     * @param records the day records, in any order
     * @return the summary
     */
    public static SeasonSummary of(Season season, Collection<DayRecord> records) {
        List<DayRecord> ordered = records.stream()
                .sorted(Comparator.comparing(DayRecord::day)
                        .thenComparing(one -> one.lift().value()))
                .toList();
        Map<LiftCode, List<DayRecord>> grouped = new TreeMap<>();
        for (DayRecord record : ordered) {
            grouped.computeIfAbsent(record.lift(), lift -> new ArrayList<>()).add(record);
        }
        Map<LiftCode, LiftTally> tallies = new LinkedHashMap<>();
        grouped.forEach((lift, days) -> tallies.put(lift, LiftTally.of(lift, days)));
        return new SeasonSummary(season, ordered, tallies);
    }

    /**
     * The machines with at least one record.
     *
     * @return their codes, in code order
     */
    public List<LiftCode> lifts() {
        return List.copyOf(tallies.keySet());
    }

    /**
     * One machine's tally.
     *
     * @param lift the machine
     * @return its tally, all zeros for a machine with no records
     */
    public LiftTally tally(LiftCode lift) {
        LiftTally found = tallies.get(lift);
        return found != null ? found : LiftTally.of(lift, List.of());
    }

    /**
     * One machine's records.
     *
     * @param lift the machine
     * @return its records by day
     */
    public List<DayRecord> forLift(LiftCode lift) {
        return tally(lift).days();
    }

    /**
     * The days a machine opened.
     *
     * @param lift the machine
     * @return the count
     */
    public int daysOpen(LiftCode lift) {
        return tally(lift).daysOpen();
    }

    /**
     * The days a machine did not open.
     *
     * @param lift the machine
     * @return the count
     */
    public int daysShut(LiftCode lift) {
        return tally(lift).daysShut();
    }

    /**
     * The time a machine was meant to run.
     *
     * @param lift the machine
     * @return the total
     */
    public Duration scheduled(LiftCode lift) {
        return tally(lift).scheduled();
    }

    /**
     * The time a machine ran.
     *
     * @param lift the machine
     * @return the total
     */
    public Duration running(LiftCode lift) {
        return tally(lift).running();
    }

    /**
     * The time a machine was stopped, whatever stopped it.
     *
     * @param lift the machine
     * @return the total
     */
    public Duration downtime(LiftCode lift) {
        return tally(lift).downtime();
    }

    /**
     * The stopped time charged to a machine.
     *
     * @param lift the machine
     * @return the total
     */
    public Duration chargeable(LiftCode lift) {
        return tally(lift).chargeable();
    }

    /**
     * The share of a machine's scheduled time it was not charged for.
     *
     * @param lift the machine
     * @return a fraction, zero when nothing was scheduled
     */
    public BigDecimal availability(LiftCode lift) {
        return tally(lift).availability();
    }

    /**
     * The day that cost a machine the most.
     *
     * @param lift the machine
     * @return the day, or empty for a machine with no records
     */
    public Optional<DayRecord> worstDay(LiftCode lift) {
        return tally(lift).worstDay();
    }

    /**
     * How many times a machine stopped for each reason.
     *
     * @param lift the machine
     * @return a copy of the counts, which the caller may change without changing the tally
     */
    public Map<StopKind, Integer> stopsByKind(LiftCode lift) {
        return new LinkedHashMap<>(tally(lift).stops());
    }

    /**
     * The share of every machine's scheduled time, added together, that was not charged for.
     *
     * @return a fraction, zero when nothing was scheduled anywhere
     */
    public BigDecimal hillAvailability() {
        long scheduled = 0;
        long lost = 0;
        for (LiftTally one : tallies.values()) {
            scheduled += one.scheduled().seconds();
            lost += one.chargeable().seconds();
        }
        return DayRecord.availability(scheduled, lost);
    }

    /**
     * The page, drawn from the tallies alone.
     *
     * @return the report
     */
    public Report render() {
        List<LiftTally> tallied = lifts().stream().map(this::tally).toList();
        Report page = new Report();
        page.heading("season summary " + season.label());
        page.field("opened", season.opens().toString());
        page.field("closed", season.closes().toString());
        page.field("operating days", season.length());
        page.field("machines", tallied.size());
        page.field("availability", Figures.percent(hillAvailability()));

        page.section("by machine");
        page.table(Table.of(
                List.of(
                        new Column("lift", 5),
                        new Column("open", 5, Align.RIGHT),
                        new Column("shut", 5, Align.RIGHT),
                        new Column("scheduled", 10, Align.RIGHT),
                        new Column("running", 10, Align.RIGHT),
                        new Column("charged", 10, Align.RIGHT),
                        new Column("available", 10, Align.RIGHT)),
                tallied.stream()
                        .map(one -> List.of(
                                one.lift().toString(),
                                one.daysOpen(),
                                one.daysShut(),
                                Figures.hours(one.scheduled(), 0),
                                Figures.hours(one.running(), 0),
                                Figures.hours(one.chargeable(), 1),
                                Figures.percent(one.availability())))
                        .toList()));

        page.section("worst day a machine");
        List<List<Object>> worst = new ArrayList<>();
        for (LiftTally one : tallied) {
            one.worstDay()
                    .ifPresent(day -> worst.add(List.of(
                            one.lift().toString(),
                            day.day().toString(),
                            Figures.hours(day.chargeable(), 1),
                            Figures.percent(day.availability()))));
        }
        page.table(Table.of(
                List.of(
                        new Column("lift", 5),
                        new Column("day", 11),
                        new Column("charged", 10, Align.RIGHT),
                        new Column("available", 10, Align.RIGHT)),
                worst));

        page.section("stops by kind");
        List<StopKind> kinds = tallied.stream()
                .flatMap(one -> one.stops().keySet().stream())
                .distinct()
                .sorted(Comparator.comparing(StopKind::value))
                .toList();
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("kind", 18));
        tallied.forEach(one -> columns.add(new Column(one.lift().toString(), 5, Align.RIGHT)));
        List<List<Object>> counts = new ArrayList<>();
        for (StopKind kind : kinds) {
            List<Object> row = new ArrayList<>();
            row.add(kind.value());
            tallied.forEach(one -> row.add(one.stops().getOrDefault(kind, 0)));
            counts.add(row);
        }
        page.table(Table.of(columns, counts));
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
}
