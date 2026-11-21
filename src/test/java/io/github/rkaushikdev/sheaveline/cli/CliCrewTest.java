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

class CliCrewTest {

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

    private Shell.Reply carded(String person, String card, String expires, String... extra) {
        List<String> line = new ArrayList<>(List.of(
                "crew",
                "card",
                person,
                card,
                "--issued",
                "2024-11-01",
                "--expires",
                expires,
                "--by",
                "Utah tramway board"));
        line.addAll(List.of(extra));
        return run(line.toArray(String[]::new));
    }

    private Shell.Reply carded(String person, String card) {
        return carded(person, card, "2025-11-01");
    }

    private Shell.Reply carded() {
        return carded("D Chin", "operator");
    }

    private Shell.Reply manned(String person, String post, String code) {
        return run("crew", "assign", code, person, post, "--on", ON);
    }

    @Test
    void aCardGoesInTheFile() {
        Shell.Reply reply = carded();
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("D Chin");
    }

    @Test
    void aCardCanBeLimitedToSomeClasses() {
        Shell.Reply reply = carded("T Woo", "operator", "2025-11-01", "--only", "surface_tow");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("covers   surface lift");
    }

    @Test
    void aClassNobodyRuns() {
        Shell.Reply reply = carded("T Woo", "operator", "2025-11-01", "--only", "funicular");
        assertThat(reply.code()).isEqualTo(2);
        assertThat(reply.output()).contains("is not one of");
    }

    @Test
    void theSameCardIsNotIssuedTwice() {
        carded();
        Shell.Reply again = carded();
        assertThat(again.code()).isEqualTo(1);
        assertThat(again.output()).contains("already has");
    }

    @Test
    void theFileListsWhoIsCardedForWhat() {
        assertThat(run("crew", "cards").output()).contains("nothing is on file");
        carded();
        Shell.Reply reply = run("crew", "cards", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("D Chin", "current");
    }

    @Test
    void aLapsedCardIsMarkedLapsed() {
        carded("D Chin", "operator", "2024-12-01");
        assertThat(run("crew", "cards", "--on", ON).output()).contains("lapsed");
    }

    @Test
    void aCardRunningOutSoonIsCalledOut() {
        carded("D Chin", "operator", "2025-02-01");
        assertThat(run("crew", "cards", "--on", ON, "--expiring", "45").output())
                .contains("runs out 2025-02-01");
    }

    @Test
    void renewingWritesANewCardAndKeepsTheOld() {
        carded();
        Shell.Reply reply =
                run("crew", "renew", "D Chin", "operator", "--expires", "2026-11-01", "--by", "Utah tramway board");
        assertThat(reply.code()).isZero();
        assertThat(run("crew", "cards", "--person", "D Chin", "--on", ON).count("D Chin"))
                .isEqualTo(2);
    }

    @Test
    void renewingACardNobodyHolds() {
        Shell.Reply reply =
                run("crew", "renew", "T Woo", "operator", "--expires", "2026-11-01", "--by", "Utah tramway board");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("no operator card to renew");
    }

    @Test
    void aShiftGoesOnTheBoard() {
        Shell.Reply reply = manned("D Chin", "bottom", "JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("D Chin on JP at bottom operator");
    }

    @Test
    void somebodyDownOnTwoMachinesAtOnceIsCalledOut() {
        manned("D Chin", "bottom", "JP");
        Shell.Reply reply = manned("D Chin", "bottom", "SB");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("also down on JP");
    }

    @Test
    void anEmptyRosterIsShortOfEverything() {
        Shell.Reply reply = run("crew", "gap", "JP", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("SHORT", "cannot be staffed");
    }

    @Test
    void aPostFilledBySomebodyUncardedIsStillShort() {
        manned("D Chin", "bottom", "JP");
        Shell.Reply reply = run("crew", "gap", "JP", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("D Chin");
    }

    @Test
    void theGapNamesWhoCouldCover() {
        carded("T Woo", "lead_operator");
        assertThat(run("crew", "gap", "JP", "--on", ON).output()).contains("cover: T Woo");
    }

    @Test
    void withNobodyCardedTheCoverLineSaysSo() {
        assertThat(run("crew", "gap", "JP", "--on", ON).output()).contains("nobody carded");
    }

    @Test
    void withNoMachineItWalksTheWholeRegister() {
        assertThat(run("crew", "gap", "--on", ON).output()).contains("JP", "SB", "2 machines cannot be staffed");
    }

    @Test
    void aFullyCardedAndMannedMachineIsLegal() {
        carded("D Chin", "operator");
        carded("T Woo", "lead_operator");
        manned("D Chin", "bottom", "JP");
        manned("T Woo", "top", "JP");
        Shell.Reply reply = run("crew", "gap", "JP", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("JP  legal");
    }
}
