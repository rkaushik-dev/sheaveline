package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.Office.Machine;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliBoardTest {

    private static final String AT = "2025-01-15 11:30";

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

    private Shell.Reply recorded(String kind, String injury, String code, String at) {
        return run(
                "board",
                "record",
                code,
                "skier caught a ski under the bail on loading",
                "--kind",
                kind,
                "--injury",
                injury,
                "--by",
                "D Chin",
                "--at",
                at);
    }

    private Shell.Reply recorded(String kind) {
        return recorded(kind, "none", "JP", AT);
    }

    private Shell.Reply recorded() {
        return recorded("loading");
    }

    private Shell.Reply filed(String kind, String at) {
        return run("board", "file", "1", "--kind", kind, "--by", "R Skaggs", "--at", at);
    }

    @Test
    void aLoadingBumpGoesInTheMonthlyReturn() {
        Shell.Reply reply = recorded();
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("incident 1", "file by");
    }

    @Test
    void aDeropementHasToBeFiledAtOnceAndStopsTheMachine() {
        Shell.Reply reply = recorded("deropement");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("does not run again until the board says so", "2025-01-15 11:30");
    }

    @Test
    void aSeriousInjuryShortensTheDeadlineOnARoutineKind() {
        String routine = recorded("loading", "none", "JP", AT).output();
        String hurt = recorded("loading", "admitted", "JP", "2025-01-15 12:30").output();
        assertThat(hurt).contains("driven by");
        assertThat(routine).isNotEqualTo(hurt);
    }

    @Test
    void aMachineThatIsNotOnTheRegister() {
        assertThat(recorded("loading", "none", "ZZ", AT).code()).isEqualTo(1);
    }

    @Test
    void whatIsWaitingToBeFiled() {
        assertThat(run("board", "unfiled").output()).contains("nothing is waiting");
        recorded("deropement");
        Shell.Reply reply = run("board", "unfiled", "--at", "2025-01-15 12:00");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("deropement");
    }

    @Test
    void anIncidentPastItsDeadlineIsMarkedLate() {
        recorded("deropement");
        assertThat(run("board", "unfiled", "--at", "2025-02-01 12:00").output()).contains("LATE");
    }

    @Test
    void filingTakesANumberAndTheIncidentComesOffTheWaitingList() {
        recorded("deropement");
        Shell.Reply filed = filed("incident", "2025-01-15 12:00");
        assertThat(filed.code()).isZero();
        assertThat(filed.output()).contains("UT-2025-0001");
        assertThat(run("board", "unfiled").output()).contains("nothing is waiting");
    }

    @Test
    void aLateFilingSaysHowLateItWas() {
        recorded("deropement");
        assertThat(filed("incident", "2025-01-20 12:00").output()).contains("late by");
    }

    @Test
    void aRopeChangeFilingWaitsForAnAcknowledgement() {
        recorded();
        assertThat(filed("rope_change", "2025-01-16 09:00").output()).contains("watch for it coming back");
    }

    @Test
    void theSameIncidentIsNotFiledTwice() {
        recorded();
        filed("incident", AT);
        Shell.Reply again = filed("incident", AT);
        assertThat(again.code()).isEqualTo(1);
        assertThat(again.output()).contains("already filed");
    }

    @Test
    void filingSomethingNobodyRecorded() {
        Shell.Reply reply = run("board", "file", "9", "--kind", "incident", "--by", "R Skaggs");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no incident 9");
    }

    @Test
    void oneMachinesRegister() {
        assertThat(run("board", "show", "SB").output()).contains("nothing is on the register for SB");
        recorded();
        Shell.Reply reply = run("board", "show", "JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("not filed");
    }

    @Test
    void theMonthlyReturnListsTheMonthItWasAskedAbout() {
        recorded("loading", "none", "JP", "2025-01-15 11:30");
        recorded("loading", "none", "JP", "2025-02-15 11:30");
        Shell.Reply reply = run("board", "return", "2025", "1");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("incidents 1");
    }

    @Test
    void aDecemberReturnDoesNotRunOffTheEndOfTheYear() {
        recorded("loading", "none", "JP", "2024-12-20 11:30");
        Shell.Reply reply = run("board", "return", "2024", "12");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("incidents 1");
    }
}
