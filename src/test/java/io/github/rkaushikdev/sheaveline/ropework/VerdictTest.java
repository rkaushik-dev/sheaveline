package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictTest {

    private static final HaulRope ROPE = johnPaul().rope();
    private static final LocalDate TAKEN = LocalDate.of(2025, 1, 15);

    private static DiameterReading reading(long stationFeet, String inches, String second) {
        return new DiameterReading(Length.ofFeet(stationFeet), Length.ofInches(inches), Length.ofInches(second));
    }

    private static DiameterReading reading(long stationFeet, String inches) {
        return reading(stationFeet, inches, inches);
    }

    private static WearSurvey wear(List<LayReading> lays, DiameterReading... readings) {
        List<DiameterReading> walked = readings.length == 0 ? List.of(reading(0, "1.376")) : List.of(readings);
        return WearSurvey.of(ROPE, TAKEN, "R Halvorsen", walked, lays);
    }

    private static WearSurvey wear(DiameterReading... readings) {
        return wear(List.of(), readings);
    }

    private static BreakSurvey breaks(BreakObservation... observations) {
        return BreakSurvey.of(ROPE, TAKEN, "R Halvorsen", List.of(observations));
    }

    private static BreakObservation seen(long stationFeet, int one, int six) {
        return new BreakObservation(Length.ofFeet(stationFeet), one, six, 0, false);
    }

    private static MagneticTest magnetic(Indication... indications) {
        return MagneticTest.of(ROPE, TAKEN, "Wire Rope Diagnostics", "LMA-250", List.of(indications));
    }

    private static SpliceRegister splices(Splice... entries) {
        List<Splice> held = entries.length == 0
                ? List.of(new Splice(
                        Length.ofFeet(0),
                        LocalDate.of(2019, 10, 4),
                        "B Kettner",
                        5,
                        ROPE.diameter().scaled(1200),
                        true))
                : List.of(entries);
        return SpliceRegister.of(ROPE, held);
    }

    private static TensionSystem tension(long carriageFeet) {
        return new TensionSystem(
                TensionKind.HYDRAULIC, Load.ofPounds(34500), Length.ofFeet(16), Length.ofFeet(carriageFeet));
    }

    @Test
    void theWearRulesAreRowsRatherThanAChain() {
        assertThat(Verdict.WEAR_RULES).hasSize(6);
        assertThat(Verdict.WEAR_RULES.stream().map(WearRule::name).distinct())
                .containsExactlyInAnyOrder("diameter", "lay length", "ovality", "local reduction");
    }

    @Test
    void aGoodRopeFiresNoRule() {
        assertThat(Verdict.wearFindings(wear())).isEmpty();
        assertThat(Verdict.assessWear(wear())).isSameAs(WearVerdict.SERVICEABLE);
    }

    @Test
    void aThinRopeFiresTheDiameterRuleAndSaysTheReading() {
        WearSurvey walked = wear(reading(0, "1.376"), reading(1200, "1.270"));
        List<Finding> found = Verdict.wearFindings(walked);
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).source()).isEqualTo("diameter");
        assertThat(found.get(0).detail()).contains("% of nominal");
        assertThat(Verdict.assessWear(walked)).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void aRopeBetweenTheTwoDiameterRowsOnlyGetsWatched() {
        WearSurvey walked = wear(reading(0, "1.376"), reading(1200, "1.310"));
        assertThat(Verdict.assessWear(walked)).isSameAs(WearVerdict.MONITOR);
    }

    @Test
    void anOvalRopeFiresTheOvalityRow() {
        WearSurvey walked = wear(reading(0, "1.378"), reading(600, "1.400", "1.320"));
        assertThat(Verdict.wearFindings(walked).stream().map(Finding::source).distinct())
                .containsExactly("ovality");
        assertThat(Verdict.assessWear(walked)).isSameAs(WearVerdict.MONITOR);
    }

    @Test
    void aNeckOnAnOtherwiseGoodRopeIsWatched() {
        WearSurvey walked =
                wear(reading(0, "1.392"), reading(1200, "1.390"), reading(2400, "1.346"), reading(3600, "1.388"));
        assertThat(walked.smallestPercent()).isGreaterThan(BigDecimal.valueOf(97));
        assertThat(walked.neckPercent()).isGreaterThan(BigDecimal.valueOf(3));
        assertThat(Verdict.wearFindings(walked)).singleElement().satisfies(found -> {
            assertThat(found.source()).isEqualTo("local reduction");
            assertThat(found.verdict()).isSameAs(WearVerdict.MONITOR);
            assertThat(found.detail()).isEqualTo("3.0% under the rest of the rope");
        });
    }

    @Test
    void aRopeWornEvenlyHasNoNeckHoweverThinItIs() {
        WearSurvey walked = wear(reading(0, "1.300"), reading(1200, "1.298"), reading(2400, "1.296"));
        assertThat(walked.neckPercent()).isLessThan(BigDecimal.ONE);
        assertThat(Verdict.wearFindings(walked)).extracting(Finding::source).doesNotContain("local reduction");
    }

    @Test
    void twoStationsAreNotEnoughToCallANeck() {
        WearSurvey walked = wear(reading(0, "1.376"), reading(1200, "1.320"));
        assertThat(walked.neckPercent()).isZero();
        assertThat(walked.median()).isEqualTo(Length.ofInches("1.320"));
        assertThat(Verdict.wearFindings(walked)).extracting(Finding::source).containsExactly("diameter");
    }

    @Test
    void aStretchedLayFiresAtTwoRowsDependingHowFar() {
        Length nominal = ROPE.spec().layLength();
        WearSurvey watched = wear(List.of(new LayReading(Length.ofFeet(600), nominal.scaled("1.03"))));
        assertThat(Verdict.assessWear(watched)).isSameAs(WearVerdict.MONITOR);
        WearSurvey gone = wear(List.of(new LayReading(Length.ofFeet(600), nominal.scaled("1.08"))));
        assertThat(Verdict.assessWear(gone)).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void brokenWiresAreAssessedFromTheCounts() {
        assertThat(Verdict.assessBreaks(breaks())).isSameAs(WearVerdict.SERVICEABLE);
        assertThat(Verdict.assessBreaks(breaks(seen(1200, 4, 7)))).isSameAs(WearVerdict.MONITOR);
        assertThat(Verdict.assessBreaks(breaks(seen(1200, 7, 9)))).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void theMagneticTraceIsAssessedFromTheIndications() {
        assertThat(Verdict.assessTest(magnetic())).isSameAs(WearVerdict.SERVICEABLE);
        MagneticTest watched = magnetic(Indication.loss(Length.ofFeet(1800), "7.4"));
        assertThat(Verdict.assessTest(watched)).isSameAs(WearVerdict.MONITOR);
        MagneticTest gone = magnetic(Indication.loss(Length.ofFeet(1800), "10.4"));
        assertThat(Verdict.assessTest(gone)).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void aLocalFaultAndALossCanFireTogether() {
        MagneticTest both =
                magnetic(Indication.loss(Length.ofFeet(1800), "10.4"), Indication.fault(Length.ofFeet(2400), 3));
        assertThat(Verdict.assessTest(both)).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void twoSmallFaultsInsideOneLayCondemnTheRopeTogether() {
        Length lay = ROPE.spec().layLength();
        MagneticTest apart =
                magnetic(Indication.fault(Length.ofFeet(2400), 3), Indication.fault(Length.ofFeet(3600), 2));
        MagneticTest together = magnetic(
                Indication.fault(Length.ofFeet(2400), 3),
                Indication.fault(Length.ofFeet(2400).plus(lay.scaled("0.5")), 2));
        assertThat(Verdict.assessTest(apart)).isSameAs(WearVerdict.MONITOR);
        assertThat(Verdict.assessTest(together)).isSameAs(WearVerdict.CONDEMNED);
        assertThat(Verdict.testFindings(together))
                .extracting(Finding::detail)
                .containsExactly("5 wires broken inside one lay");
    }

    @Test
    void aTraceReadingFindsOnceAtItsWorseLevel() {
        MagneticTest gone = magnetic(Indication.loss(Length.ofFeet(1800), "10.4"));
        assertThat(Verdict.testFindings(gone)).singleElement().satisfies(found -> {
            assertThat(found.verdict()).isSameAs(WearVerdict.CONDEMNED);
            assertThat(found.source()).isEqualTo("magnetic test");
            assertThat(found.detail()).isEqualTo("10.4% loss of area");
        });
        assertThat(Verdict.TRACE_RULES).extracting(TraceRule::name).containsExactly("loss of area", "local fault");
    }

    @Test
    void aTraceRowCannotWatchAboveWhereItCondemns() {
        assertThatThrownBy(() -> new TraceRule(
                        "loss of area", MagneticTest::peakLoss, BigDecimal.ONE, BigDecimal.TEN, String::valueOf))
                .isInstanceOf(RopeworkError.class);
        TraceRule row = Verdict.TRACE_RULES.get(0);
        assertThat(row.judge(BigDecimal.valueOf(6))).isSameAs(WearVerdict.SERVICEABLE);
        assertThat(row.judge(new BigDecimal("6.1"))).isSameAs(WearVerdict.MONITOR);
        assertThat(row.judge(BigDecimal.valueOf(10))).isSameAs(WearVerdict.MONITOR);
        assertThat(row.judge(new BigDecimal("10.1"))).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void anEmptyRopeFileSaysNothingAgainstTheRope() {
        RopeFile empty = new RopeFile();
        assertThat(empty.findings()).isEmpty();
        assertThat(empty.verdict()).isSameAs(WearVerdict.SERVICEABLE);
        assertThat(empty.mayRun()).isTrue();
        assertThatThrownBy(empty::worst).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aRopeFileReadsEverySourceItHas() {
        RopeFile held = new RopeFile()
                .withWear(wear(reading(0, "1.376"), reading(1200, "1.310")))
                .withBreaks(breaks(seen(1200, 4, 7)))
                .withMagnetic(magnetic(Indication.loss(Length.ofFeet(1800), "7.4")));
        assertThat(held.sources()).containsExactlyInAnyOrder("diameter", "broken wires", "magnetic test");
        assertThat(held.verdict()).isSameAs(WearVerdict.MONITOR);
    }

    @Test
    void theWorstSourceWinsTheFile() {
        RopeFile held = new RopeFile()
                .withWear(wear(reading(0, "1.376"), reading(1200, "1.310")))
                .withBreaks(breaks(seen(1200, 7, 9)));
        assertThat(held.verdict()).isSameAs(WearVerdict.CONDEMNED);
        assertThat(held.mayRun()).isFalse();
        assertThat(held.worst().verdict()).isSameAs(WearVerdict.CONDEMNED);
    }

    @Test
    void aFailingSpliceRegisterCondemnsTheRope() {
        SpliceRegister close = splices(
                new Splice(
                        Length.ofFeet(0),
                        LocalDate.of(2019, 10, 4),
                        "B Kettner",
                        5,
                        ROPE.diameter().scaled(1200),
                        true),
                new Splice(
                        Length.ofFeet(200),
                        LocalDate.of(2021, 6, 1),
                        "B Kettner",
                        5,
                        ROPE.diameter().scaled(1200)));
        RopeFile held = new RopeFile().withSplices(close);
        assertThat(held.verdict()).isSameAs(WearVerdict.CONDEMNED);
        assertThat(held.sources()).contains("splices");
    }

    @Test
    void aCarriageWithNoReservePutsTheRopeOnWatch() {
        RopeFile held = new RopeFile().withTension(tension(15));
        assertThat(held.verdict()).isSameAs(WearVerdict.MONITOR);
        assertThat(held.sources()).contains("tension");
        assertThat(new RopeFile().withTension(tension(6)).findings()).isEmpty();
    }

    @Test
    void anOverdueMagneticTestCondemnsTheRopeOnTheDayItIsAsked() {
        RetestSchedule schedule = new RetestSchedule(ROPE, LocalDate.of(2022, 10, 4));
        RopeFile held = new RopeFile().withRetest(schedule);
        assertThat(held.verdict(LocalDate.of(2025, 1, 15))).isSameAs(WearVerdict.CONDEMNED);
        assertThat(held.verdict(LocalDate.of(2024, 6, 1))).isSameAs(WearVerdict.SERVICEABLE);
        assertThat(held.verdict()).isSameAs(WearVerdict.SERVICEABLE);
    }

    @Test
    void aFindingPrintsItsSourceAndItsReading() {
        WearSurvey walked = wear(reading(0, "1.376"), reading(1200, "1.270"));
        assertThat(Verdict.wearFindings(walked).get(0).toString()).startsWith("diameter: ");
    }
}
