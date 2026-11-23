package io.github.rkaushikdev.sheaveline.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.Span;
import io.github.rkaushikdev.sheaveline.plant.Support;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import org.junit.jupiter.api.Test;

class CatenaryTest {

    private static final LoadPerFoot ROPE = LoadPerFoot.ofPoundsPerFoot("3.12");

    private static SagModel model(String poundsPerFoot, long tension) {
        return new SagModel(LoadPerFoot.ofPoundsPerFoot(poundsPerFoot), Load.ofPounds(tension));
    }

    private static SagModel model(String poundsPerFoot) {
        return model(poundsPerFoot, 34500);
    }

    private static SagModel model(long tension) {
        return model("3.12", tension);
    }

    private static SagModel model() {
        return model("3.12", 34500);
    }

    private static Span span(long lowerStation, long lowerElev, long upperStation, long upperElev) {
        return new Span(
                1,
                Support.terminal("a", Length.ofFeet(lowerStation), Length.ofFeet(lowerElev)),
                Support.terminal("b", Length.ofFeet(upperStation), Length.ofFeet(upperElev)));
    }

    private static Span span(long upperElev) {
        return span(0, 6400, 1000, upperElev);
    }

    private static Span span() {
        return span(6400);
    }

    @Test
    void aModelNeedsWeightAndTension() {
        assertThatThrownBy(() -> new SagModel(LoadPerFoot.ofPoundsPerFoot(0), Load.ofPounds(34500)))
                .isInstanceOf(LineError.class);
        assertThatThrownBy(() -> new SagModel(ROPE, Load.ofPounds(0))).isInstanceOf(LineError.class);
    }

    @Test
    void theMidspanSagIsTheStandardParabola() {
        // 3.12 lb/ft over 1000 ft at 34,500 lb is 3.12 * 1e6 / 276000.
        assertThat(Decimals.quantize(model().sagOver(Length.ofFeet(1000)).feet(), 2))
                .isEqualByComparingTo("11.30");
    }

    @Test
    void sagGrowsWithTheSquareOfTheSpan() {
        Length shorter = model().sagOver(Length.ofFeet(500));
        Length longer = model().sagOver(Length.ofFeet(1000));
        assertThat(Decimals.quantize(longer.ratio(shorter), 2)).isEqualByComparingTo("4");
    }

    @Test
    void moreTensionPullsTheRopeUp() {
        Length slack = model(20000).sagOver(Length.ofFeet(1000));
        Length tight = model(40000).sagOver(Length.ofFeet(1000));
        assertThat(tight).isLessThan(slack);
    }

    @Test
    void moreWeightPullsTheRopeDown() {
        Length light = model("3.12").sagOver(Length.ofFeet(1000));
        Length heavy = model("6.24").sagOver(Length.ofFeet(1000));
        assertThat(heavy.mils()).isEqualTo(light.mils() * 2);
    }

    @Test
    void thereIsNoSagAtTheSupportsAndMostAtTheMiddle() {
        Length horizontal = Length.ofFeet(1000);
        assertThat(model().sagAt(horizontal, Length.ofFeet(0)).isZero()).isTrue();
        assertThat(model().sagAt(horizontal, Length.ofFeet(1000)).isZero()).isTrue();
        assertThat(model().sagAt(horizontal, Length.ofFeet(500))).isEqualTo(model().sagOver(horizontal));
    }

    @Test
    void theSagShapeIsSymmetricAboutTheMiddle() {
        Length horizontal = Length.ofFeet(1000);
        Length left = model().sagAt(horizontal, Length.ofFeet(250));
        Length right = model().sagAt(horizontal, Length.ofFeet(750));
        assertThat(left).isEqualTo(right);
    }

    @Test
    void aPointOutsideTheSpanHasNoSagToReport() {
        assertThatThrownBy(() -> model().sagAt(Length.ofFeet(1000), Length.ofFeet(1200)))
                .isInstanceOf(LineError.class);
    }

    @Test
    void theRopeSitsBelowTheChordByTheSag() {
        Span level = span();
        Length atMid = model().ropeElevation(level, Length.ofFeet(500));
        assertThat(atMid).isLessThan(Length.ofFeet(6400));
        assertThat(Length.ofFeet(6400).minus(atMid)).isEqualTo(model().sagOver(Length.ofFeet(1000)));
    }

    @Test
    void theRopeTouchesTheChordAtBothSupports() {
        Span level = span();
        assertThat(model().ropeElevation(level, Length.ofFeet(0))).isEqualTo(Length.ofFeet(6400));
        assertThat(model().ropeElevation(level, Length.ofFeet(1000))).isEqualTo(Length.ofFeet(6400));
    }

    @Test
    void onALevelSpanTheLowPointIsTheMiddle() {
        assertThat(model().lowPoint(span())).isEqualTo(Length.ofFeet(500));
    }

