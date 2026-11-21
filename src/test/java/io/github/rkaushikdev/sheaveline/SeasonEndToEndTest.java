package io.github.rkaushikdev.sheaveline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Office.Machine;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.cli.Main;
import io.github.rkaushikdev.sheaveline.cli.Terminal;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A whole season through the front doors, with nothing reaching past them.
 *
 * <p>Every other test in this suite holds something still. This one does not. It builds the hill
 * through the command line, runs it for a stretch of days, breaks it, fixes it, reports it to the
 * board and then asks for the annual packet, and it only ever touches the two surfaces a person
 * touches. Nothing here opens a repository or builds a domain object.
 *
 * <p>That is the point. Each piece has been proved on its own and the thing that keeps going wrong
 * in software like this is the seam between them, so the seams are what this walks.
 */
class SeasonEndToEndTest {

    private static final LocalDate OPENS = LocalDate.of(2024, 12, 1);

    private static final LocalDate CLOSES = LocalDate.of(2025, 4, 15);

    private static final LocalDate FIRST = LocalDate.of(2025, 1, 13);

    private static final List<LocalDate> DAYS =
            IntStream.range(0, 5).mapToObj(FIRST::plusDays).toList();

    @TempDir
    Path tmp;

    private String hill;

    private Database tabletDatabase;

    private Wire.Running tablet;

    /**
     * What one command line came back with.
     *
     * @param code the exit code
     * @param out what went to standard output
     * @param err what went to standard error
     */
    private record Reply(int code, String out, String err) {}

    @BeforeEach
    void stockTheHill() {
        hill = Office.stocked(tmp, Machine.JOHN_PAUL, Machine.STRAWBERRY);
    }

    @AfterEach
    void putTheTabletAway() {
        if (tablet != null) {
            tablet.close();
            tabletDatabase.close();
        }
    }

    /** The http surface reading the same database the commands write to. */
    private Http tablet() {
        if (tablet == null) {
            tabletDatabase = Database.open(hill);
            tablet = Wire.freshClient(
                    Settings.of(tabletDatabase).withKeys(Wire.RING).withToday(() -> FIRST));
        }
        return tablet.client();
    }

