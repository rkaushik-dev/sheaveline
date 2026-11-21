package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.Office.Machine;
import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.reports.Forms;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliReportTest {

    private static final String ON = "2025-01-15";
    private static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");

    @TempDir
    Path tmp;

    private final Shell shell = new Shell();

    private String database;

    @BeforeEach
    void twoMachinesOnTheRegister() {
        database = Office.stocked(tmp, Machine.JOHN_PAUL, Machine.STRAWBERRY);
    }

    private Shell.Reply run(String... args) {
        return shell.on(database, args);
    }

    /** A day in the book with the whole sheet walked and passed. */
    private void writeUp(Lift machine, LocalDate day) {
        List<CheckResult> results = new ArrayList<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(machine)) {
                results.add(new CheckResult(item, CheckOutcome.PASS, "R Skaggs"));
            }
        }
        PreOpSheet sheet = new PreOpSheet(machine, day, results, "R Skaggs", "D Chin");
        Shell.change(database, work -> work.dayLogs().add(DayLog.start(machine.code(), day, WINDOW, sheet)));
    }

    private Shell.Reply export(String sheet, String... extra) {
        List<String> line =
                new ArrayList<>(List.of("report", "export", sheet, "--opens", "2024-12-01", "--closes", "2025-04-15"));
        line.addAll(List.of(extra));
        return run(line.toArray(String[]::new));
    }

    private Shell.Reply packet(String... extra) {
        List<String> line = new ArrayList<>(List.of(
                "report", "packet", "2024-25", "--opens", "2024-12-01", "--closes", "2025-04-15", "--by", "R Skaggs"));
        line.addAll(List.of(extra));
        return run(line.toArray(String[]::new));
    }

    private static List<String> written(String text) {
        return text.lines().filter(one -> !one.isEmpty()).toList();
    }

    @Test
    void theMorningPagePrintsEightyColumns() {
        Shell.Reply reply = run("report", "morning", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("John Paul");
        assertThat(reply.lines()).allMatch(one -> one.length() <= Forms.PAGE_WIDTH);
    }

    @Test
    void theMorningPageNamesWhatIsStoppingAMachine() {
        assertThat(run("report", "morning", "--on", ON).output()).contains("morning walk not signed");
    }

    @Test
    void theSnowReportGoesOnThePage() {
        assertThat(run("report", "morning", "--on", ON, "--snow", "eight overnight")
                        .output())
                .contains("eight overnight");
    }

    @Test
    void theLineWalkIsAskedForByNamingATension() {
        Shell.Reply plain = run("report", "morning", "--on", ON);
        Shell.Reply walked = run("report", "morning", "--on", ON, "--tension", "34500 lb");
        assertThat(plain.code()).isZero();
        assertThat(walked.code()).isZero();
        assertThat(walked.output()).isNotEqualTo(plain.output());
    }

    @Test
    void aTensionThatWillNotRead() {
        Shell.Reply reply = run("report", "morning", "--on", ON, "--tension", "hard");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("hard");
    }

    @Test
    void theSeasonPageAddsUpWhatWasWrittenDown() {
        for (int offset = 0; offset < 3; offset++) {
            writeUp(Hill.johnPaul(), LocalDate.of(2025, 1, 15).plusDays(offset));
        }
        Shell.Reply reply = run("report", "season", "2024-25", "--opens", "2024-12-01", "--closes", "2025-04-15");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("SEASON SUMMARY 2024-25", "by machine");
        assertThat(reply.lines()).allMatch(one -> one.length() <= Forms.PAGE_WIDTH);
    }

    @Test
    void aSeasonThatClosesBeforeItOpens() {
        Shell.Reply reply = run("report", "season", "2024-25", "--opens", "2025-04-15", "--closes", "2024-12-01");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("closes before it opens");
    }

    @Test
    void theRopeFileStillPrintsFromItsOwnGroup() {
        assertThat(run("rope", "list").code()).isZero();
    }

    @Test
    void theAnnualPacketPrintsAndSaysWhatIsOutstanding() {
        Shell.Reply reply = packet("--on", "2025-05-12");
        assertThat(reply.output()).contains("ANNUAL PACKET 2024-25", "outstanding");
        assertThat(reply.lines()).allMatch(one -> one.length() <= Forms.PAGE_WIDTH);
    }

    @Test
    void aPacketWithSomethingOutstandingLeavesABadExitCode() {
        Shell.Reply reply = packet("--on", "2025-05-12");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("inspections overdue");
    }

    @Test
    void thePacketNamesTheRopesOnTheMachines() {
        assertThat(packet().output()).contains("never walked", "FAT-2019-04412");
    }

    @Test
    void everySheetExportsAtLeastItsHeading() {
        for (String sheet : List.of("days", "stops", "incidents", "wear")) {
            Shell.Reply reply = export(sheet);
            assertThat(reply.code()).as(sheet).isZero();
            assertThat(reply.lines().get(0)).as(sheet).contains(",");
        }
    }

    @Test
    void aSheetNobodyHas() {
        Shell.Reply reply = export("ropeway");
        assertThat(reply.code()).isEqualTo(2);
        assertThat(reply.output()).contains("is not one of");
    }

    @Test
    void theDaysThatWereWrittenUpComeOutAsRows() {
        for (int offset = 0; offset < 3; offset++) {
            writeUp(Hill.johnPaul(), LocalDate.of(2025, 1, 15).plusDays(offset));
        }
        Shell.Reply reply = export("days");
        assertThat(reply.code()).isZero();
        List<String> lines = written(reply.output());
        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).startsWith("lift,day,opened");
        assertThat(lines.get(1)).startsWith("JP,2025-01-15");
    }

    @Test
    void writingToAFileSaysHowManyRowsWentIn() throws IOException {
        writeUp(Hill.johnPaul(), LocalDate.of(2025, 1, 15));
        Path made = tmp.resolve("days.csv");
        Shell.Reply reply = export("days", "--to", made.toString());
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("1 rows to " + made);
        assertThat(Files.readAllLines(made).get(0)).startsWith("lift,day");
    }

    @Test
    void aDayOutsideTheSeasonDoesNotComeOut() {
        writeUp(Hill.johnPaul(), LocalDate.of(2025, 8, 1));
        assertThat(written(export("days").output())).hasSize(1);
    }
}
