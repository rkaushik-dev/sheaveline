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

class CliEvacTest {

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

    private Shell.Reply drilled(String code, String kind, int carriers, String on) {
        return run(
                "evac",
                "drill",
                code,
                "--kind",
                kind,
                "--team",
                "alpha",
                "--team",
                "bravo",
                "--carriers",
                Integer.toString(carriers),
                "--minutes",
                "48",
                "--observer",
                "R Skaggs",
                "--on",
                on);
    }

    private Shell.Reply drilled(String code) {
        return drilled(code, "full_line", 6, ON);
    }

    private Shell.Reply staging(String... extra) {
        List<String> line = new ArrayList<>(List.of("evac", "staging", "JP"));
        line.addAll(List.of(extra));
        line.addAll(List.of("--on", ON));
        return run(line.toArray(String[]::new));
    }

    @Test
    void aFullLineDrillCarriesTheAnnual() {
        Shell.Reply reply = drilled("JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("the annual now runs to 2026-01-15");
    }

    @Test
    void aDrillThatWorkedTooFewCarriersDoesNot() {
        Shell.Reply reply = drilled("JP", "full_line", 2, ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("does not satisfy the annual");
    }

    @Test
    void aTabletopDoesNotSatisfyTheAnnualEither() {
        Shell.Reply reply = drilled("JP", "tabletop", 0, ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("does not satisfy the annual");
    }

    @Test
    void theSameMachineIsNotDrilledTwiceInADay() {
        drilled("JP");
        Shell.Reply again = drilled("JP");
        assertThat(again.code()).isEqualTo(1);
        assertThat(again.output()).contains("already drilled");
    }

    /** The register refuses a drill on a machine it has never heard of, and says so as the database. */
    @Test
    void aMachineThatIsNotOnTheRegister() {
        assertThat(drilled("ZZ").code()).isEqualTo(Main.NOT_ANSWERING);
    }

    @Test
    void aHillThatHasNeverDrilledIsOutOfDate() {
        Shell.Reply reply = run("evac", "check", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("OUT OF DATE", "does not carry the public", "never");
    }

    @Test
    void aDrilledMachineReadsCurrent() {
        drilled("JP");
        drilled("SB");
        Shell.Reply reply = run("evac", "check", "--on", ON);
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).doesNotContain("OUT OF DATE");
        assertThat(reply.count("current")).isEqualTo(2);
    }

    @Test
    void aDrillThatHasRunOutReadsOutOfDateAgain() {
        drilled("JP", "full_line", 6, "2024-01-15");
        Shell.Reply reply = run("evac", "check", "--on", ON);
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("OUT OF DATE");
    }

    @Test
    void theStagingPlanParksEveryTeamOnItsOwnRun() {
        Shell.Reply reply = staging("--team", "alpha:24:200", "--team", "bravo:24:200");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("2 teams, 84 carriers", "alpha", "bravo");
    }

    @Test
    void iceOnTheRopeIsTakenIntoTheStaging() {
        Shell.Reply bare = staging("--team", "alpha:24:200", "--team", "bravo:24:200");
        Shell.Reply iced = staging("--team", "alpha:24:200", "--team", "bravo:24:200", "--ice", "2 in");
        assertThat(iced.code()).isEqualTo(bare.code());
        assertThat(iced.output()).contains("2 teams, 84 carriers").isNotEqualTo(bare.output());
        assertThat(staging("--team", "alpha:24:200", "--ice", "-1 in").code()).isNotZero();
        assertThat(run("evac", "staging", "--help").output()).contains("--ice");
    }

    @Test
    void aTeamWrittenTheWrongWay() {
        Shell.Reply reply = staging("--team", "alpha 24 200");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("name:ladder:rope");
    }

    /** A dash means no rope on the truck, and on this line that is the whole job. */
    @Test
    void aLadderAloneDoesNotReachAChairHangingFortyFeetUp() {
        Shell.Reply reply = staging("--team", "alpha:24:-");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("nobody can work this line", "nobody reaches it");
    }

    @Test
    void theSameTeamWithARopeOnTheTruckCanWorkIt() {
        Shell.Reply reply = staging("--team", "alpha:24:200");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("alpha", "1 teams, 84 carriers");
    }

    @Test
    void aShortTeamCannotReachTheWholeLine() {
        Shell.Reply reply = staging("--team", "alpha:8:-");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("nobody reaches it");
    }

    @Test
    void moreSeatsFilledMakesTheTeamsBusier() {
        Shell.Reply quiet = staging("--team", "alpha:24:200", "--seats", "2");
        Shell.Reply busy = staging("--team", "alpha:24:200", "--seats", "4");
        assertThat(quiet.code()).isZero();
        assertThat(busy.code()).isZero();
        assertThat(quiet.output()).isNotEqualTo(busy.output());
    }
}
