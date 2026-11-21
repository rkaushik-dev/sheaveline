package io.github.rkaushikdev.sheaveline.line;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.holdDownAssembly;
import static io.github.rkaushikdev.sheaveline.Hill.supportAssembly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
import io.github.rkaushikdev.sheaveline.plant.SheaveAssembly;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.Support;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import org.junit.jupiter.api.Test;

class DeviationTest {

    private static final SagModel SAG = new SagModel(LoadPerFoot.ofPoundsPerFoot("3.12"), Load.ofPounds(34500));

    private static Support support(String label, long station, long elevation) {
        return Support.terminal(label, Length.ofFeet(station), Length.ofFeet(elevation));
    }

    private static Span span(int index, Support lower, Support upper) {
        return new Span(index, lower, upper);
    }

    private static Tower tower(long station, long elevation, SheaveAssembly up, SheaveAssembly down) {
        return new Tower(
                JOHN_PAUL.tower(4), Length.ofFeet(station), Length.ofFeet(elevation - 30), Length.ofFeet(30), up, down);
    }

    private static Tower tower(long station, long elevation) {
        return tower(station, elevation, supportAssembly(4, "0.7"), supportAssembly(4, "0.7"));
    }

    private static TowerLoading loading(
            long lowerElev, long midElev, long upperElev, SheaveAssembly up, SheaveAssembly down) {
        Span below = span(1, support("a", 0, lowerElev), support("t", 1000, midElev));
        Span above = span(2, support("t", 1000, midElev), support("b", 2000, upperElev));
        return new TowerLoading(tower(1000, midElev, up, down), below, above, SAG);
    }

    private static TowerLoading loading(long lowerElev, long midElev, long upperElev) {
        return loading(lowerElev, midElev, upperElev, supportAssembly(4, "0.7"), supportAssembly(4, "0.7"));
    }

    private static TowerLoading loading(long upperElev) {
        return loading(6400, 6500, upperElev);
    }

    private static TowerLoading loading(SheaveAssembly up, SheaveAssembly down) {
        return loading(6400, 6500, 6560, up, down);
    }

    private static TowerLoading loading() {
        return loading(6560);
    }

    @Test
    void theSagTipsTheRopeAtBothEndsOfASpan() {
        Span level = span(1, support("a", 0, 6400), support("b", 1000, 6400));
        Angle tip = Deviation.sagSlope(SAG, level);
        assertThat(tip.millidegrees()).isPositive();
        assertThat(Deviation.arrivalSlope(SAG, level)).isEqualTo(tip);
        assertThat(Deviation.departureSlope(SAG, level)).isEqualTo(tip.negated());
    }

    @Test
    void theRopeArrivesSteeperThanTheChordAndLeavesFlatter() {
        Span climbing = span(1, support("a", 0, 6400), support("b", 1000, 6500));
        assertThat(Deviation.arrivalSlope(SAG, climbing)).isGreaterThan(climbing.slope());
        assertThat(Deviation.departureSlope(SAG, climbing)).isLessThan(climbing.slope());
    }

    @Test
    void aTowerWhereTheSlopeEasesPressesTheRopeDown() {
        TowerLoading held = loading(6400, 6500, 6560);
        assertThat(held.deviation().millidegrees()).isPositive();
        assertThat(held.pressesDown()).isTrue();
        assertThat(held.assemblyKindSeen()).isSameAs(AssemblyKind.SUPPORT);
    }

    @Test
    void aTowerWhereTheSlopeSteepensLiftsTheRopeOff() {
        TowerLoading held = loading(6400, 6440, 6600);
        assertThat(held.deviation().millidegrees()).isNegative();
        assertThat(held.pressesDown()).isFalse();
        assertThat(held.assemblyKindSeen()).isSameAs(AssemblyKind.HOLD_DOWN);
    }

    @Test
    void theForceIntoTheAssemblyIsTensionTimesTheDeviation() {
        TowerLoading held = loading();
        Load expected = SAG.tension().scaled(Double.toString(held.deviation().radians()));
        assertThat(held.radialForce()).isEqualTo(expected);
    }

    @Test
    void theForceGrowsWithTheDeviation() {
        long gentle = loading(6540).radialForce().pounds();
        long sharp = loading(6510).radialForce().pounds();
        assertThat(sharp).isGreaterThan(gentle);
    }

    @Test
    void theScheduleAndTheRopeCanDisagreeAboutWhatATowerIs() {
        TowerLoading held = loading(6400, 6440, 6600, supportAssembly(), supportAssembly());
        assertThat(held.assemblyKindSeen()).isSameAs(AssemblyKind.HOLD_DOWN);
        assertThat(held.agreesWithSchedule()).isFalse();
    }

    @Test
    void aTowerBuiltTheWayTheRopeBehavesAgrees() {
        TowerLoading held = loading(6400, 6440, 6600, holdDownAssembly(), holdDownAssembly());
        assertThat(held.agreesWithSchedule()).isTrue();
    }

    @Test
    void theDesignedDeviationIsReadOffBothAssemblies() {
        TowerLoading held = loading(supportAssembly(4, "0.7"), supportAssembly(4, "0.7"));
        assertThat(held.designedDeviation()).isEqualTo(Angle.ofDegrees("1.4"));
    }

    @Test
    void aTowerCloseToItsDesignFigureMatches() {
        TowerLoading held = loading(supportAssembly(4, "0.7"), supportAssembly(4, "0.7"));
        Angle error = held.deviationError();
        assertThat(held.matchesDesign(error.abs().plus(Angle.ofDegrees("0.01"))))
                .isTrue();
        assertThat(held.matchesDesign(Angle.ofDegrees(0))).isFalse();
    }

    @Test
    void aHoldDownWithAlmostNoForceOnItLiftsOff() {
        TowerLoading barely = loading(6400, 6402, 6406, holdDownAssembly(), holdDownAssembly());
        assertThat(barely.tower().isHoldDown()).isTrue();
        assertThat(barely.liftsOff()).isTrue();
    }

    @Test
    void aHoldDownWithRealForceOnItStaysSeated() {
        TowerLoading firm = loading(6400, 6440, 6700, holdDownAssembly(), holdDownAssembly());
        assertThat(firm.liftsOff()).isFalse();
    }

    @Test
    void aSupportTowerIsNeverAskedAboutLiftOff() {
        assertThat(loading().liftsOff()).isFalse();
    }

    @Test
    void theMinimumHoldDownForceIsAFractionOfTheTension() {
        assertThat(loading().minimumHoldDownForce()).isEqualTo(Load.ofPounds(690));
    }

    @Test
    void theLoadSplitsAcrossTheSheavesOnTheLoadedSide() {
        TowerLoading held = loading(supportAssembly(6, "0.7"), supportAssembly(4, "0.7"));
        long whole = Math.abs(held.radialForce().pounds());
        assertThat(held.perSheave().pounds())
                .isEqualTo(
                        Load.ofPounds(whole).scaled(Decimals.fixed(1.0 / 6, 10)).pounds());
    }

    @Test
    void aTowerHasToSitBetweenTheTwoSpansItIsGiven() {
        Span below = span(1, support("a", 0, 6400), support("t", 1000, 6500));
        Span above = span(2, support("t", 1000, 6500), support("b", 2000, 6560));
        assertThatThrownBy(() -> new TowerLoading(tower(1200, 6500), below, above, SAG))
                .isInstanceOf(LineError.class);
    }

    @Test
    void aLoadingPrintsTheTwoNumbersAForemanWants() {
        String printed = loading().toString();
        assertThat(printed).startsWith("JP-T4 at ");
        assertThat(printed).contains("lb into the assembly");
    }
}
