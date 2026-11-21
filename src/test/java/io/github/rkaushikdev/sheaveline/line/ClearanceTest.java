package io.github.rkaushikdev.sheaveline.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.LineProfile;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.Support;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClearanceTest {

    private static final LineProfile FLAT = LineProfile.fromSurvey(new long[][] {{0, 6400}, {500, 6400}, {1000, 6400}});
    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("3.12"), Load.ofPounds(34500));

    private static Span span(long upperElev) {
        return new Span(
                1,
                Support.terminal("a", Length.ofFeet(0), Length.ofFeet(6430)),
                Support.terminal("b", Length.ofFeet(1000), Length.ofFeet(upperElev)));
    }

    private static Span span() {
        return span(6440);
    }

    private static ClearanceCheck check(GroundUseMap uses, long snowFeet, long dropFeet, long upperElev) {
        return new ClearanceCheck(span(upperElev), FLAT, SAG, Length.ofFeet(dropFeet), Length.ofFeet(snowFeet), uses);
    }

    private static ClearanceCheck check(GroundUseMap uses, long snowFeet) {
        return check(uses, snowFeet, 7, 6440);
    }

    private static ClearanceCheck check(GroundUseMap uses) {
        return check(uses, 0);
    }

    private static ClearanceCheck check(long snowFeet) {
        return check(new GroundUseMap(), snowFeet);
    }

    private static ClearanceCheck check() {
        return check(0);
    }

    private static UseSegment segment(long start, long end, GroundUse use) {
        return new UseSegment(Length.ofFeet(start), Length.ofFeet(end), use);
    }

    @Test
    void eachGroundUseCarriesItsOwnMinimum() {
        assertThat(GroundUse.OPEN.minimum()).isEqualTo(Length.ofFeet(12));
        assertThat(GroundUse.ROAD.minimum()).isEqualTo(Length.ofFeet(24));
        assertThat(GroundUse.WATER.minimum()).isEqualTo(Length.ofFeet(30));
        assertThat(GroundUse.ROAD.isTrafficked()).isTrue();
        assertThat(GroundUse.OPEN.isTrafficked()).isFalse();
    }

    @Test
    void groundWithNothingRecordedIsOpenGround() {
        assertThat(new GroundUseMap().useAt(Length.ofFeet(400))).isSameAs(GroundUse.OPEN);
        assertThat(new GroundUseMap().minimumAt(Length.ofFeet(400))).isEqualTo(Length.ofFeet(12));
    }

    @Test
    void aSegmentSetsTheUseOverItsStretch() {
        GroundUseMap uses = GroundUseMap.of(List.of(segment(300, 600, GroundUse.TRAIL)));
        assertThat(uses.useAt(Length.ofFeet(200))).isSameAs(GroundUse.OPEN);
        assertThat(uses.useAt(Length.ofFeet(450))).isSameAs(GroundUse.TRAIL);
        assertThat(uses.useAt(Length.ofFeet(700))).isSameAs(GroundUse.OPEN);
    }

    @Test
    void overlappingUsesTakeTheMoreDemandingOne() {
        GroundUseMap uses =
                GroundUseMap.of(List.of(segment(300, 600, GroundUse.TRAIL), segment(400, 500, GroundUse.ROAD)));
        assertThat(uses.useAt(Length.ofFeet(450))).isSameAs(GroundUse.ROAD);
        assertThat(uses.useAt(Length.ofFeet(350))).isSameAs(GroundUse.TRAIL);
    }

    @Test
    void aSegmentHasToRunUpTheLine() {
        assertThatThrownBy(() -> segment(600, 300, GroundUse.TRAIL)).isInstanceOf(LineError.class);
    }

    @Test
    void theCarrierBottomIsTheRopeLessTheHanger() {
        ClearanceReading reading = check().readingAt(Length.ofFeet(500));
        Length rope = SAG.ropeElevation(span(), Length.ofFeet(500));
        assertThat(reading.carrierBottom()).isEqualTo(rope.minus(Length.ofFeet(7)));
    }

    @Test
    void theAvailableClearanceTakesOffTheGroundAndTheSnow() {
        ClearanceReading dry = check(0).readingAt(Length.ofFeet(500));
        ClearanceReading deep = check(6).readingAt(Length.ofFeet(500));
        assertThat(dry.available().minus(deep.available())).isEqualTo(Length.ofFeet(6));
    }

    @Test
    void aSpanWithRoomPassesAndTheSameSpanInDeepSnowDoesNot() {
        assertThat(check(0).passes()).isTrue();
        assertThat(check(8).passes()).isFalse();
    }

    @Test
    void theMarginIsWhatIsThereLessWhatIsWanted() {
        ClearanceReading reading = check().readingAt(Length.ofFeet(0));
        assertThat(reading.available()).isEqualTo(Length.ofFeet(23));
        assertThat(reading.required()).isEqualTo(Length.ofFeet(12));
        assertThat(reading.margin()).isEqualTo(Length.ofFeet(11));
        assertThat(reading.passes()).isTrue();
    }

    @Test
    void aRoadUnderTheSpanCanFailWhereOpenGroundPasses() {
        Length station = Length.ofFeet(500);
        ClearanceReading openGround = check().readingAt(station);
        assertThat(openGround.passes()).isTrue();
        ClearanceCheck overRoad = check(GroundUseMap.of(List.of(segment(400, 600, GroundUse.ROAD))));
        ClearanceReading tight = overRoad.readingAt(station);
        assertThat(tight.available()).isEqualTo(openGround.available());
        assertThat(tight.required()).isEqualTo(Length.ofFeet(24));
    }

    @Test
    void theTightestMarginIsNotAlwaysTheLowestRope() {
        GroundUseMap uses = GroundUseMap.of(List.of(segment(0, 100, GroundUse.WATER)));
        ClearanceCheck walked = check(uses, 6);
        // The chord climbs, so the rope bottoms out below midspan.
        assertThat(walked.lowest().station()).isEqualTo(Length.ofFeet(400));
        assertThat(walked.tightest().station()).isNotEqualTo(walked.lowest().station());
    }

    @Test
    void theFailuresAreListedStationByStation() {
        ClearanceCheck walked = check(8);
        List<ClearanceReading> failures = walked.failures();
        assertThat(failures).isNotEmpty();
        assertThat(failures).noneMatch(ClearanceReading::passes);
        assertThat(walked.passes()).isFalse();
    }

    @Test
    void aFinerStepFindsAtLeastAsMuchAsACoarseOne() {
        ClearanceCheck walked = check(8);
        int coarse = walked.failures(Length.ofFeet(250)).size();
        int fine = walked.failures(Length.ofFeet(25)).size();
        assertThat(fine).isGreaterThanOrEqualTo(coarse);
    }

    @Test
    void changingTheSnowMakesANewCheck() {
        ClearanceCheck dry = check(0);
        ClearanceCheck deep = dry.withSnow(Length.ofFeet(8));
        assertThat(dry.snow().isZero()).isTrue();
        assertThat(deep.snow()).isEqualTo(Length.ofFeet(8));
        assertThat(deep.span()).isSameAs(dry.span());
    }

    @Test
    void aCheckRefusesACarrierAboveTheRopeOrNegativeSnow() {
        assertThatThrownBy(() -> check(new GroundUseMap(), 0, -1, 6440)).isInstanceOf(LineError.class);
        assertThatThrownBy(() -> check(-1)).isInstanceOf(LineError.class);
    }

    @Test
    void aReadingPrintsBothSidesOfTheComparison() {
        ClearanceReading reading = check().readingAt(Length.ofFeet(0));
        assertThat(reading).hasToString("23.0 ft of 12 ft wanted at station 0");
    }

    @Test
    void aClimbingSpanIsTightestNearItsLowEnd() {
        ClearanceCheck walked = check(new GroundUseMap(), 8, 7, 6700);
        assertThat(walked.tightest().station()).isLessThan(Length.ofFeet(700));
    }
}
