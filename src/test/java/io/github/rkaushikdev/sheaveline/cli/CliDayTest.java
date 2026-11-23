package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.Office.Machine;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDayTest {

    private static final String ON = "2025-01-15";

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

    private Shell.Reply started(String code, String... extra) {
        List<String> line = new ArrayList<>(
                List.of("day", "start", code, "--on", ON, "--mechanic", "R Skaggs", "--operator", "D Chin"));
        line.addAll(List.of(extra));
        return run(line.toArray(String[]::new));
    }

    private Shell.Reply started() {
        return started("JP");
    }

    @Test
    void startingTheDayOpensTheBook() {
        Shell.Reply reply = started();
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("JP 2025-01-15 open, 9:00 to 16:30");
    }

    @Test
    void aDayStartedWithoutASheetSaysItMayNotRun() {
        Shell.Reply reply = run("day", "start", "JP", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("no sheet walked yet");
    }

    @Test
    void aFailedCriticalCheckIsNamedAsABlocker() {
        Shell.Reply reply = started("JP", "--fail", "b1:sheave train two is dry");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("blocker", "B1 failed");
    }

    @Test
    void aFailedCheckWillNotGoInWithoutAReason() {
        Shell.Reply reply = started("JP", "--fail", "b1");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("CODE:reason");
    }

    @Test
    void theSameDayWillNotBeStartedTwice() {
        started();
        Shell.Reply reply = started();
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("already has a log");
    }

    @Test
    void aMachineThatIsNotOnTheRegister() {
        Shell.Reply reply = started("ZZ");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no lift ZZ");
    }

    @Test
    void aNoteGoesInTheBookAndComesBackOut() {
        started();
        Shell.Reply reply = run("day", "note", "JP", "10:15", "ramp regroomed", "--by", "D Chin", "--on", ON);
        assertThat(reply.code()).isZero();
        Shell.Reply shown = run("day", "show", "JP", "--on", ON);
        assertThat(shown.output()).contains("ramp regroomed", "10:15");
    }

    @Test
    void aStopAndARestart() {
        started();
        Shell.Reply stopped = run("day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON);
        assertThat(stopped.code()).isZero();
        Shell.Reply back = run("day", "restart", "JP", "11:30", "11:45", "--by", "D Chin", "--on", ON);
        assertThat(back.code()).isZero();
        assertThat(back.output()).contains("JP running again, down 0:15:00");
    }

    @Test
    void aStopLongEnoughToIceAGripWantsTheLineWalked() {
        started();
        run("day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON);
        Shell.Reply refused = run("day", "restart", "JP", "11:30", "12:05", "--by", "D Chin", "--on", ON);
        assertThat(refused.code()).isEqualTo(1);
        assertThat(refused.output()).contains("line check");
        Shell.Reply walked =
                run("day", "restart", "JP", "11:30", "12:05", "--by", "D Chin", "--on", ON, "--line-checked");
        assertThat(walked.code()).isZero();
    }

    @Test
    void aThirdGateTripInsideTheHourCallsTheMechanicAndWantsTheLineWalked() {
        started();
        for (String[] trip : new String[][] {{"11:00", "11:02"}, {"11:20", "11:21"}}) {
            run("day", "stop", "JP", trip[0], "--kind", "gate", "--by", "D Chin", "--on", ON);
            assertThat(run("day", "restart", "JP", trip[0], trip[1], "--by", "D Chin", "--on", ON)
                            .code())
                    .isZero();
        }
        Shell.Reply third = run("day", "stop", "JP", "11:40", "--kind", "gate", "--by", "D Chin", "--on", ON);
        assertThat(third.output()).contains("3 trips inside the hour: call the mechanic");
        Shell.Reply refused = run("day", "restart", "JP", "11:40", "11:42", "--by", "D Chin", "--on", ON);
        assertThat(refused.code()).isEqualTo(1);
        assertThat(refused.output()).contains("mechanic walks the line");
        Shell.Reply walked =
                run("day", "restart", "JP", "11:40", "11:50", "--by", "R Skaggs", "--on", ON, "--line-checked");
        assertThat(walked.code()).isZero();
    }

    @Test
    void aDeropementSaysTheLineGetsWalked() {
        started();
        Shell.Reply reply = run(
                "day",
                "stop",
                "JP",
                "11:30",
                "--kind",
                "deropement",
                "--by",
                "D Chin",
                "--note",
                "grip off at tower four",
                "--on",
                ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("the line is walked before this one runs again");
        Shell.Reply refused = run("day", "restart", "JP", "11:30", "12:05", "--by", "R Skaggs", "--on", ON);
        assertThat(refused.code()).isEqualTo(1);
        assertThat(refused.output()).contains("line check");
    }

    @Test
    void aReportableStopWillNotGoInWithoutAWriteUp() {
        started();
        Shell.Reply reply = run("day", "stop", "JP", "11:30", "--kind", "evacuation", "--by", "D Chin", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("written up");
    }

    @Test
    void aStopThatRanOvernightRestartsTheNextMorning() {
        started();
        run("day", "stop", "JP", "16:05", "--kind", "power", "--by", "D Chin", "--on", ON);
        Shell.Reply reply = run(
                "day",
                "restart",
                "JP",
                "16:05",
                "08:40",
                "--by",
                "R Skaggs",
                "--on",
                ON,
                "--next-day",
                "--line-checked");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("16:35:00");
    }

    @Test
    void theMeterIsWrittenDown() {
        started();
        Shell.Reply reply = run("day", "meter", "JP", "40120.5", "1402000", "--by", "R Skaggs", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("40120.5");
    }

    @Test
    void aReadingSaysTheHoursItAddsAndAMisreadIsRefused() {
        started();
        assertThat(run("day", "meter", "JP", "40120.5", "1402000", "--by", "R Skaggs", "--on", ON)
                        .output())
                .contains("0 hours since the last reading");
        Shell.Reply next = run("day", "meter", "JP", "40129.0", "1406000", "--by", "R Skaggs", "--on", "2025-01-16");
        assertThat(next.code()).isZero();
        assertThat(next.output()).contains("40129.0", "8.5 hours since the last reading");
        Shell.Reply misread = run("day", "meter", "JP", "40120.0", "1410000", "--by", "R Skaggs", "--on", "2025-01-17");
        assertThat(misread.code()).isEqualTo(1);
        assertThat(misread.output()).contains("misread rather than a wrap");
    }

    @Test
    void whatIsStoppedRightNow() {
        started();
        assertThat(run("day", "open-stops").output()).contains("nothing is stopped");
        run("day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON);
        Shell.Reply reply = run("day", "open-stops");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("JP", "2025-01-15 11:30");
    }

    @Test
    void openStopsCanBeAskedAboutOneMachine() {
        run("day", "stop", "JP", "11:30", "--by", "D Chin", "--on", ON);
        assertThat(run("day", "open-stops", "--lift", "SB").output()).contains("nothing is stopped");
    }

    @Test
    void showingADayNobodyWroteUp() {
        Shell.Reply reply = run("day", "show", "JP", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no log for JP on 2025-01-15");
    }
}
