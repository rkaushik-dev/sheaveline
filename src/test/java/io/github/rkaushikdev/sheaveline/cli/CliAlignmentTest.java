package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.Office.Machine;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliAlignmentTest {

    private static final String AUTUMN = "2024-10-01";

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

    /** One shot a tower, each where the drawing says unless it is pushed over or let down. */
    private static List<String> shots(Map<String, Integer> offsets, Map<String, Integer> settled) {
        List<String> given = new ArrayList<>();
        for (Tower tower : Hill.jpTowers()) {
            String ref = tower.ref().toString();
            BigDecimal crossarm = tower.crossarmElevation()
                    .feet()
                    .subtract(Decimals.divide(BigDecimal.valueOf(settled.getOrDefault(ref, 0)), 12));
            given.add("--shot");
            given.add(ref + ":" + Decimals.fixed(tower.station().feet(), 0) + ":" + Decimals.fixed(crossarm, 3) + ":"
                    + offsets.getOrDefault(ref, 0));
        }
        return given;
    }

    private Shell.Reply shoot(String on, Map<String, Integer> offsets, Map<String, Integer> settled) {
        List<String> line = new ArrayList<>(List.of("lift", "shoot", "JP", "--surveyor", "H Ogilvie", "--on", on));
        line.addAll(shots(offsets, settled));
        return run(line.toArray(String[]::new));
    }

    private Shell.Reply shoot(String on, Map<String, Integer> offsets) {
        return shoot(on, offsets, Map.of());
    }

    private Shell.Reply shoot(String on) {
        return shoot(on, Map.of());
    }

    @Test
    void aLineShotWhereTheDrawingSaysItIsReadsClean() {
        Shell.Reply reply = shoot(AUTUMN);
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("7 towers", "every tower is where the drawing says it is");
    }

    @Test
    void aTowerOutOfPlumbIsNamedOnTheWayIn() {
        Shell.Reply reply = shoot(AUTUMN, Map.of("JP-T4", 18));
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("JP-T4 leans");
    }

    @Test
    void aTowerThatHasSettledIsNamedToo() {
        Shell.Reply reply = shoot(AUTUMN, Map.of(), Map.of("JP-T5", 4));
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("JP-T5 has settled");
    }

    @Test
    void aShotWrittenTheWrongWay() {
        Shell.Reply reply = run("lift", "shoot", "JP", "--surveyor", "H Ogilvie", "--shot", "JP-T4 1200 7040 0");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.err()).contains("tower:station:crossarm:offset");
    }

    @Test
    void theSameMachineIsNotShotTwiceInADay() {
        shoot(AUTUMN);
        Shell.Reply again = shoot(AUTUMN);
        assertThat(again.code()).isEqualTo(1);
        assertThat(again.err()).contains("already shot");
    }

    @Test
    void aTowerFromAnotherMachineDoesNotBelongInTheSurvey() {
        Shell.Reply reply = run("lift", "shoot", "JP", "--surveyor", "H Ogilvie", "--shot", "SB-T2:600:6500:0");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.err()).contains("not on JP");
    }

    @Test
    void aMachineNobodyHasShot() {
        Shell.Reply reply = run("lift", "drift", "JP");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.err()).contains("has never been shot");
    }

    @Test
    void oneSurveyCarriesNoRate() {
        shoot(AUTUMN);
        Shell.Reply reply = run("lift", "drift", "JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("read across 1 surveys", "not moving");
    }

    @Test
    void twoSurveysGiveARateAndAProjection() {
        shoot("2020-10-01");
        shoot("2024-10-01", Map.of("JP-T4", 4));
        Shell.Reply reply = run("lift", "drift", "JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.out()).contains("read across 2 surveys", "JP-T4", "flat");
    }

    @Test
    void aTowerGoingOverFastIsCalledOut() {
        shoot("2023-10-01");
        shoot("2024-10-01", Map.of("JP-T4", 10));
        assertThat(run("lift", "drift", "JP").out()).contains("JP-T4 is out of plumb inside five years at this rate");
    }

    @Test
    void aLineThatHasNotMovedIsNotCalledOut() {
        shoot("2020-10-01");
        shoot("2024-10-01");
        assertThat(run("lift", "drift", "JP").out()).doesNotContain("out of plumb inside five years");
    }
}
