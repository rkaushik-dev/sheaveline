package io.github.rkaushikdev.sheaveline.reports;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.HillCalendar;
import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakObservation;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ExportTest {

    private static final HaulRope ROPE = Hill.johnPaul().rope();
    private static final LocalDate ON = LocalDate.of(2025, 1, 15);

    private static Written written(Writing call) throws IOException {
        StringBuilder out = new StringBuilder();
        int count = call.to(out);
        return new Written(count, read(out.toString()));
    }

    private static Map<String, String> body(List<String> columns, List<String> row) {
        assertThat(row).hasSameSizeAs(columns);
        Map<String, String> body = new LinkedHashMap<>();
        IntStream.range(0, columns.size()).forEach(index -> body.put(columns.get(index), row.get(index)));
        return body;
    }

    private static DayRecord record(LocalDate day) {
        Duration scheduled = Duration.ofHours(7);
        Duration charged = Duration.ofSeconds(1800);
        return new DayRecord(
                JOHN_PAUL,
                day,
                true,
                scheduled,
                scheduled.minus(charged),
                charged,
                charged,
                List.of(StopKind.OPERATOR));
    }

    private static StopEvent stop(StopKind kind, boolean restarted, String note) {
        return new StopEvent(
                JOHN_PAUL,
                kind,
                LocalDateTime.of(2025, 1, 15, 11, 30),
                "D Chin",
                note,
                restarted ? Optional.of(LocalDateTime.of(2025, 1, 15, 11, 45)) : Optional.empty(),
                restarted ? Optional.of("D Chin") : Optional.empty(),
                true);
    }

    private static WearSurvey survey(List<DiameterReading> readings) {
        return WearSurvey.of(ROPE, ON, "R Halvorsen", readings);
    }

    private static DiameterReading reading(long stationFeet) {
        return new DiameterReading(Length.ofFeet(stationFeet), Length.ofInches("1.354"), Length.ofInches("1.350"));
    }

    @Test
    void aSheetWritesItsHeadingFirst() throws IOException {
        Written out = written(handle -> Export.DAYS.write(handle, List.of()));
        assertThat(out.count()).isZero();
        assertThat(out.rows()).containsExactly(Export.DAYS.columns());
    }

    @Test
    void aRowWithTheWrongNumberOfColumnsIsRefused() {
        Sheet made = new Sheet("small", List.of("one", "two"));
        assertThatThrownBy(() -> made.write(new StringBuilder(), List.of(List.of(1, 2, 3))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 columns and got 3");
    }

    @Test
    void nothingBecomesAnEmptyCellRatherThanTheWordNone() throws IOException {
        Written out = written(handle -> Export.writeStops(handle, List.of(stop(StopKind.OPERATOR, false, ""))));
        assertThat(out.count()).isEqualTo(1);
        assertThat(out.rows().get(1).get(4)).isEmpty();
        assertThat(out.rows().get(1).get(5)).isEmpty();
        assertThat(out.rows().get(1).get(6)).isEmpty();
    }

    @Test
    void theDaysGoOutAsTheIntegersTheyAreHeldIn() throws IOException {
        SeasonSummary summary = SeasonSummary.of(HillCalendar.theSeason(), List.of(record(ON)));
        Written out = written(handle -> Export.writeDays(handle, summary));
        assertThat(out.count()).isEqualTo(1);
        Map<String, String> body = body(Export.DAYS.columns(), out.rows().get(1));
        assertThat(body.get("lift")).isEqualTo("JP");
        assertThat(body.get("scheduled_seconds")).isEqualTo("25200");
        assertThat(body.get("chargeable_seconds")).isEqualTo("1800");
        assertThat(body.get("availability")).isEqualTo("0.9286");
        assertThat(body.get("opened")).isEqualTo("1");
    }

    @Test
    void everyDayInTheSeasonGetsARow() throws IOException {
        List<DayRecord> days =
                IntStream.range(0, 4).mapToObj(one -> record(ON.plusDays(one))).toList();
        Written out = written(handle -> Export.writeDays(handle, SeasonSummary.of(HillCalendar.theSeason(), days)));
        assertThat(out.count()).isEqualTo(4);
        assertThat(out.rows()).hasSize(5);
    }

    @Test
    void aStopCarriesWhatTheKindDecidesAboutIt() throws IOException {
        Written out = written(handle -> Export.writeStops(handle, List.of(stop(StopKind.POWER, true, ""))));
        Map<String, String> body = body(Export.STOPS.columns(), out.rows().get(1));
        assertThat(body.get("seconds")).isEqualTo("900");
        assertThat(body.get("counts_against_the_machine")).isEqualTo("0");
        assertThat(body.get("reportable")).isEqualTo("0");
        assertThat(body.get("line_checked")).isEqualTo("1");
    }

    @Test
    void aDeropementIsMarkedReportable() throws IOException {
        StopEvent made = stop(StopKind.DEROPEMENT, true, "grip off at tower four");
        Written out = written(handle -> Export.writeStops(handle, List.of(made)));
        Map<String, String> body = body(Export.STOPS.columns(), out.rows().get(1));
        assertThat(body.get("reportable")).isEqualTo("1");
        assertThat(body.get("note")).isEqualTo("grip off at tower four");
    }

    @Test
    void anIncidentCarriesItsDeadlineAndItsFiling() throws IOException {
        Incident one = new Incident(
                JOHN_PAUL,
                LocalDateTime.of(2025, 1, 15, 11, 30),
                IncidentKind.DEROPEMENT,
                InjuryLevel.NONE,
                3,
                "grip off at tower four",
                "D Chin");
        Written out = written(handle -> Export.writeIncidents(
                handle,
                List.of(
                        new FiledIncident(one, Optional.of(FilingNumber.parse("UT-2025-0001"))),
                        new FiledIncident(one, Optional.empty()))));
        assertThat(out.count()).isEqualTo(2);
        assertThat(out.rows().get(1).get(6)).isEqualTo("UT-2025-0001");
        assertThat(out.rows().get(2).get(6)).isEmpty();
        assertThat(out.rows().get(1).get(5)).isEqualTo("2025-01-15T13:30:00");
    }

    @Test
    void aWearWalkBecomesOneRowAStation() throws IOException {
        WearSurvey walked = survey(List.of(reading(600), reading(1200), reading(1800)));
        Written out = written(handle -> Export.writeWear(handle, List.of(walked)));
        assertThat(out.count()).isEqualTo(3);
        assertThat(out.rows().get(1).get(3)).isEqualTo("7200000");
        assertThat(out.rows().get(1).get(4)).isEqualTo("1354");
        assertThat(out.rows().get(1).get(5)).isEqualTo("1350");
    }

    @Test
    void aBreakCountBecomesOneRowAStationWithWhatItIsOver() throws IOException {
        BreakSurvey counted = BreakSurvey.of(
                ROPE,
                ON,
                "R Halvorsen",
                List.of(
                        new BreakObservation(Length.ofFeet(0), 3, 3, 0, true),
                        new BreakObservation(Length.ofFeet(1200), 4, 7),
                        new BreakObservation(Length.ofFeet(2400), 1, 1)));
        Written out = written(handle -> Export.writeBreaks(handle, List.of(counted)));
        assertThat(out.count()).isEqualTo(3);
        Map<String, String> splice = body(Export.BREAKS.columns(), out.rows().get(1));
        assertThat(splice)
                .containsEntry("rope", "FAT-2019-04412")
                .containsEntry("station_mils", "0")
                .containsEntry("at_splice", "1")
                .containsEntry("verdict", "condemned")
                .containsEntry("over", "3 in one lay at the splice against 2");
        Map<String, String> watched = body(Export.BREAKS.columns(), out.rows().get(2));
        assertThat(watched).containsEntry("verdict", "monitor").containsEntry("over", "");
        assertThat(body(Export.BREAKS.columns(), out.rows().get(3))).containsEntry("verdict", "serviceable");
    }

    /** A spreadsheet reads {@code 1.354"} as a date, and then the column is text. */
    @Test
    void aDiameterNeverLeavesWithAnInchMarkOnIt() throws IOException {
        WearSurvey walked = survey(List.of(reading(600)));
        Written out = written(handle -> Export.writeWear(handle, List.of(walked)));
        assertThat(out.rows().get(1)).allSatisfy(cell -> assertThat(cell).doesNotContain("\""));
        assertThat(out.rows().get(1)).allSatisfy(cell -> assertThat(cell).doesNotContain("'"));
    }

    @Test
    void everySheetIsReachableByName() {
        assertThat(Export.SHEETS.keySet()).containsExactlyInAnyOrder("days", "incidents", "stops", "wear", "breaks");
        assertThat(Export.SHEETS)
                .allSatisfy((name, sheet) -> assertThat(sheet.name()).isEqualTo(name));
    }

    @Test
    void noTwoSheetsShareAColumnLayoutByAccident() {
        List<List<String>> layouts =
                Export.SHEETS.values().stream().map(Sheet::columns).toList();
        assertThat(new HashSet<>(layouts)).hasSameSizeAs(layouts);
    }

    @Test
    void theRowsEndTheSameWayOnEveryMachine() throws IOException {
        StringBuilder handle = new StringBuilder();
        Export.writeStops(handle, List.of(stop(StopKind.OPERATOR, true, "")));
        assertThat(handle.toString()).doesNotContain("\r\n");
        assertThat(handle.toString()).endsWith("\n");
    }

    /**
     * Comma separated text read back the way a spreadsheet loads it: double quoted cells may hold
     * commas, line breaks and doubled double quotes, and a line feed outside quotes ends a row.
     */
    private static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < text.length(); at++) {
            char one = text.charAt(at);
            if (quoted) {
                if (one == '"' && at + 1 < text.length() && text.charAt(at + 1) == '"') {
                    cell.append('"');
                    at++;
                } else if (one == '"') {
                    quoted = false;
                } else {
                    cell.append(one);
                }
            } else if (one == '"') {
                quoted = true;
            } else if (one == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (one == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(List.copyOf(row));
                row.clear();
            } else if (one != '\r') {
                cell.append(one);
            }
        }
        if (!row.isEmpty() || !cell.isEmpty()) {
            row.add(cell.toString());
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    /** Something that writes a sheet and says how many rows it wrote. */
    @FunctionalInterface
    private interface Writing {
        int to(StringBuilder out) throws IOException;
    }

    /** What a sheet wrote: its count and its rows, the heading first. */
    private record Written(int count, List<List<String>> rows) {}
}