    @Test
    void onAClimbingSpanTheLowPointMovesDownhill() {
        Span climbing = span(6440);
        assertThat(model().lowPoint(climbing)).isLessThan(Length.ofFeet(500));
    }

    @Test
    void onADroppingSpanTheLowPointMovesUphill() {
        Span dropping = span(6360);
        assertThat(model().lowPoint(dropping)).isGreaterThan(Length.ofFeet(500));
    }

    @Test
    void onASpanSteeperThanTheSagTheLowPointIsTheBottomSupport() {
        Span steep = span(6800);
        assertThat(model().lowPoint(steep)).isEqualTo(Length.ofFeet(0));
    }

    @Test
    void onASpanDroppingHarderThanTheSagTheLowPointIsTheTop() {
        Span steep = span(6000);
        assertThat(model().lowPoint(steep)).isEqualTo(Length.ofFeet(1000));
    }

    @Test
    void theTensionThatWouldHoldAWantedSag() {
        Length wanted = Length.ofFeet(10);
        Load needed = model().tensionForSag(Length.ofFeet(1000), wanted);
        assertThat(needed).isEqualTo(Load.ofPounds(39000));
        SagModel lifted = model().withTension(needed);
        assertThat(lifted.sagOver(Length.ofFeet(1000))).isEqualTo(wanted);
    }

    @Test
    void aWantedSagOfNothingHasNoAnswer() {
        assertThatThrownBy(() -> model().tensionForSag(Length.ofFeet(1000), Length.ofFeet(0)))
                .isInstanceOf(LineError.class);
    }

    @Test
    void theSagRatioIsHowADrawingQuotesIt() {
        assertThat(Decimals.quantize(model().sagRatio(Length.ofFeet(1000)), 4)).isEqualByComparingTo("0.0113");
        assertThatThrownBy(() -> model().sagRatio(Length.ofFeet(0))).isInstanceOf(LineError.class);
    }

    @Test
    void swappingTheWeightLeavesTheTensionAlone() {
        SagModel heavier = model().withWeight(LoadPerFoot.ofPoundsPerFoot("4.00"));
        assertThat(heavier.tension()).isEqualTo(model().tension());
        assertThat(heavier.weight()).isNotEqualTo(model().weight());
    }

    @Test
    void theCarriersAddTheirShareToTheWeightOnTheLine() {
        LoadPerFoot loaded = Catenary.lineWeight(ROPE, Load.ofPounds(1090), Length.ofFeet(109));
        assertThat(loaded.poundsPerFoot()).isEqualByComparingTo("13.12");
        assertThat(loaded).isGreaterThan(ROPE);
    }

    @Test
    void carriersWithNoSpacingHaveNoShare() {
        assertThatThrownBy(() -> Catenary.lineWeight(ROPE, Load.ofPounds(1090), Length.ofFeet(0)))
                .isInstanceOf(LineError.class);
    }

    @Test
    void aSleeveOfIceWeighsWhatItsVolumeOfGlazeWeighs() {
        Length rope = Length.ofInches("1.378");
        assertThat(Catenary.iceWeight(rope, Length.ofInches("0.5")).poundsPerFoot())
                .isEqualByComparingTo("1.168");
        assertThat(Catenary.iceWeight(rope, Length.ofInches("1")).poundsPerFoot())
                .isEqualByComparingTo("2.957");
        assertThat(Catenary.iceWeight(rope, Length.ZERO).millipoundsPerFoot()).isZero();
        assertThatThrownBy(() -> Catenary.iceWeight(rope, Length.ofInches("-0.1")))
                .isInstanceOf(LineError.class)
                .hasMessage("ice on a rope is not thinner than nothing");
    }

    @Test
    void aMachinesLoadedLineIsItsRopeItsCarriersAndItsIce() {
        Lift lift = Hill.johnPaul();
        Length spacing = Length.ofFeet(109);
        LoadPerFoot bare = Catenary.loadedLine(lift, spacing, Length.ZERO);
        assertThat(bare)
                .isEqualTo(Catenary.lineWeight(
                        lift.rope().spec().weight(),
                        lift.carriers().onLine().getFirst().gross(),
                        spacing));
        LoadPerFoot iced = Catenary.loadedLine(lift, spacing, Length.ofInches("0.5"));
        assertThat(iced.millipoundsPerFoot() - bare.millipoundsPerFoot()).isEqualTo(1168);
        assertThat(new SagModel(iced, Load.ofPounds(34500)).sagOver(Length.ofFeet(600)))
                .isGreaterThan(new SagModel(bare, Load.ofPounds(34500)).sagOver(Length.ofFeet(600)));
    }

    @Test
    void aModelPrintsItsTwoNumbers() {
        assertThat(model()).hasToString("3.12 lb/ft at 34,500 lb");
    }
}
