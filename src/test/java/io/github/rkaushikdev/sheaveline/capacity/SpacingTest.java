package io.github.rkaushikdev.sheaveline.capacity;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpacingTest {

    private static LineSpacing spacing(long loopFeet, int carriers, long fpm) {
        return new LineSpacing(Length.ofFeet(loopFeet), carriers, Speed.ofFeetPerMinute(fpm));
    }

    private static LineSpacing spacing(long loopFeet, int carriers) {
        return spacing(loopFeet, carriers, 1000);
    }

    private static LineSpacing spacing() {
        return spacing(10132, 84);
    }

    private static SpacingSurvey survey(long... gapsFeet) {
        List<Length> gaps = Arrays.stream(gapsFeet).mapToObj(Length::ofFeet).toList();
        return SpacingSurvey.of(Length.ofFeet(100), gaps);
    }

    @Test
    void spacingIsTheLoopDividedByTheCarriers() {
        assertThat(spacing(10000, 100).spacing()).isEqualTo(Length.ofFeet(100));
    }

    @Test
    void theIntervalIsTheSpacingAtTheRopeSpeed() {
        assertThat(spacing(10000, 100, 1000).interval()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void aSpacingBuiltFromALiftReadsItsOwnNumbers() {
        Lift lift = johnPaul();
        LineSpacing built = LineSpacing.of(lift);
        assertThat(built.carriers()).isEqualTo(84);
        assertThat(built.speed()).isEqualTo(lift.designSpeed());
        assertThat(built.loop()).isEqualTo(lift.expectedRopeLength());
    }

    @Test
    void aSpacingCanBeAskedAtAnotherSpeed() {
        Lift lift = johnPaul();
        LineSpacing slow = LineSpacing.of(lift, Speed.ofFeetPerMinute(500));
        assertThat(slow.interval()).isGreaterThan(LineSpacing.of(lift).interval());
    }

    @Test
    void theLineChecksItsOwnNumbers() {
        assertThatThrownBy(() -> spacing(0, 84)).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> spacing(10132, 0)).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> new LineSpacing(Length.ofFeet(10000), 84, Speed.ofFeetPerMinute(0)))
                .isInstanceOf(CapacityError.class);
    }

    @Test
    void takingCarriersOffOpensTheIntervalUp() {
        LineSpacing full = spacing(10132, 84);
        LineSpacing thinned = full.withCarriers(60);
        assertThat(thinned.interval()).isGreaterThan(full.interval());
        assertThat(thinned.spacing()).isGreaterThan(full.spacing());
    }

    @Test
    void slowingTheRopeOpensTheIntervalUpAsWell() {
        LineSpacing fast = spacing(10132, 84, 1000);
        LineSpacing slow = fast.atSpeed(Speed.ofFeetPerMinute(500));
        assertThat(slow.interval().seconds()).isEqualTo(fast.interval().seconds() * 2);
        assertThat(slow.spacing()).isEqualTo(fast.spacing());
    }

    @Test
    void theTwoDialsCanBeTurnedTheOtherWayRound() {
        LineSpacing line = spacing(10000, 100);
        assertThat(line.carriersFor(Length.ofFeet(200))).isEqualTo(50);
        assertThat(line.spacingFor(Duration.ofSeconds(6))).isEqualTo(Length.ofFeet(100));
    }

    @Test
    void aSpacingOfNothingHasNoCarrierCount() {
        assertThatThrownBy(() -> spacing().carriersFor(Length.ofFeet(0))).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> spacing().spacingFor(Duration.ofSeconds(0))).isInstanceOf(CapacityError.class);
    }

    @Test
    void aDetachableWillRunAShorterIntervalThanAFixedGrip() {
        LineSpacing line = spacing(10000, 100, 1000);
        assertThat(line.interval()).isEqualTo(Duration.ofSeconds(6));
        assertThat(line.meets(LiftClass.DETACHABLE_CHAIR)).isTrue();
        assertThat(line.meets(LiftClass.FIXED_CHAIR)).isFalse();
    }

    @Test
    void theShortfallSaysHowFarUnderTheFloorItIs() {
        LineSpacing line = spacing(10000, 100, 1000);
        assertThat(line.shortfall(LiftClass.FIXED_CHAIR)).isEqualTo(Duration.ofSeconds(3));
        assertThat(line.shortfall(LiftClass.DETACHABLE_CHAIR)).isEqualTo(Duration.ofSeconds(0));
    }

    @Test
    void theFullestTheLineMayBeRunForItsClass() {
        LineSpacing line = spacing(10000, 100, 1000);
        assertThat(line.mostCarriersFor(LiftClass.DETACHABLE_CHAIR)).isEqualTo(100);
        assertThat(line.mostCarriersFor(LiftClass.FIXED_CHAIR)).isEqualTo(66);
    }

    @Test
    void aTramIsHeldToAnIntervalNothingElseWouldTolerate() {
        LineSpacing line = spacing(10000, 2, 1000);
        assertThat(line.meets(LiftClass.AERIAL_TRAM)).isTrue();
    }

    @Test
    void theQuadOnTheHillClearsItsOwnFloor() {
        LineSpacing line = LineSpacing.of(johnPaul());
        assertThat(line.meets(LiftClass.DETACHABLE_CHAIR)).isTrue();
        assertThat(line.interval().seconds()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void theTripleOnTheHillClearsItsOwnFloor() {
        LineSpacing line = LineSpacing.of(strawberry());
        assertThat(line.meets(LiftClass.FIXED_CHAIR)).isTrue();
    }

    @Test
    void aLinePrintsTheThreeNumbersTheCrewReads() {
        assertThat(spacing(10000, 100)).hasToString("100 carriers at 100 ft, 0:06 apart");
    }

    @Test
    void aSurveyReportsItsWidestAndTightestGaps() {
        SpacingSurvey walked = survey(98, 103, 99, 101);
        assertThat(walked.widest()).isEqualTo(Length.ofFeet(103));
        assertThat(walked.tightest()).isEqualTo(Length.ofFeet(98));
        assertThat(walked.spread()).isEqualTo(Length.ofFeet(5));
    }

    @Test
    void anEvenLineIsInsideTheTolerance() {
        assertThat(survey(98, 103, 99, 101).isEven()).isTrue();
        assertThat(survey(98, 103, 99, 101).outOfTolerance()).isEmpty();
    }

    @Test
    void aLineWithOneBunchedPairIsNotEven() {
        SpacingSurvey walked = survey(98, 103, 60, 140);
        assertThat(walked.isEven()).isFalse();
        assertThat(walked.outOfTolerance()).hasSize(2);
        assertThat(walked.worstError()).isEqualByComparingTo("0.4");
    }

    @Test
    void aTighterToleranceCatchesMore() {
        SpacingSurvey walked = survey(98, 103, 99, 101);
        assertThat(walked.isEven("0.05")).isTrue();
        assertThat(walked.isEven("0.01")).isFalse();
    }

    @Test
    void aSurveyChecksItsOwnNumbers() {
        assertThatThrownBy(() -> SpacingSurvey.of(Length.ofFeet(0), List.of(Length.ofFeet(100))))
                .isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> SpacingSurvey.of(Length.ofFeet(100), List.of()))
                .isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> survey(100, 0)).isInstanceOf(CapacityError.class);
    }
}