    private Reply invoke(String... args) {
        List<String> line = new ArrayList<>(List.of("--database", hill));
        line.addAll(List.of(args));
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                name -> null,
                new BufferedReader(new StringReader("")),
                new PrintWriter(out, true),
                new PrintWriter(err, true));
        int code = Main.run(terminal, line.toArray(String[]::new));
        return new Reply(code, out.toString(), err.toString());
    }

    private Reply run(String... args) {
        Reply reply = invoke(args);
        assertThat(reply.code())
                .as(List.of(args) + "\n" + reply.out() + "\n" + reply.err())
                .isZero();
        return reply;
    }

    private Reply refuse(String... args) {
        Reply reply = invoke(args);
        assertThat(reply.code())
                .as(List.of(args) + " was allowed\n" + reply.out())
                .isEqualTo(1);
        return reply;
    }

    /** The job codes off the printed table, which is what a mechanic reads too. */
    private List<String> ladderJobs(String code) {
        String printed = run("upkeep", "due", code, "--on", OPENS.minusDays(1).toString())
                .out();
        List<String> found = new ArrayList<>();
        for (String line : printed.lines().toList()) {
            String first = line.split(" ", -1)[0];
            if (first.length() == 2 && Character.isUpperCase(first.charAt(0)) && Character.isDigit(first.charAt(1))) {
                found.add(first);
            }
        }
        return found;
    }

    /** Every job on the machine's ladder done the day before the season opens, and the machine drilled. */
    private void signOffTheLadder(String code) {
        String when = OPENS.minusDays(1).toString();
        for (String job : ladderJobs(code)) {
            run(
                    "upkeep",
                    "record",
                    code,
                    job,
                    "--by",
                    "R Skaggs",
                    "--hours",
                    "40000.0",
                    "--cycles",
                    "1400000",
                    "--on",
                    when);
        }
        run(
                "evac",
                "drill",
                code,
                "--team",
                "alpha",
                "--team",
                "bravo",
                "--carriers",
                "8",
                "--minutes",
                "52",
                "--observer",
                "M Vetter",
                "--on",
                when);
    }

    private void openTheDay(LocalDate day) {
        long since = ChronoUnit.DAYS.between(FIRST, day);
        run("day", "start", "JP", "--mechanic", "R Skaggs", "--operator", "D Chin", "--on", day.toString());
        run(
                "day",
                "meter",
                "JP",
                (40000 + since * 8) + ".0",
                Long.toString(1_400_000 + since * 4000),
                "--by",
                "R Skaggs",
                "--on",
                day.toString());
    }

    @Test
    void aSeasonRunsFromAnEmptyRegisterToASignedPacket() {
        // December: the ladder is signed off and the machine is drilled.
        signOffTheLadder("JP");
        signOffTheLadder("SB");

        // January: five operating days, one of which goes wrong.
        for (LocalDate day : DAYS) {
            openTheDay(day);
        }
        String bad = DAYS.get(2).toString();
        run(
                "day",
                "stop",
                "JP",
                "11:30",
                "--kind",
                "safety_circuit",
                "--by",
                "D Chin",
                "--note",
                "tower six stop opened, nothing found",
                "--on",
                bad);
        run("day", "restart", "JP", "11:30", "12:20", "--by", "R Skaggs", "--line-checked", "--on", bad);

        // The walk that afternoon turns up a defect, which becomes a work order.
        run(
                "upkeep",
                "record",
                "JP",
                "W1",
                "--by",
                "R Skaggs",
                "--defect",
                "JP-T6:defect:stop circuit terminal corroded",
                "--on",
                bad);
        Reply raised = run("upkeep", "raise", "JP", "JP-T6", "--by", "R Skaggs");
        assertThat(raised.out()).contains("due by");

        // The board hears about the stop, because a safety circuit stop with a hold is not routine.
        run(
                "board",
                "record",
                "JP",
                "stop circuit opened with the public on the line",
                "--kind",
                "electrical",
                "--by",
                "D Chin",
                "--at",
                bad + " 11:30");
        Reply filed = run("board", "file", "1", "--kind", "incident", "--by", "M Vetter", "--at", bad + " 16:00");
        assertThat(filed.out()).contains("UT-2025-0001");

        // The work is done and the order closes.
        Reply board = run("upkeep", "board", "--on", DAYS.get(3).toString());
        assertThat(board.out()).contains("corroded");
        run("upkeep", "close", "1", "--by", "R Skaggs", "--on", DAYS.get(3).toString());
        assertThat(run("upkeep", "board").out()).contains("the board is clear");

        // May: the season is added up and the packet goes to the state.
        Reply summary = run("report", "season", "2024-25", "--opens", OPENS.toString(), "--closes", CLOSES.toString());
        assertThat(summary.out()).contains("SEASON SUMMARY 2024-25", "JP");

        Reply packet = invoke(
                "report",
                "packet",
                "2024-25",
                "--opens",
                OPENS.toString(),
                "--closes",
                CLOSES.toString(),
                "--by",
                "M Vetter",
                "--on",
                "2025-05-12");
        assertThat(packet.out())
                .contains("ANNUAL PACKET 2024-25", "UT-2025-0001")
                .doesNotContain("NOT FILED");
    }

    @Test
    void whatTheOfficeTypedIsWhatTheTerminalTabletReads() {
        Http tablet = tablet();
        signOffTheLadder("JP");
        openTheDay(FIRST);

        Http.Answer day = tablet.get("/lifts/JP/days/" + FIRST, Wire.headers());
        assertThat(day.status()).isEqualTo(200);
        JsonNode read = day.json();
        assertThat(read.get("sheet_walked")).isEqualTo(Http.TRUE);
        assertThat(read.get("may_open")).isEqualTo(Http.TRUE);
        assertThat(read.at("/meter/cycles").isIntegralNumber()).isTrue();
        assertThat(read.at("/meter/cycles").asLong()).isEqualTo(1_400_000);

        JsonNode listed = tablet.get("/lifts/JP/days", Wire.headers()).json();
        assertThat(listed.get("items")).isEqualTo(Http.tree("[\"" + FIRST + "\"]"));
        assertThat(listed.get("total").asInt()).isEqualTo(1);
    }

    @Test
    void whatTheTabletWritesIsWhatTheOfficeReadsBack() {
        Http tablet = tablet();
        signOffTheLadder("JP");
        openTheDay(FIRST);

        Http.Answer stopped = tablet.postJson(
                "/lifts/JP/stops",
                Map.of("kind", "loading gate", "stopped", FIRST + "T11:30:00", "stopped_by", "D Chin"),
                Wire.headers(Role.OPERATOR));
        assertThat(stopped.status()).isEqualTo(201);
        assertThat(tablet.get("/stops/open", Wire.headers())
                        .json()
                        .get(0)
                        .get("lift")
                        .asText())
                .isEqualTo("JP");

        Reply shown = run("day", "open-stops");
        assertThat(shown.out()).contains("JP", "loading gate");

        Http.Answer restarted = tablet.postJson(
                "/lifts/JP/stops/restart",
                Map.of("stopped", FIRST + "T11:30:00", "restarted", FIRST + "T11:45:00", "restarted_by", "D Chin"),
                Wire.headers(Role.OPERATOR));
        assertThat(restarted.json().get("length").asText()).isEqualTo("0:15:00");
        assertThat(run("day", "open-stops").out()).contains("nothing is stopped");
    }

    @Test
    void theSeasonArithmeticIsTheSameWhicheverDoorItCameInBy() {
        Http tablet = tablet();
        signOffTheLadder("JP");
        for (LocalDate day : DAYS.subList(0, 3)) {
            openTheDay(day);
        }
        String second = DAYS.get(1).toString();
        Http.Answer stopped = tablet.postJson(
                "/lifts/JP/stops",
                Map.of("kind", "loading gate", "stopped", second + "T11:00:00", "stopped_by", "D Chin"),
                Wire.headers(Role.OPERATOR));
        assertThat(stopped.status()).isEqualTo(201);
        // Fifteen minutes, because at twenty the line has to be walked before it runs.
        Http.Answer restarted = tablet.postJson(
                "/lifts/JP/stops/restart",
                Map.of("stopped", second + "T11:00:00", "restarted", second + "T11:15:00", "restarted_by", "D Chin"),
                Wire.headers(Role.OPERATOR));
        assertThat(restarted.status()).isEqualTo(200);
        String summary = run("report", "season", "2024-25", "--opens", OPENS.toString(), "--closes", CLOSES.toString())
                .out();
        assertThat(summary).contains("0.2 h", "loading gate           1");
    }

    @Test
    void aMachineThatHasNotBeenDrilledIsNamedInThePacket() {
        signOffTheLadder("JP");
        Reply packet = refuse(
                "report",
                "packet",
                "2024-25",
                "--opens",
                OPENS.toString(),
                "--closes",
                CLOSES.toString(),
                "--by",
                "M Vetter",
                "--on",
                "2025-05-12");
        assertThat(packet.out()).contains("SB has");
    }

    @Test
    void theExportHoldsTheSameNumbersThePagePrinted() throws IOException {
        signOffTheLadder("JP");
        for (LocalDate day : DAYS.subList(0, 2)) {
            openTheDay(day);
        }
        Path made = tmp.resolve("days.csv");
        run(
                "report",
                "export",
                "days",
                "--opens",
                OPENS.toString(),
                "--closes",
                CLOSES.toString(),
                "--to",
                made.toString());
        List<String> rows = Files.readAllLines(made, StandardCharsets.UTF_8);
        assertThat(rows.get(0)).startsWith("lift,day,opened");
        assertThat(rows).hasSize(3);
        assertThat(rows.subList(1, rows.size())).allMatch(one -> one.startsWith("JP,2025-01-1"));
    }
}
