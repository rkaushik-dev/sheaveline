package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.reports.Forms;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRopeTest {

    private static final String SERIAL = "FAT-2019-04412";

    @TempDir
    Path tmp;

    private final Shell shell = new Shell();

    private String database;

    @BeforeEach
    void twoMachinesAndASpare() {
        database = Office.copied(tmp, "ropes", work -> {
            work.lifts().add(Hill.johnPaul());
            work.lifts().add(Hill.strawberry());
            work.ropes().add(Hill.wildcat().rope());
        });
    }

    private Shell.Reply run(String... args) {
        return shell.on(database, args);
    }

    private Shell.Reply walk(String taken, String... readings) {
        List<String> line =
                new ArrayList<>(List.of("rope", "walk", SERIAL, "--taken", taken, "--inspector", "R Halvorsen"));
        for (String one : readings) {
            line.add("--reading");
            line.add(one);
        }
        return run(line.toArray(String[]::new));
    }

    private Shell.Reply walked(String taken, String inches) {
        return walk(taken, "1200 ft:" + inches + " in:" + inches + " in");
    }

    private Shell.Reply forecast(String... extra) {
        List<String> line = new ArrayList<>(List.of("rope", "forecast"));
        line.addAll(List.of(extra));
        return run(line.toArray(String[]::new));
    }

    @Test
    void aReadingReadsInTheOrderItIsWrittenOnTheClipboard() {
        DiameterReading reading = RopeCommands.parseReading("1200 ft:1.352 in:1.348 in");
        assertThat(reading.station()).isEqualTo(Length.ofFeet(1200));
        assertThat(reading.acrossCrowns()).isEqualTo(Length.ofInches("1.352"));
        assertThat(reading.acrossValleys()).isEqualTo(Length.ofInches("1.348"));
    }

    @Test
    void aReadingWithTheWrongNumberOfPartsIsRefused() {
        assertThatThrownBy(() -> RopeCommands.parseReading("1200 ft:1.352 in")).isInstanceOf(ParseError.class);
        assertThatThrownBy(() -> RopeCommands.parseReading("1200:1.352:1.348:extra"))
                .isInstanceOf(ParseError.class);
    }

    @Test
    void theListNamesEveryRope() {
        Shell.Reply reply = run("rope", "list");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains(SERIAL, "BRI-2011-01887", "BRI-2016-00294");
    }

    @Test
    void theListCarriesTheConstructionAndTheAge() {
        assertThat(run("rope", "list").output()).contains("6x25 filler wire", "2019-10-04");
    }

    @Test
    void theSparesAreTheRopesOnNoMachine() {
        Shell.Reply reply = run("rope", "list", "--spares");
        assertThat(reply.output()).contains("BRI-2016-00294").doesNotContain(SERIAL);
    }

    @Test
    void theFilePrintsTheWayItGoesInTheFolder() {
        Shell.Reply reply = run("rope", "file", SERIAL);
        assertThat(reply.code()).isZero();
        assertThat(reply.output())
                .contains(
                        "ROPE FILE " + SERIAL, "on machine               JP John Paul", "nothing recorded against it");
    }

    @Test
    void aSpareRopePrintsAsInTheShop() {
        assertThat(run("rope", "file", "BRI-2016-00294").output()).contains("on machine               in the shop");
    }

    @Test
    void aRopeThatIsNotOnFile() {
        Shell.Reply reply = run("rope", "file", "FAT-2019-99999");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no rope").doesNotContain("\tat ");
    }

    @Test
    void somethingThatIsNotASerial() {
        Shell.Reply reply = run("rope", "file", "nonsense");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("a rope serial");
    }

    @Test
    void aWalkIsRecordedAndTheVerdictPrinted() {
        Shell.Reply reply = walk("2025-01-15", "0 ft:1.376 in:1.376 in", "1200 ft:1.352 in:1.348 in");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("2 readings recorded", "verdict serviceable", "% of nominal");
    }

    @Test
    void aWornRopeComesBackCondemned() {
        assertThat(walk("2025-01-15", "1200 ft:1.270 in:1.270 in").output()).contains("verdict condemned");
    }

    @Test
    void aRecordedWalkComesBackOnTheList() {
        walk("2025-01-15", "0 ft:1.376 in:1.376 in");
        assertThat(run("rope", "walks", SERIAL).output()).contains("2025-01-15", "R Halvorsen");
    }

    @Test
    void aRecordedWalkReachesThePrintedFile() {
        walk("2025-01-15", "1200 ft:1.270 in:1.270 in");
        assertThat(run("rope", "file", SERIAL).output())
                .contains("standing                 condemned", "caliper walks");
    }

    @Test
    void aWalkWithABadReadingIsRefusedBeforeAnythingIsWritten() {
        Shell.Reply reply = walk("2025-01-15", "1200 ft:about an inch");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(run("rope", "walks", SERIAL).count("R Halvorsen")).isZero();
    }

    @Test
    void aWalkTakenBeforeTheRopeWentOnIsRefused() {
        Shell.Reply reply = walk("2015-01-15", "0 ft:1.376 in:1.376 in");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("predate");
    }

    @Test
    void aWalkNeedsAtLeastOneReading() {
        assertThat(walk("2025-01-15").code()).isNotZero();
    }

    @Test
    void everyPrintedLineFitsThePage() {
        for (List<String> args :
                List.of(List.of("rope", "list"), List.of("rope", "file", SERIAL), List.of("rope", "walks", SERIAL))) {
            assertThat(run(args.toArray(String[]::new)).lines())
                    .as(args.toString())
                    .allMatch(one -> one.length() <= Forms.PAGE_WIDTH);
        }
    }

    @Test
    void theGroupListsItsCommands() {
        assertThat(run("rope", "--help").output()).contains("list", "file", "walk", "walks");
    }

    @Test
    void aRopeWithOneWalkCannotBeProjected() {
        walked("2024-01-15", "1.354");
        Shell.Reply reply = forecast(SERIAL, "--on", "2025-09-01");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("needs 2 walks");
    }

    @Test
    void twoWalksGiveTheDateTheRopeComesOff() {
        walked("2023-01-15", "1.378");
        walked("2025-01-15", "1.330");
        Shell.Reply reply = forecast(SERIAL, "--on", "2025-09-01");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("2027-01-16", "two points");
    }

    @Test
    void aRopeThatHasNotWornSaysSoRatherThanGuessing() {
        walked("2024-01-15", "1.378");
        walked("2025-01-15", "1.378");
        Shell.Reply reply = forecast(SERIAL, "--on", "2025-09-01");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("not wearing");
    }

    @Test
    void withNoSerialItPrintsOnlyWhatWantsOrdering() {
        walked("2023-01-15", "1.378");
        walked("2025-01-15", "1.330");
        assertThat(forecast("--on", "2025-09-01").output()).contains("nothing on the hill wants ordering yet");
    }

    @Test
    void aRopeWearingFastIsNamedAsWantingOrdering() {
        walked("2025-01-15", "1.340");
        walked("2025-06-15", "1.300");
        Shell.Reply reply = forecast("--on", "2025-09-01");
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("should already be on order", SERIAL);
    }

    @Test
    void aShorterLeadTimeMovesTheOrderDate() {
        walked("2023-01-15", "1.378");
        walked("2025-01-15", "1.330");
        Shell.Reply slow = forecast(SERIAL, "--on", "2025-09-01", "--lead-time", "300");
        Shell.Reply quick = forecast(SERIAL, "--on", "2025-09-01", "--lead-time", "30");
        assertThat(slow.output()).isNotEqualTo(quick.output()).contains("2027-01-16");
        assertThat(quick.output()).contains("2027-01-16");
    }

    @Test
    void aRopeNobodyHas() {
        Shell.Reply reply = forecast("ZZZ-2019-0404");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no rope");
    }
}
