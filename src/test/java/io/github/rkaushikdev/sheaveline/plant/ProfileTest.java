package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileTest {

    private static final long[][] SURVEY = {
        {0, 6400}, {300, 6440}, {600, 6480}, {900, 6560}, {1200, 6700}, {1600, 6820}, {2000, 6900}
    };

    private static LineProfile profile() {
        return LineProfile.fromSurvey(SURVEY);
    }

    private static Span span(long lowerStation, long lowerElevation, long upperStation, long upperElevation) {
        return new Span(
                1,
                Support.terminal("a", Length.ofFeet(lowerStation), Length.ofFeet(lowerElevation)),
                Support.terminal("b", Length.ofFeet(upperStation), Length.ofFeet(upperElevation)));
    }

    private static List<BigDecimal> feet(List<Length> stations) {
        return stations.stream().map(Length::feet).toList();
    }

    @Test
    void aProfileSortsTheShotsItIsGiven() {
        LineProfile scrambled = LineProfile.fromSurvey(new long[][] {{600, 6480}, {0, 6400}, {300, 6440}});
        assertThat(scrambled.points().stream().map(point -> point.station().feet()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal(0), new BigDecimal(300), new BigDecimal(600));
    }

    @Test
    void aProfileNeedsTwoShotsAndNoDuplicates() {
        assertThatThrownBy(() -> LineProfile.fromSurvey(new long[][] {{0, 6400}}))
                .isInstanceOf(PlantError.class)
                .hasMessage("a profile needs at least two survey shots");
        assertThatThrownBy(() -> LineProfile.fromSurvey(new long[][] {{0, 6400}, {0, 6410}}))
                .isInstanceOf(PlantError.class)
                .hasMessage("two shots share station 0");
    }

    @Test
    void theGroundReadsBackAtAShot() {
        assertThat(profile().elevationAt(Length.ofFeet(600))).isEqualTo(Length.ofFeet(6480));
        assertThat(profile().elevationAt(Length.ofFeet(0))).isEqualTo(Length.ofFeet(6400));
        assertThat(profile().elevationAt(Length.ofFeet(2000))).isEqualTo(Length.ofFeet(6900));
    }

    @Test
    void betweenTwoShotsTheGroundIsAStraightLine() {
        assertThat(profile().elevationAt(Length.ofFeet(450))).isEqualTo(Length.ofFeet(6460));
        assertThat(profile().elevationAt(Length.ofFeet(1400))).isEqualTo(Length.ofFeet(6760));
    }

    @Test
    void offTheSurveyIsRefusedRatherThanExtrapolated() {
        assertThatThrownBy(() -> profile().elevationAt(Length.ofFeet(2400)))
                .isInstanceOf(PlantError.class)
                .hasMessage("station 2400 is off the survey");
        assertThatThrownBy(() -> profile().elevationAt(Length.ofFeet(-10))).isInstanceOf(PlantError.class);
        assertThat(profile().covers(Length.ofFeet(2400))).isFalse();
    }

    @Test
    void theProfileKnowsItsEndsAndItsExtremes() {
        LineProfile ground = profile();
        assertThat(ground.firstStation()).isEqualTo(Length.ofFeet(0));
        assertThat(ground.lastStation()).isEqualTo(Length.ofFeet(2000));
        assertThat(ground.highest().elevation()).isEqualTo(Length.ofFeet(6900));
        assertThat(ground.lowest().elevation()).isEqualTo(Length.ofFeet(6400));
    }

    @Test
    void aClearanceIsTheGapBetweenRopeAndGround() {
        Clearance gap = new Clearance(Length.ofFeet(600), Length.ofFeet(6512), Length.ofFeet(6480));
        assertThat(gap.height()).isEqualTo(Length.ofFeet(32));
        assertThat(gap.isBelow(Length.ofFeet(40))).isTrue();
        assertThat(gap.isBelow(Length.ofFeet(20))).isFalse();
        assertThat(gap).hasToString("32.0 ft at station 600");
    }

    @Test
    void theSamplerWalksTheSpanAtTheStep() {
        List<Length> stations = profile().stations(span(0, 6440, 100, 6480), Length.ofFeet(25));
        assertThat(feet(stations))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(
                        new BigDecimal(0),
                        new BigDecimal(25),
                        new BigDecimal(50),
                        new BigDecimal(75),
                        new BigDecimal(100));
    }

    @Test
    void theSamplerPutsTheSurveyShotsInAsWell() {
        List<Length> stations = profile().stations(span(0, 6440, 700, 6580), Length.ofFeet(250));
        assertThat(stations).contains(Length.ofFeet(300), Length.ofFeet(600));
        assertThat(stations.get(0)).isEqualTo(Length.ofFeet(0));
        assertThat(stations.get(stations.size() - 1)).isEqualTo(Length.ofFeet(700));
    }

    @Test
    void theSamplerAlwaysIncludesBothEnds() {
        List<Length> stations = profile().stations(span(100, 6420, 350, 6450), Length.ofFeet(200));
        assertThat(stations.get(0)).isEqualTo(Length.ofFeet(100));
        assertThat(stations.get(stations.size() - 1)).isEqualTo(Length.ofFeet(350));
    }

    @Test
    void aStepOfNothingIsRefused() {
        assertThatThrownBy(() -> profile().stations(span(0, 6440, 600, 6520), Length.ofFeet(0)))
                .isInstanceOf(PlantError.class)
                .hasMessage("a sampling step has to be positive");
    }

    @Test
    void clearanceReadsTheRopeAndTheGroundAtOneStation() {
        Clearance gap = profile().clearanceAt(span(0, 6440, 600, 6540), Length.ofFeet(300));
        assertThat(gap.ropeElevation()).isEqualTo(Length.ofFeet(6490));
        assertThat(gap.groundElevation()).isEqualTo(Length.ofFeet(6440));
        assertThat(gap.height()).isEqualTo(Length.ofFeet(50));
    }

    /** The ground climbs faster than the chord in the middle of this span. */
    @Test
    void theTightestClearanceIsTheOneThatGetsActedOn() {
        LineProfile steep = LineProfile.fromSurvey(new long[][] {{0, 6400}, {300, 6520}, {600, 6540}});
        Clearance worst = steep.tightest(span(0, 6440, 600, 6580), Length.ofFeet(50));
        assertThat(worst.station()).isEqualTo(Length.ofFeet(300));
        assertThat(worst.height()).isEqualTo(Length.ofFeet(-10));
    }

    @Test
    void theTightestIsFoundBetweenShotsWhenTheKnollIsThere() {
        LineProfile knoll = LineProfile.fromSurvey(new long[][] {{0, 6400}, {250, 6560}, {500, 6400}});
        Clearance worst = knoll.tightest(span(0, 6440, 500, 6440), Length.ofFeet(25));
        assertThat(worst.station()).isEqualTo(Length.ofFeet(250));
    }

    @Test
    void everySampledClearanceComesBackInOrder() {
        List<Clearance> gaps = profile().clearances(span(0, 6440, 600, 6540), Length.ofFeet(150));
        assertThat(feet(gaps.stream().map(Clearance::station).toList()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(
                        new BigDecimal(0),
                        new BigDecimal(150),
                        new BigDecimal(300),
                        new BigDecimal(450),
                        new BigDecimal(600));
        assertThat(gaps).allMatch(gap -> gap.getClass() == Clearance.class);
    }

    @Test
    void aGroundPointCanBeBuiltStraightFromFeet() {
        GroundPoint point = GroundPoint.atFeet("600", "6480.5");
        assertThat(point.station()).isEqualTo(Length.ofFeet(600));
        assertThat(point.elevation()).isEqualTo(Length.ofFeet("6480.5"));
    }
}
