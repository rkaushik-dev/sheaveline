package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Handing the numbers to somebody who is going to open them in a spreadsheet.
 *
 * <p>The eighty column pages are for reading. This is for the finance office, who want last season's
 * downtime next to the year before's in a pivot table, and for the state, who ask once a year for the
 * incident register in a form they can load.
 *
 * <p>Two rules and they are not negotiable. Every measure goes out as the integer this system holds
 * it in, with the unit named in the column heading, because a spreadsheet that reads {@code 1.378"}
 * as a date has already lost. And every row is one thing: a day, a stop, an incident. Nothing is
 * grouped, nothing is subtotalled, and no cell contains a formula. Whoever opens it is going to group
 * it themselves and they will do it differently every time.
 *
 * <p>Rows are written straight to the destination rather than built in memory. A season of ten
 * minute weather readings across four stations is two hundred thousand rows and there is no reason
 * for all of it to exist at once, so every row source here is lazy.
 *
 * <p>Moments go out as {@code 2025-01-15T11:30:00}: the date, a {@code T}, the time to the second,
 * and the microseconds after a point only when there are any.
 */
public final class Export {

    /** One row a machine a day. */
    public static final Sheet DAYS = new Sheet(
            "days",
            List.of(
                    "lift",
                    "day",
                    "opened",
                    "scheduled_seconds",
                    "running_seconds",
                    "downtime_seconds",
                    "chargeable_seconds",
                    "availability",
                    "stops"));

    /** One row a stop. */
    public static final Sheet STOPS = new Sheet(
            "stops",
            List.of(
                    "lift",
                    "kind",
                    "stopped",
                    "stopped_by",
                    "restarted",
                    "restarted_by",
                    "seconds",
                    "line_checked",
                    "reportable",
                    "counts_against_the_machine",
                    "note"));

    /** One row an incident. */
    public static final Sheet INCIDENTS = new Sheet(
            "incidents",
            List.of("lift", "at", "kind", "injury", "people", "due_by", "filing", "reported_by", "narrative"));

    /** One row a caliper reading. */
    public static final Sheet WEAR = new Sheet(
            "wear",
            List.of("rope", "taken", "inspector", "station_mils", "crowns_mils", "valleys_mils", "percent_of_nominal"));

    /** Every sheet, by the name it is asked for by. */
    public static final Map<String, Sheet> SHEETS = sheets(DAYS, STOPS, INCIDENTS, WEAR);

    private static final DateTimeFormatter TO_THE_SECOND =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT);

    private Export() {}

    /**
     * A season's day records as rows, by day and then by machine.
     *
     * @param summary the season
     * @return the rows of {@link #DAYS}
     */
    public static Iterable<List<Object>> dayRows(SeasonSummary summary) {
        return lazily(
                summary.records(),
                record -> List.of(
                        record.lift().toString(),
                        record.day().toString(),
                        flag(record.opened()),
                        record.scheduled().seconds(),
                        record.running().seconds(),
                        record.downtime().seconds(),
                        record.chargeable().seconds(),
                        Decimals.fixed(record.availability(), 4),
                        record.stops().size()));
    }

    /**
     * Stops as rows. A stop still on has no restart and no length, and those cells are empty.
     *
     * @param stops the stops
     * @return the rows of {@link #STOPS}
     */
    public static Iterable<List<Object>> stopRows(Iterable<StopEvent> stops) {
        return lazily(
                stops,
                one -> List.of(
                        one.lift().toString(),
                        one.kind().value(),
                        moment(one.stopped()),
                        one.stoppedBy(),
                        one.restarted().map(Export::moment),
                        one.restartedBy(),
                        one.isOpen()
                                ? Optional.empty()
                                : Optional.of(one.length().seconds()),
                        flag(one.lineChecked()),
                        flag(one.kind().isReportable()),
                        flag(one.kind().countsAgainstTheMachine()),
                        one.note()));
    }

    /**
     * Incidents as rows, each with its deadline and, once it has one, its filing number.
     *
     * @param incidents the incidents and their filings
     * @return the rows of {@link #INCIDENTS}
     */
    public static Iterable<List<Object>> incidentRows(Iterable<FiledIncident> incidents) {
        return lazily(incidents, filed -> {
            Incident one = filed.incident();
            return List.of(
                    one.lift().toString(),
                    moment(one.at()),
                    one.kind().value(),
                    one.injury().value(),
                    one.people(),
                    moment(one.dueBy()),
                    filed.filing().map(FilingNumber::toString),
                    one.reportedBy(),
                    one.narrative());
        });
    }

    /**
     * Caliper walks as rows: one row a reading, not one a walk, because the pivot is on the station.
     *
     * @param surveys the walks
     * @return the rows of {@link #WEAR}
     */
    public static Iterable<List<Object>> wearRows(Iterable<WearSurvey> surveys) {
        return () -> stream(surveys)
                .flatMap(survey -> survey.diameters().stream().map(reading -> wearRow(survey, reading)))
                .iterator();
    }

    /**
     * Write a season's days.
     *
     * @param out where the text goes
     * @param summary the season
     * @return how many rows were written
     * @throws IOException when the destination cannot be written
     */
    public static int writeDays(Appendable out, SeasonSummary summary) throws IOException {
        return DAYS.write(out, dayRows(summary));
    }

    /**
     * Write stops.
     *
     * @param out where the text goes
     * @param stops the stops
     * @return how many rows were written
     * @throws IOException when the destination cannot be written
     */
    public static int writeStops(Appendable out, Iterable<StopEvent> stops) throws IOException {
        return STOPS.write(out, stopRows(stops));
    }

    /**
     * Write incidents.
     *
     * @param out where the text goes
     * @param incidents the incidents and their filings
     * @return how many rows were written
     * @throws IOException when the destination cannot be written
     */
    public static int writeIncidents(Appendable out, Iterable<FiledIncident> incidents) throws IOException {
        return INCIDENTS.write(out, incidentRows(incidents));
    }

    /**
     * Write caliper walks, a row a reading.
     *
     * @param out where the text goes
     * @param surveys the walks
     * @return how many rows were written
     * @throws IOException when the destination cannot be written
     */
    public static int writeWear(Appendable out, Iterable<WearSurvey> surveys) throws IOException {
        return WEAR.write(out, wearRows(surveys));
    }

    private static List<Object> wearRow(WearSurvey survey, DiameterReading reading) {
        return List.of(
                survey.rope().serial().toString(),
                survey.taken().toString(),
                survey.inspector(),
                reading.station().mils(),
                reading.acrossCrowns().mils(),
                reading.acrossValleys().mils(),
                Decimals.fixed(survey.percentOfNominal(reading), 2));
    }

    private static int flag(boolean value) {
        return value ? 1 : 0;
    }

    private static String moment(LocalDateTime at) {
        int micros = at.getNano() / 1000;
        String text = TO_THE_SECOND.format(at);
        return micros == 0 ? text : text + String.format(Locale.ROOT, ".%06d", micros);
    }

    private static <T> Iterable<List<Object>> lazily(Iterable<T> things, Function<T, List<Object>> row) {
        return () -> stream(things).map(row).iterator();
    }

    private static <T> Stream<T> stream(Iterable<T> things) {
        return StreamSupport.stream(things.spliterator(), false);
    }

    private static Map<String, Sheet> sheets(Sheet... sheets) {
        Map<String, Sheet> byName = new LinkedHashMap<>();
        for (Sheet one : sheets) {
            byName.put(one.name(), one);
        }
        return Collections.unmodifiableMap(byName);
    }
}
