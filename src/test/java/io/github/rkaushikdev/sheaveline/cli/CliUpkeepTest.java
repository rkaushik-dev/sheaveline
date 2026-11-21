package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.Office.Machine;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliUpkeepTest {

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

    private Shell.Reply found(String defect) {
        return run("upkeep", "record", "JP", "A1", "--by", "R Skaggs", "--defect", defect, "--on", ON);
    }

    @Test
    void aMachineWithNoRecordsHasTheWholeLadderDue() {
        Shell.Reply reply = run("upkeep", "due", "JP", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("overdue", "out of service");
    }

    @Test
    void aJobSignedOffComesOffTheOverdueList() {
        Shell.Reply before = run("upkeep", "due", "JP", "--on", ON);
        run("upkeep", "record", "JP", "A1", "--by", "R Skaggs", "--on", ON);
        Shell.Reply after = run("upkeep", "due", "JP", "--on", ON);
        assertThat(before.count("overdue")).isGreaterThan(after.count("overdue"));
    }

    @Test
    void aJobThatIsNotOnThisMachine() {
        Shell.Reply reply = run("upkeep", "record", "JP", "ZZ", "--by", "R Skaggs", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no job ZZ");
    }

    @Test
    void aDefectIsWrittenDownWithTheJob() {
        Shell.Reply reply = found("JP-T4:defect:liner cracked on the outer sheave");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("liner cracked", "wants a work order raising");
    }

    @Test
    void anObservationDoesNotWantAWorkOrder() {
        Shell.Reply reply = found("JP-T2:observation:paint going at the base");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).doesNotContain("wants a work order raising");
    }

    @Test
    void aDefectThatIsNotWrittenTheWayTheSheetWritesThem() {
        Shell.Reply reply = found("JP-T4 liner cracked");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("where:level:what");
    }

    @Test
    void aLevelNobodyUses() {
        Shell.Reply reply = found("JP-T4:awful:liner cracked");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("observation");
    }

    @Test
    void theOpenDefectsAreListed() {
        found("JP-T4:defect:liner cracked on the outer sheave");
        Shell.Reply reply = run("upkeep", "defects");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("JP-T4", "liner cracked");
    }

    @Test
    void nothingOpenSaysSo() {
        assertThat(run("upkeep", "defects").output()).contains("nothing open");
    }

    @Test
    void aSeriousDefectRaisesAnOrderThatStopsTheMachine() {
        found("JP-DRV:serious:service brake will not hold");
        Shell.Reply reply = run("upkeep", "raise", "JP", "JP-DRV", "--by", "R Skaggs");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("does not carry the public");
    }

    @Test
    void anOrdinaryDefectRaisesAnOrderThatDoesNot() {
        found("JP-T4:defect:liner cracked on the outer sheave");
        Shell.Reply reply = run("upkeep", "raise", "JP", "JP-T4", "--by", "R Skaggs");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).doesNotContain("does not carry the public").contains("due by");
    }

    @Test
    void raisingAgainstADefectNobodyFound() {
        Shell.Reply reply = run("upkeep", "raise", "JP", "JP-T9", "--by", "R Skaggs");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("no open defect at JP-T9");
    }

    @Test
    void theBoardShowsTheOpenOrdersAndThenClears() {
        assertThat(run("upkeep", "board").output()).contains("the board is clear");
        found("JP-T4:defect:liner cracked on the outer sheave");
        run("upkeep", "raise", "JP", "JP-T4", "--by", "R Skaggs");
        Shell.Reply board = run("upkeep", "board", "--on", ON);
        assertThat(board.code()).isZero();
        assertThat(board.output()).contains("liner cracked");
        Shell.Reply closed = run("upkeep", "close", "1", "--by", "R Skaggs", "--on", ON);
        assertThat(closed.code()).isZero();
        assertThat(run("upkeep", "board").output()).contains("the board is clear");
    }

    @Test
    void theBoardCanBeAskedAboutOneMachine() {
        found("JP-T4:defect:liner cracked on the outer sheave");
        run("upkeep", "raise", "JP", "JP-T4", "--by", "R Skaggs");
        assertThat(run("upkeep", "board", "--lift", "SB").output()).doesNotContain("liner cracked");
    }

    @Test
    void closingAnOrderNobodyRaised() {
        Shell.Reply reply = run("upkeep", "close", "9", "--by", "R Skaggs", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no work order 9");
    }

    @Test
    void theLadderReadingNamesTheMachineItRead() {
        Shell.Reply reply = run("upkeep", "due", "JP", "--on", ON);
        assertThat(reply.output()).contains("JP John Paul", Hill.JOHN_PAUL.toString());
    }
}
