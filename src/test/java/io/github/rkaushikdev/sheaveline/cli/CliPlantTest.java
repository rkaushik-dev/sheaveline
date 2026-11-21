package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.reports.Forms;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliPlantTest {

    @TempDir
    Path tmp;

    private final Shell shell = new Shell();

    private String database;

    @BeforeEach
    void theWholeHill() {
        database = Office.stocked(tmp);
    }

    private Shell.Reply run(String... args) {
        return shell.on(database, args);
    }

    @Test
    void theListNamesEveryMachine() {
        Shell.Reply reply = run("lift", "list");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("John Paul", "Strawberry", "Wildcat");
    }

    @Test
    void theListCarriesTheClassAndTheCarrierCount() {
        assertThat(run("lift", "list").output()).contains("detachable chairlift", "84", "1000 fpm");
    }

    @Test
    void theListIsHeadedAndRuled() {
        List<String> lines = run("lift", "list").lines();
        assertThat(lines.get(0)).startsWith("code");
        assertThat(lines.get(1)).matches("[- ]+");
    }

    @Test
    void oneMachinePrintsAFullPage() {
        Shell.Reply reply = run("lift", "show", "JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.output())
                .contains(
                        "JP JOHN PAUL",
                        "class                    detachable chairlift",
                        "vertical rise            1540 ft",
                        "rope                     FAT-2019-04412",
                        "rope length agrees       yes");
    }

    @Test
    void thePageWorksOutTheCapacityRatherThanStoringIt() {
        assertThat(run("lift", "show", "JP").output()).contains("carrier interval", "people an hour");
    }

    @Test
    void aLowerCaseCodeWorks() {
        assertThat(run("lift", "show", "jp").code()).isZero();
    }

    @Test
    void aMachineThatIsNotThereIsASentenceAndAnExitCode() {
        Shell.Reply reply = run("lift", "show", "ZZ");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).doesNotContain("\tat ").contains("there is no lift ZZ");
    }

    @Test
    void somethingThatIsNotACodeIsRefusedTheSameWay() {
        Shell.Reply reply = run("lift", "show", "1A");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("a lift code");
    }

    @Test
    void theTowerScheduleListsEveryTower() {
        Shell.Reply reply = run("lift", "towers", "JP");
        assertThat(reply.code()).isZero();
        for (int number = 1; number < 8; number++) {
            assertThat(reply.output()).contains("JP-T" + number);
        }
    }

    @Test
    void theTowerScheduleCarriesTheCrossarmElevation() {
        List<String> lines = run("lift", "towers", "JP").lines();
        assertThat(lines.get(0)).contains("crossarm");
        assertThat(String.join("\n", lines)).contains("6567");
    }

    @Test
    void theSpansAreNumberedAndMarked() {
        Shell.Reply reply = run("lift", "spans", "JP");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("longest", "steepest", "John Paul base to");
    }

    @Test
    void everyPrintedLineFitsThePage() {
        for (List<String> args : List.of(
                List.of("lift", "list"),
                List.of("lift", "show", "JP"),
                List.of("lift", "towers", "JP"),
                List.of("lift", "spans", "JP"))) {
            assertThat(run(args.toArray(String[]::new)).lines())
                    .as(args.toString())
                    .allMatch(one -> one.length() <= Forms.PAGE_WIDTH);
        }
    }

    @Test
    void withdrawingAsksFirst() {
        Shell.Reply reply = shell.typing("n\n").on(database, "lift", "withdraw", "WC");
        assertThat(reply.code()).isNotZero();
        assertThat(run("lift", "list").count("Wildcat")).isEqualTo(1);
    }

    @Test
    void answeringYesTakesTheMachineOff() {
        Shell.Reply reply = shell.typing("y\n").on(database, "lift", "withdraw", "WC");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("WC withdrawn");
        assertThat(run("lift", "list").output()).doesNotContain("Wildcat");
    }

    @Test
    void theFlagSkipsTheQuestion() {
        Shell.Reply reply = run("lift", "withdraw", "WC", "--yes");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).doesNotContain("?");
    }

    @Test
    void withdrawingAMachineThatIsNotThere() {
        Shell.Reply reply = run("lift", "withdraw", "ZZ", "--yes");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no lift ZZ");
    }

    @Test
    void theGroupListsItsCommands() {
        assertThat(run("lift", "--help").output()).contains("list", "show", "towers", "spans", "withdraw");
    }

    @Test
    void thePageReadsTheDayTheConsoleThinksItIs() {
        String text = run("lift", "show", "JP").output();
        assertThat(text).contains("age ");
        assertThat(text.contains(Integer.toString(LocalDate.now().getYear() - 2004)) || text.contains("years"))
                .isTrue();
    }
}
