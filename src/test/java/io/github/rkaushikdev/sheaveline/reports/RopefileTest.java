package io.github.rkaushikdev.sheaveline.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakObservation;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.Indication;
import io.github.rkaushikdev.sheaveline.ropework.MagneticTest;
import io.github.rkaushikdev.sheaveline.ropework.Splice;
import io.github.rkaushikdev.sheaveline.ropework.SpliceRegister;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RopefileTest {

    private static final HaulRope ROPE = Hill.johnPaul().rope();
    private static final LocalDate ON = LocalDate.of(2025, 1, 15);

    private static WearSurvey wear(LocalDate taken) {
        return wear(taken, "1.376");
    }

    private static WearSurvey wear(LocalDate taken, String inches) {
        return WearSurvey.of(
                ROPE,
                taken,
                "R Halvorsen",
                List.of(new DiameterReading(Length.ofFeet(1200), Length.ofInches(inches), Length.ofInches(inches))));
    }

    private static BreakSurvey breaks(int count) {
        return BreakSurvey.of(
                ROPE, ON, "R Halvorsen", List.of(new BreakObservation(Length.ofFeet(1200), count, count + 2)));
    }

    private static MagneticTest magnetic(String loss) {
        return MagneticTest.of(
                ROPE, ON, "Wire Rope Diagnostics", "LMA-250", List.of(Indication.loss(Length.ofFeet(1800), loss)));
    }

    private static SpliceRegister splices() {
        return splicesAt(4000);
    }

    private static SpliceRegister splicesAt(long secondFeet) {
        Length longSplice = ROPE.diameter().scaled(1200);
        return SpliceRegister.of(
                ROPE,
                List.of(
                        new Splice(Length.ofFeet(0), LocalDate.of(2019, 10, 4), "B Kettner", 5, longSplice, true),
                        new Splice(Length.ofFeet(secondFeet), LocalDate.of(2022, 6, 1), "B Kettner", 5, longSplice)));
    }

    private static Page page() {
        return new Page();
    }

    @Test
    void theHeaderCarriesTheMillDetails() {
        String text = page().made().text();
        assertThat(text).contains("ROPE FILE FAT-2019-04412");
        assertThat(text).contains("construction             6x25 filler wire");
        assertThat(text).contains("nominal diameter         1.378\"");
        assertThat(text).contains("condemning diameter      1.282\"");
        assertThat(text).contains("breaking strength        196,000 lb");
    }

    @Test
    void theAgeIsWorkedOutAgainstTheDayItIsPrinted() {
        assertThat(page().made().text()).contains("seasons in service       5");
        assertThat(page().on(LocalDate.of(2026, 1, 15)).made().text()).contains("seasons in service       6");
    }

    @Test
    void aRopeWithNothingAgainstItSaysSo() {
        String text = page().made().text();
        assertThat(text).contains("nothing recorded against it");
        assertThat(text).contains("standing                 serviceable");
        assertThat(text).contains("may run                  yes");
    }

    @Test
    void theVerdictReadsTheMostRecentOfEachKind() {
        String text = page().wear(wear(LocalDate.of(2024, 12, 1)), wear(ON, "1.270"))
                .made()
                .text();
        assertThat(text).contains("standing                 condemned");
        assertThat(text).contains("may run                  no");
    }

    @Test
    void anOlderWalkDoesNotCondemnARopeThatHasBeenWalkedSince() {
        String text = page().wear(wear(LocalDate.of(2024, 12, 1), "1.270"), wear(ON))
                .made()
                .text();
        assertThat(text).contains("standing                 serviceable");
    }

    @Test
    void theCaliperWalksAreListedInTheOrderTheyWereWalked() {
        String text =
                page().wear(wear(ON), wear(LocalDate.of(2024, 12, 1))).made().text();
        assertThat(text).contains("caliper walks");
        String section = text.substring(text.indexOf("caliper walks"));
        assertThat(section.indexOf("2024-12-01")).isLessThan(section.indexOf("2025-01-15"));
    }

    @Test
    void eachWalkCarriesItsOwnVerdict() {
        String text = page().wear(wear(ON, "1.270")).made().text();
        assertThat(text).contains("condemned");
        assertThat(text).contains("R Halvorsen");
    }

    @Test
    void theBreakCountsGetTheirOwnSection() {
        String text = page().breaks(breaks(4)).made().text();
        assertThat(text).contains("broken wire counts");
        assertThat(text).contains("monitor");
    }

    @Test
    void theMagneticTestsGetTheirOwnSection() {
        String text = page().tests(magnetic("7.4")).made().text();
        assertThat(text).contains("magnetic tests");
        assertThat(text).contains("Wire Rope Diagnostics");
        assertThat(text).contains("7.4%");
    }

    @Test
    void theSplicesAreListedWithWhichKindTheyAre() {
        String text = page().splices(splices()).made().text();
        assertThat(text).contains("splices");
        assertThat(text).contains("mill");
        assertThat(text).contains("field");
        assertThat(text).contains("B Kettner");
    }

    @Test
    void aSectionWithNothingInItIsNotPrinted() {
        String text = page().made().text();
        for (String heading : List.of("caliper walks", "broken wire counts", "magnetic tests")) {
            assertThat(text).doesNotContain(heading);
        }
    }

    @Test
    void thePageCountsHowManyTimesTheRopeWasLookedAt() {
        RopeFilePage held = page().wear(wear(ON), wear(LocalDate.of(2024, 12, 1)))
                .breaks(breaks(3))
                .tests(magnetic("2.1"))
                .made();
        assertThat(held.timesLookedAt()).isEqualTo(4);
        assertThat(held.text()).contains("looked at 4 times");
    }

    @Test
    void aFailingSpliceRegisterReachesTheVerdict() {
        String text = page().splices(splicesAt(200)).made().text();
        assertThat(text).contains("standing                 condemned");
        assertThat(text).contains("splices: splice register fails");
    }

    @Test
    void theWholePageFitsEightyColumns() {
        RopeFilePage held = page().wear(wear(ON), wear(LocalDate.of(2024, 12, 1)))
                .breaks(breaks(3))
                .tests(magnetic("2.1"))
                .splices(splices())
                .made();
        assertThat(held.render().lines())
                .allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(Forms.PAGE_WIDTH));
    }

    @Test
    void theMachineTheRopeIsOnIsNamed() {
        assertThat(page().made().text()).contains("on machine               JP John Paul");
    }

    /** One rope file page, built the way a test wants it. */
    private static final class Page {

        private LocalDate on = ON;
        private List<WearSurvey> wear = List.of();
        private List<BreakSurvey> breaks = List.of();
        private List<MagneticTest> tests = List.of();
        private Optional<SpliceRegister> splices = Optional.empty();

        Page on(LocalDate value) {
            on = value;
            return this;
        }

        Page wear(WearSurvey... values) {
            wear = List.of(values);
            return this;
        }

        Page breaks(BreakSurvey... values) {
            breaks = List.of(values);
            return this;
        }

        Page tests(MagneticTest... values) {
            tests = List.of(values);
            return this;
        }

        Page splices(SpliceRegister value) {
            splices = Optional.of(value);
            return this;
        }

        RopeFilePage made() {
            return RopeFilePage.of(ROPE, on, "JP John Paul", wear, breaks, tests, splices);
        }
    }
}
