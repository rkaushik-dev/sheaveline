package io.github.rkaushikdev.sheaveline.ropework;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.LayDirection;
import io.github.rkaushikdev.sheaveline.plant.RopeSpec;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreaksTest {

    private static final HaulRope ROPE = johnPaul().rope();

    private static HaulRope langRope() {
        RopeSpec spec = new RopeSpec(
                ROPE.spec().construction(),
                ROPE.spec().grade(),
                LayDirection.RIGHT_LANG,
                ROPE.spec().nominalDiameter(),
                ROPE.spec().breakingStrength(),
                ROPE.spec().weight());
        return new HaulRope(ROPE.serial(), spec, ROPE.installed(), ROPE.length(), 1);
    }

    private static BreakObservation seen(long stationFeet, int one, int six, int valley, boolean splice) {
        return new BreakObservation(Length.ofFeet(stationFeet), one, six, valley, splice);
    }

    private static BreakObservation seen(long stationFeet, int one, int six) {
        return seen(stationFeet, one, six, 0, false);
    }

    private static BreakObservation seen(long stationFeet, int one) {
        return seen(stationFeet, one, one);
    }

    private static BreakObservation valley(long stationFeet, int one, int valley) {
        return seen(stationFeet, one, one, valley, false);
    }

    private static BreakObservation spliced(long stationFeet, int one, boolean splice) {
        return seen(stationFeet, one, one, 0, splice);
    }

    private static BreakSurvey survey(BreakObservation... observations) {
        return BreakSurvey.of(ROPE, LocalDate.of(2025, 1, 15), "R Halvorsen", List.of(observations));
    }

    @Test
    void aRegularLayRopeGetsTheFullAllowance() {
        BreakLimits limits = BreakLimits.forSpec(ROPE.spec());
        assertThat(limits.oneLay()).isEqualTo(6);
        assertThat(limits.sixLays()).isEqualTo(12);
        assertThat(limits.valley()).isEqualTo(1);
        assertThat(limits.atSplice()).isEqualTo(2);
    }

    @Test
    void aLangLayRopeGetsHalfOfIt() {
        BreakLimits limits = BreakLimits.forSpec(langRope().spec());
        assertThat(limits.oneLay()).isEqualTo(3);
        assertThat(limits.sixLays()).isEqualTo(6);
    }

    @Test
    void theWatchLevelIsHalfTheAllowanceAndNoValleyBreaks() {
        BreakLimits watch = BreakLimits.forSpec(ROPE.spec()).watchLevel();
        assertThat(watch.oneLay()).isEqualTo(3);
        assertThat(watch.sixLays()).isEqualTo(6);
        assertThat(watch.valley()).isZero();
    }

    @Test
    void anObservationChecksItsOwnCounts() {
        assertThatThrownBy(() -> seen(-10, 2)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> new BreakObservation(Length.ofFeet(0), -1, 0)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> seen(0, 5, 2)).isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> valley(0, 2, 3)).isInstanceOf(RopeworkError.class);
    }

    @Test
    void aCountInsideTheAllowancePasses() {
        assertThat(seen(0, 5, 9).exceeds(BreakLimits.forSpec(ROPE.spec()))).isFalse();
    }

    @Test
    void oneTooManyInALayFails() {
        assertThat(seen(0, 7, 9).exceeds(BreakLimits.forSpec(ROPE.spec()))).isTrue();
    }

    @Test
    void tooManyAcrossSixLaysFailsEvenWhenEachLayIsFine() {
        assertThat(seen(0, 4, 13).exceeds(BreakLimits.forSpec(ROPE.spec()))).isTrue();
    }

    @Test
    void twoValleyBreaksFailOnTheirOwn() {
        BreakLimits limits = BreakLimits.forSpec(ROPE.spec());
        assertThat(valley(0, 3, 1).exceeds(limits)).isFalse();
        assertThat(valley(0, 3, 2).exceeds(limits)).isTrue();
    }

    @Test
    void theSpliceIsHeldToATighterCount() {
        BreakLimits limits = BreakLimits.forSpec(ROPE.spec());
        assertThat(spliced(0, 3, true).exceeds(limits)).isNotEqualTo(false);
        assertThat(spliced(0, 3, true).exceeds(limits)).isTrue();
        assertThat(spliced(0, 2, true).exceeds(limits)).isFalse();
        assertThat(spliced(0, 3, false).exceeds(limits)).isFalse();
    }

    @Test
    void aStationInsideEveryLimitBreachesNone() {
        assertThat(seen(0, 5, 9).breaches(BreakLimits.forSpec(ROPE.spec()))).isEmpty();
    }

    @Test
    void aStationSaysEveryLimitItIsOverInTheStandardsOrder() {
        BreakLimits limits = BreakLimits.forSpec(ROPE.spec());
        assertThat(seen(0, 7, 13, 2, true).breaches(limits))
                .containsExactly(
                        "7 in one lay against 6",
                        "13 in six lays against 12",
                        "2 in the valleys against 1",
                        "7 in one lay at the splice against 2");
        assertThat(seen(0, 4, 13).breaches(limits)).containsExactly("13 in six lays against 12");
    }

    @Test
    void theSpliceLimitIsOnlyReadAtTheSplice() {
        BreakLimits limits = BreakLimits.forSpec(ROPE.spec());
        assertThat(spliced(0, 3, true).breaches(limits)).containsExactly("3 in one lay at the splice against 2");
        assertThat(spliced(0, 3, false).breaches(limits)).isEmpty();
    }

    @Test
    void aLangLayStationIsReadAgainstItsOwnHalvedAllowance() {
        BreakLimits limits = BreakLimits.forSpec(langRope().spec());
        assertThat(seen(0, 4, 4).breaches(limits)).containsExactly("4 in one lay against 3");
        assertThat(seen(0, 4, 4).breaches(BreakLimits.forSpec(ROPE.spec()))).isEmpty();
    }

    @Test
    void aBrokenWireFindingSaysTheStationAndWhatItIsOver() {
        BreakSurvey walked = survey(seen(1200, 7, 9), seen(2400, 4, 7));
        assertThat(Verdict.breakFindings(walked))
                .extracting(Finding::verdict, Finding::detail)
                .containsExactly(
                        tuple(WearVerdict.CONDEMNED, "7 in one lay at 1200 ft, 7 in one lay against 6"),
                        tuple(
                                WearVerdict.MONITOR,
                                "4 in one lay at 2400 ft, 4 in one lay against 3, 7 in six lays against 6"));
    }

    @Test
    void theWorstStationPrefersAValleyBreakOverABiggerCrownCount() {
        BreakSurvey walked = survey(seen(600, 5), valley(1200, 2, 1));
        assertThat(walked.worst().orElseThrow().station()).isEqualTo(Length.ofFeet(1200));
    }

    @Test
    void theBreaksAddUpAcrossTheWalk() {
        BreakSurvey walked = survey(seen(600, 3), seen(1200, 2), seen(2400, 4));
        assertThat(walked.totalBreaks()).isEqualTo(9);
    }

    @Test
    void aSurveyIsSignedAndCannotPredateTheRope() {
        assertThatThrownBy(() -> BreakSurvey.of(ROPE, LocalDate.of(2025, 1, 15), " ", List.of()))
                .isInstanceOf(RopeworkError.class);
        assertThatThrownBy(() -> BreakSurvey.of(ROPE, LocalDate.of(2015, 1, 1), "R Halvorsen", List.of()))
                .isInstanceOf(RopeworkError.class);
    }

    @Test
    void aStationPastTheEndOfTheRopeIsRefused() {
        assertThatThrownBy(() -> survey(seen(12000, 2))).isInstanceOf(RopeworkError.class);
    }

    @Test
    void theCleanLengthTakesOutALayForEveryMarkedStation() {
        BreakSurvey walked = survey(seen(600, 3), seen(1200, 2));
        assertThat(walked.cleanLength()).isLessThan(ROPE.length());
        assertThat(survey().cleanLength()).isEqualTo(ROPE.length());
    }
}
