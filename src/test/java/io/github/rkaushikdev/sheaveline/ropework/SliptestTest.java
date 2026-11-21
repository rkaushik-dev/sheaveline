package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.jpCarriers;
import static io.github.rkaushikdev.sheaveline.Hill.sbCarriers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import io.github.rkaushikdev.sheaveline.plant.Grip;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SliptestTest {

    private static final Load RATED = Load.ofPounds(2600);
    private static final LocalDate TESTED = LocalDate.of(2025, 1, 15);

    private static SlipTest slip(int carrier, long pounds) {
        return new SlipTest(carrier, TESTED, "D Ruiz", Load.ofPounds(pounds), RATED);
    }

    private static SlipTest slip(int carrier) {
        return slip(carrier, 2600);
    }

    private static SlipTest slip() {
        return slip(1);
    }

    private static SlipTest pulled(long pounds) {
        return slip(1, pounds);
    }

    private static SlipProgramme programme(CarrierSet fleet, boolean failedBefore, List<SlipTest> tests) {
        return SlipProgramme.of(fleet, "2024/25", tests, failedBefore);
    }

    private static SlipProgramme programme(List<SlipTest> tests) {
        return programme(jpCarriers(), false, tests);
    }

    private static SlipProgramme programme(SlipTest... tests) {
        return programme(List.of(tests));
    }

    private static List<SlipTest> cleanRound(int from, int to) {
        return IntStream.rangeClosed(from, to)
                .mapToObj(number -> slip(number, 2550))
                .toList();
    }

    @Test
    void aSlipTestChecksItsOwnRecord() {
        assertThatThrownBy(() -> slip(0)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new SlipTest(1, TESTED, " ", RATED, RATED)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> pulled(0)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new SlipTest(1, TESTED, "D Ruiz", RATED, Load.ofPounds(0)))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aGripThatSlipsNearItsRatingPasses() {
        assertThat(pulled(2600).passed()).isTrue();
        assertThat(pulled(2300).passed()).isTrue();
        assertThat(pulled(2900).passed()).isTrue();
    }

    @Test
    void aGripThatSlipsWellOutsideTheBandFails() {
        assertThat(pulled(2100).passed()).isFalse();
        assertThat(pulled(3100).passed()).isFalse();
    }

    @Test
    void theBandIsFifteenPercentEitherSide() {
        SlipTest.Band band = slip().band();
        assertThat(band.low()).isEqualTo(Load.ofPounds(2210));
        assertThat(band.high()).isEqualTo(Load.ofPounds(2990));
    }

    @Test
    void aGripThatLetsGoEarlyIsToldFromOneThatHoldsTooHard() {
        assertThat(pulled(2100).slippedEarly()).isTrue();
        assertThat(pulled(3100).slippedEarly()).isFalse();
        assertThat(Decimals.quantize(pulled(2100).deviation(), 2)).isEqualByComparingTo("-0.19");
    }

    @Test
    void aSlipTestIsBuiltFromTheGripItCameOff() {
        Grip grip = jpCarriers().iterator().next().gripOf();
        SlipTest built = SlipTest.of(3, TESTED, "D Ruiz", Load.ofPounds(2550), grip);
        assertThat(built.rated()).isEqualTo(grip.ratedSlip());
        assertThat(built.passed()).isTrue();
    }

    @Test
    void aFixedGripNeverGoesOnTheSlipBench() {
        Grip grip = sbCarriers().iterator().next().gripOf();
        assertThatThrownBy(() -> SlipTest.of(3, TESTED, "D Ruiz", Load.ofPounds(2000), grip))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aTestPrintsItsOutcome() {
        assertThat(slip(7, 2550)).hasToString("carrier 7 slipped at 2,550 lb, pass");
        assertThat(slip(7, 2000)).hasToString("carrier 7 slipped at 2,000 lb, fail");
    }

    @Test
    void theOrdinarySampleIsATenthOfTheFleet() {
        assertThat(programme().fleetSize()).isEqualTo(84);
        assertThat(programme().ordinarySample()).isEqualTo(9);
        assertThat(programme().requiredSample()).isEqualTo(9);
    }

    @Test
    void aSmallFleetStillGetsFourGripsTested() {
        CarrierSet small = new CarrierSet(jpCarriers().carriers().subList(0, 20));
        assertThat(programme(small, false, List.of()).ordinarySample()).isEqualTo(4);
    }

    @Test
    void aFleetSmallerThanTheMinimumIsTestedWhole() {
        CarrierSet tiny = new CarrierSet(jpCarriers().carriers().subList(0, 3));
        assertThat(programme(tiny, false, List.of()).requiredSample()).isEqualTo(3);
    }

    @Test
    void aFailureThisSeasonPullsTheWholeFleetIn() {
        SlipProgramme walked = programme(slip(1, 2600), slip(2, 2000));
        assertThat(walked.failures()).isNotEmpty();
        assertThat(walked.requiredSample()).isEqualTo(84);
        assertThat(walked.isComplete()).isFalse();
    }

    @Test
    void aFailureLastSeasonDoesTheSameBeforeAnyTestIsRun() {
        assertThat(programme(jpCarriers(), true, List.of()).requiredSample()).isEqualTo(84);
    }

    @Test
    void aCleanSeasonLeavesTheSampleWhereItWas() {
        SlipProgramme walked = programme(cleanRound(1, 9));
        assertThat(walked.failures()).isEmpty();
        assertThat(walked.requiredSample()).isEqualTo(9);
        assertThat(walked.isComplete()).isTrue();
        assertThat(walked.outstanding()).isZero();
    }

    @Test
    void theOutstandingCountFallsAsTestsComeIn() {
        SlipProgramme walked = programme(slip(1), slip(2), slip(3));
        assertThat(walked.testedCount()).isEqualTo(3);
        assertThat(walked.outstanding()).isEqualTo(6);
    }

    @Test
    void addingAFailingTestReopensAFinishedProgramme() {
        SlipProgramme done = programme(cleanRound(1, 9));
        assertThat(done.isComplete()).isTrue();
        SlipProgramme reopened = done.withTest(slip(10, 2000));
        assertThat(reopened.isComplete()).isFalse();
        assertThat(reopened.outstanding()).isEqualTo(84 - 10);
    }

    @Test
    void aCarrierIsSlipTestedOnceASeason() {
        assertThatThrownBy(() -> programme(slip(1), slip(1))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aCarrierFromAnotherLiftIsRefused() {
        assertThatThrownBy(() -> programme(slip(900))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aProgrammeNamesItsSeason() {
        assertThatThrownBy(() -> SlipProgramme.of(jpCarriers(), " ")).isInstanceOf(RopeworkError.class);
        assertThat(programme(slip(1))).hasToString("2024/25 slip tests, 1 of 9 done");
    }

    @Test
    void theCarriersStillToDoAreListed() {
        SlipProgramme walked = programme(slip(1), slip(2));
        List<Integer> outstanding = walked.untestedCarriers();
        assertThat(outstanding).doesNotContain(1);
        assertThat(outstanding).contains(3);
        assertThat(outstanding).hasSize(82);
    }

    @Test
    void earlySlipsArePickedOutFromTheOtherFailures() {
        SlipProgramme walked = programme(slip(1, 2000), slip(2, 3200));
        assertThat(walked.earlySlips().stream().map(SlipTest::carrier)).containsExactly(1);
        assertThat(walked.failures()).hasSize(2);
    }
}
