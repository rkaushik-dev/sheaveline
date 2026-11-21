package io.github.rkaushikdev.sheaveline.line;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryTest {

    private static final Lift LIFT = Hill.johnPaul();

    private static LineGeometry geometry(long snowFeet, GroundUseMap uses, long tension) {
        return new LineGeometry(
                LIFT,
                new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(tension)),
                Length.ofFeet(snowFeet),
                uses);
    }

    private static LineGeometry geometry(long snowFeet, GroundUseMap uses) {
        return geometry(snowFeet, uses, 34500);
    }

    private static LineGeometry geometry(long snowFeet, long tension) {
        return geometry(snowFeet, new GroundUseMap(), tension);
    }

    private static LineGeometry geometry(long snowFeet) {
        return geometry(snowFeet, 34500);
    }

    private static LineGeometry geometry() {
        return geometry(4);
    }

    private static UseSegment segment(long start, long end, GroundUse use) {
        return new UseSegment(Length.ofFeet(start), Length.ofFeet(end), use);
    }

    @Test
    void theWalkCoversEverySpanAndEveryTower() {
        LineGeometry walk = geometry();
        assertThat(walk.spans()).hasSize(8);
        assertThat(walk.tightestClearances()).hasSize(8);
        assertThat(walk.towerLoadings()).hasSize(7);
    }

    @Test
    void theCarrierDropIsTheLongestHangerOnTheLine() {
        assertThat(geometry().carrierDrop()).isEqualTo(Length.ofFeetInches(6, 4));
    }

    @Test
    void eachTowerLoadingSitsBetweenTheRightTwoSpans() {
        List<TowerLoading> loadings = geometry().towerLoadings();
        for (int position = 0; position < loadings.size(); position++) {
            TowerLoading loading = loadings.get(position);
            assertThat(loading.tower().number()).isEqualTo(position + 1);
            assertThat(loading.below().upper().station())
                    .isEqualTo(loading.tower().station());
            assertThat(loading.above().lower().station())
                    .isEqualTo(loading.tower().station());
        }
    }

    @Test
    void theWorstClearanceIsTheLeastMarginAcrossTheLine() {
        LineGeometry walk = geometry();
        ClearanceReading worst = walk.worstClearance();
        Length least = walk.tightestClearances().stream()
                .map(ClearanceReading::margin)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        assertThat(worst.margin()).isLessThanOrEqualTo(least);
    }

    @Test
    void aLineWithRoomEverywhereIsFit() {
        LineGeometry walk = geometry(2);
        assertThat(walk.verdict()).isSameAs(GeometryVerdict.FIT);
        assertThat(walk.verdict().mayRun()).isTrue();
        assertThat(walk.findings()).isEmpty();
    }

    @Test
    void deepEnoughSnowTakesTheLineOut() {
        LineGeometry walk = geometry(40);
        assertThat(walk.verdict()).isSameAs(GeometryVerdict.UNFIT);
        assertThat(walk.verdict().mayRun()).isFalse();
        assertThat(walk.findings()).anyMatch(finding -> finding.severity() == Severity.STOP);
    }

    @Test
    void waterUnderASpanCanRestrictALineThatIsOtherwiseFine() {
        LineGeometry walk = geometry(2);
        assertThat(walk.verdict()).isSameAs(GeometryVerdict.FIT);
        LineGeometry overWater = geometry(18, GroundUseMap.of(List.of(segment(1500, 1700, GroundUse.WATER))));
        assertThat(overWater.verdict()).isNotSameAs(GeometryVerdict.FIT);
    }

    @Test
    void droppingTheTensionLetsTheRopeDownOntoTheGround() {
        LineGeometry tight = geometry(6, 40000);
        LineGeometry slack = geometry(6, 9000);
        assertThat(slack.worstClearance().available())
                .isLessThan(tight.worstClearance().available());
    }

    @Test
    void findingsNameWhereTheyAre() {
        LineGeometry walk = geometry(40);
        for (Finding finding : walk.findings()) {
            assertThat(finding.where()).isNotEmpty();
            assertThat(finding.detail()).isNotEmpty();
            assertThat(finding.toString()).startsWith(finding.where());
        }
    }

    @Test
    void aStopFindingBeatsARestrictOne() {
        assertThat(Severity.STOP.stopsTheLift()).isTrue();
        assertThat(Severity.RESTRICT.stopsTheLift()).isFalse();
        assertThat(Severity.NOTE.stopsTheLift()).isFalse();
    }

    @Test
    void aFindingPrintsWhereAndWhat() {
        Finding finding = new Finding("span 3", Severity.STOP, "clearance 4.0 ft over open ground");
        assertThat(finding).hasToString("span 3: clearance 4.0 ft over open ground");
    }

    @Test
    void changingTheSnowMakesANewWalkAndCanChangeTheVerdict() {
        LineGeometry dry = geometry(2);
        LineGeometry deep = dry.withSnow(Length.ofFeet(40));
        assertThat(dry.verdict()).isSameAs(GeometryVerdict.FIT);
        assertThat(deep.verdict()).isSameAs(GeometryVerdict.UNFIT);
        assertThat(dry.snow()).isEqualTo(Length.ofFeet(2));
    }

    @Test
    void changingTheTensionMakesANewWalk() {
        LineGeometry slack =
                geometry().withTension(new SagModel(LoadPerFoot.ofPoundsPerFoot("6.20"), Load.ofPounds(12000)));
        assertThat(slack.sag().tension()).isEqualTo(Load.ofPounds(12000));
        assertThat(slack.lift()).isSameAs(LIFT);
    }

    @Test
    void aFinerStepNeverReportsFewerProblems() {
        LineGeometry walk = geometry(30);
        int coarse = walk.findings(Length.ofFeet(200)).size();
        int fine = walk.findings(Length.ofFeet(25)).size();
        assertThat(fine).isGreaterThanOrEqualTo(coarse);
    }

    @Test
    void theVerdictReadsTheWorstFindingAndNothingElse() {
        LineGeometry walk = geometry(40);
        assertThat(walk.verdict()).isSameAs(GeometryVerdict.UNFIT);
        assertThat(walk.findings()).anyMatch(finding -> finding.severity() == Severity.STOP);
    }
}
